package dev.reme.worldseam;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft-only serialization and live-server validation, deliberately
 * separate from portable SeamStack topology. Plain JUnit can test topology
 * without Minecraft's NeoForge/ModLauncher classloader; GameTest exercises
 * this actual Minecraft persistence implementation.
 */
public final class SeamStackStorage {
    private SeamStackStorage() {}

    public static void validate(SeamStack stack,MinecraftServer server) {
        for(SeamStack.Entry e:stack.entries()) {
            ServerLevel level=server.getLevel(
                net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(e.id())));
            if(level==null)throw new IllegalArgumentException(
                "Missing dimension in this save (check mod/datapack): "+e.id());
            int min=level.getMinBuildHeight(), max=level.getMaxBuildHeight();
            int low=e.bottomY()!=null?e.bottomY():min;
            int top=e.topY()!=null?e.topY():max;
            if(low<min||top>max||low>=top)
                throw new IllegalArgumentException(e.id()+" seam heights "+
                    low+".."+top+" outside build range ["+min+","+max+")");
        }
    }

    public static CompoundTag save(SeamStack stack) {
        CompoundTag tag=new CompoundTag();
        tag.putInt("schema",1);
        tag.putBoolean("loop",stack.loop());
        ListTag list=new ListTag();
        for(SeamStack.Entry entry:stack.entries()){
            CompoundTag e=new CompoundTag();
            e.putString("id",entry.id());
            e.putBoolean("up",entry.connectsUp());
            e.putBoolean("down",entry.connectsDown());
            if(entry.bottomY()!=null)e.putInt("bottomY",entry.bottomY());
            if(entry.topY()!=null)e.putInt("topY",entry.topY());
            list.add(e);
        }
        tag.put("entries",list);
        return tag;
    }

    public static SeamStack read(CompoundTag tag) {
        List<SeamStack.Entry> entries=new ArrayList<>();
        ListTag list=tag.getList("entries",Tag.TAG_COMPOUND);
        if(list.isEmpty())throw new IllegalArgumentException("Empty saved dimension stack");
        for(int i=0;i<list.size();i++){
            CompoundTag e=list.getCompound(i);
            entries.add(new SeamStack.Entry(e.getString("id"),
                e.contains("bottomY",Tag.TAG_INT)?e.getInt("bottomY"):null,
                e.contains("topY",Tag.TAG_INT)?e.getInt("topY"):null,
                e.getBoolean("up"),e.getBoolean("down")));
        }
        return new SeamStack(entries,tag.getBoolean("loop"));
    }
}
