package dev.reme.mcstudio;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * Game-authoritative, READ-ONLY capture of real placed Mod blocks, native
 * BlockStates and observed Kinetic BlockEntity RPM. No physics are faked.
 *
 * Operator-only, local server file output (no HTTP service or asset upload).
 * This JSON can be imported in the RPMS web editor after importing the user's
 * exact Mod JAR models. The editor remains a preview and cannot reproduce
 * server logic from snapshots.
 */
public final class NativeSnapshotCommand {
    private static final Logger LOG = LogUtils.getLogger();
    private NativeSnapshotCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("rpms")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("snapshot")
                    .executes(ctx -> export(ctx.getSource(), 6))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 8))
                        .executes(ctx -> export(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
        );
    }

    private static int export(CommandSourceStack source, int radius) {
        ServerLevel level=source.getLevel();
        BlockPos center=BlockPos.containing(source.getPosition());
        JsonObject data=snapshot(level,center,radius);
        int count=data.getAsJsonArray("blocks").size();
        if(count==0) {
            source.sendFailure(Component.literal("RPMS: 范围内没有 Create/航空学机械方块。"));
            return 0;
        }
        try {
            // World-local writable directory; do not reveal arbitrary absolute
            // filesystem paths in chat or allow users to supply filenames.
            Path out=source.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("rpms-snapshots").normalize();
            Files.createDirectories(out);
            Path file=out.resolve("rpms-"+level.getGameTime()+".json");
            Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(data),StandardCharsets.UTF_8);
            LOG.info("RPMS_NATIVE_SNAPSHOT blocks={} radius={} world={} file={}",
                count,radius,level.dimension().location(),file.getFileName());
            source.sendSuccess(()->Component.literal(
                "RPMS: 已从真实 MC 服务器导出 "+count+" 个方块到世界存档/rpms-snapshots/"+file.getFileName()
                +"。请在网页导入同版本 Mod JAR 后读取此 JSON。"),false);
            return count;
        } catch(IOException ex) {
            LOG.error("RPMS native snapshot failed",ex);
            source.sendFailure(Component.literal("RPMS 导出失败："+ex.getClass().getSimpleName()));
            return 0;
        }
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    public static JsonObject snapshot(ServerLevel level,BlockPos center,int radius) {
        if(radius<1||radius>8)throw new IllegalArgumentException("radius 1..8");
        JsonObject out=new JsonObject();
        out.addProperty("format","rpms-verified-layout");
        out.addProperty("version",1);
        out.addProperty("notMinecraftSchematic",true);
        out.addProperty("nativeSnapshot",true);
        out.addProperty("source","Real NeoForge server / read-only blocks and kinetic speed");
        out.addProperty("dimension",level.dimension().location().toString());
        out.addProperty("gameTick",level.getGameTime());
        JsonArray origin=new JsonArray();
        origin.add(center.getX());origin.add(center.getY());origin.add(center.getZ());
        out.add("originWorld",origin);
        JsonArray blocks=new JsonArray();
        for(int dy=-4;dy<=4;dy++){
            for(int dx=-radius;dx<=radius;dx++){
                for(int dz=-radius;dz<=radius;dz++){
                    BlockPos position=center.offset(dx,dy,dz);
                    if(!level.hasChunkAt(position))continue; // no chunk forced by audit
                    BlockState state=level.getBlockState(position);
                    if(state.isAir())continue;
                    ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    if(!isMechanicalNamespace(id.getNamespace()))continue;
                    JsonObject block=new JsonObject();
                    block.addProperty("id",id.toString());
                    JsonArray rel=new JsonArray();
                    rel.add(dx);rel.add(dy+5);rel.add(dz);block.add("pos",rel);
                    JsonObject props=new JsonObject();
                    for(Map.Entry<Property<?>,Comparable<?>> entry:state.getValues().entrySet()){
                        Property p=entry.getKey();
                        String value=p.getName((Comparable)entry.getValue());
                        props.addProperty(p.getName(),value);
                        if(p.getName().equals("axis")||p.getName().equals("facing"))
                            block.addProperty(p.getName(),value);
                    }
                    block.add("state",props);
                    BlockEntity be=level.getBlockEntity(position);
                    if(be!=null){
                        JsonObject nativeState=new JsonObject();
                        ResourceLocation beId=BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                        nativeState.addProperty("blockEntity",beId.toString());
                        nativeState.addProperty("implementation",be.getClass().getName());
                        for(String method:new String[]{"getSpeed","getTheoreticalSpeed","getGeneratedSpeed",
                                "getReceivedSignal","getStress","getStressCapacity"}){
                            Number value=readOptionalNumber(be,method);
                            if(value!=null)nativeState.addProperty(method,value);
                        }
                        block.add("nativeRuntime",nativeState);
                        // Existing RPMS layout tools understand 'rpm' for the
                        // creative motor. Populate it only from actual live
                        // Create data, never a browser-side guess.
                        if(id.toString().equals("create:creative_motor")){
                            Number observed=readOptionalNumber(be,"getSpeed");
                            if(observed!=null)block.addProperty("rpm",observed);
                        }
                    }
                    blocks.add(block);
                }
            }
        }
        out.add("blocks",blocks);
        out.addProperty("runtimeSemantics","observedBlockStateAndSpeedOnly; does NOT certify stress physics, wireless or flight");
        return out;
    }

    private static boolean isMechanicalNamespace(String ns) {
        return ns.equals("create")||ns.equals("simulated")||ns.equals("aeronautics")||ns.equals("offroad");
    }
    private static Number readOptionalNumber(BlockEntity be,String name){
        try {
            Method getter=be.getClass().getMethod(name);
            Object value=getter.invoke(be);
            return value instanceof Number number?number:null;
        }catch(ReflectiveOperationException|SecurityException ignored){
            return null;
        }
    }
}
