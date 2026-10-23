package dev.reme.mcstudio.npc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/**
 * Operator-only real Minecraft animation gallery for actually rendered
 * player-skinned NPCs. The characters and skeleton animations exist in the
 * world; there is no screen-space sprite or fabricated simulation video.
 *
 * This is an ANIMATION SHOWCASE, not evidence of autonomous eating, two
 * persons holding hands, rendered restraint, or a safe piggyback interaction.
 * Live-world crime/escort has separate functional GameTests.
 *
 * Does not place / remove ANY terrain blocks, force-load chunks, change
 * actual residents, access player saves, or spawn entities on bad ground.
 */
public final class NpcAnimationShowcase {
    private NpcAnimationShowcase() {}
    public static final String TAG = "reme_npc_animation_stage";
    public static final int COLS = 6;
    public static final int SPACING = 4;
    public static final long STAGE_TICKS = 72000L;
    public record StageResult(boolean success, String reason, int actors) {}

    private static BlockPos slot(BlockPos center,int index) {
        return center.offset((index%COLS)*SPACING-10,0,(index/COLS)*SPACING-8);
    }
    private static String stageTag(int slot) {
        return "reme_anim_" + String.format(Locale.ROOT,"%02d",slot);
    }

    public static StageResult create(ServerLevel level,BlockPos center,
                                     @Nullable ServerPlayer owner) {
        NpcPose[] actions=NpcPose.values();
        int total=actions.length;
        if(total != 30)
            return new StageResult(false,"动画资源总数与展台布局不符",0);
        if(!level.getEntitiesOfClass(PlayerNpcEntity.class,
                new AABB(center).inflate(18,4,16),npc -> npc.getTags().contains(TAG)).isEmpty())
            return new StageResult(false,"此地已存在动画展台，拒绝重复创建实体",0);
        // ALL-or-NOTHING survey. A failure may not damage user construction.
        for(int i=0;i<total;i++){
            BlockPos pos=slot(center,i);
            if(!level.isInWorldBounds(pos)
                ||!level.getWorldBorder().isWithinBounds(pos)
                ||!level.hasChunkAt(pos)||!level.hasChunkAt(pos.below())
                ||!level.getBlockState(pos).isAir()
                ||!level.getBlockState(pos.above()).isAir()
                ||!level.getBlockState(pos.below()).isSolid()
                ||!level.getEntitiesOfClass(PlayerNpcEntity.class,
                    new AABB(pos).inflate(0.7,1,0.7)).isEmpty())
                return new StageResult(false,"第"+i+"个动作位置实体或地形被占用："+pos,0);
            if(!SharedHumanoidMotion.authoredClip(actions[i]))
                return new StageResult(false,"无真实动画资源："+actions[i],0);
        }
        List<PlayerNpcEntity> created=new ArrayList<>(total);
        for(int i=0;i<total;i++){
            PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
            if(npc==null){
                rollback(created);
                return new StageResult(false,"真实NPC实体创建失败，已回滚展台",0);
            }
            BlockPos pos=slot(center,i);
            npc.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            npc.setMode(PlayerNpcEntity.Mode.STAY);
            npc.setSkinId(i%6);
            npc.addTag(TAG);
            npc.addTag(stageTag(i));
            if(owner!=null) npc.setOwner(owner);
            npc.setCustomName(Component.literal(
                String.format(Locale.ROOT,"%02d",i)+" | "+actions[i].name()));
            npc.setCustomNameVisible(true);
            npc.showAnimationFor(actions[i],STAGE_TICKS);
            if(!level.addFreshEntity(npc)){
                npc.discard();
                rollback(created);
                return new StageResult(false,"真实NPC登记失败，已清理本次展台",0);
            }
            created.add(npc);
            System.out.println("REME_NPC_REAL_ANIMATION_ACTOR index="+i
                +" uuid="+npc.getUUID()+" pose="+actions[i]
                +" xyz="+npc.blockPosition().toShortString()
                +" syncPose="+npc.visualPose()+" actualEntity=true");
        }
        System.out.println("REME_NPC_ANIMATION_STAGE_REAL clips="+total
            +" actualEntities="+created.size()+" syntheticSprite=false"
            +" saveSchemaChanged=false playerAnimatorResourceDirectory=player_animations");
        return new StageResult(true,
            "真实NPC动作展台完成：30个独立玩家骨架实体、30种动作循环；"
            +"这只证明视觉姿态展示，不证明双人牵手/背负的真实物理交互",created.size());
    }

    private static void rollback(List<PlayerNpcEntity> actors) {
        for(PlayerNpcEntity npc:actors) npc.discard();
    }

    public static int spawnCommand(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer operator=source.getPlayerOrException();
        if(!operator.isCreative()){
            source.sendFailure(Component.literal("仅创造模式管理员可放置真实NPC动作展台"));
            return 0;
        }
        StageResult result=create(operator.serverLevel(),operator.blockPosition(),operator);
        if(!result.success()){
            source.sendFailure(Component.literal(result.reason()));
            return 0;
        }
        source.sendSuccess(()->Component.literal(result.reason()),true);
        return result.actors();
    }

    public static int playCommand(CommandSourceStack source,String action)
        throws CommandSyntaxException {
        ServerPlayer operator=source.getPlayerOrException();
        NpcPose pose;
        try { pose=NpcPose.valueOf(action.toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException invalid) {
            source.sendFailure(Component.literal("未知动作："+action));
            return 0;
        }
        if(!SharedHumanoidMotion.authoredClip(pose)){
            source.sendFailure(Component.literal("未制作真实关键帧："+pose));
            return 0;
        }
        var actors=operator.serverLevel().getEntitiesOfClass(PlayerNpcEntity.class,
            operator.getBoundingBox().inflate(64),
            npc -> npc.getTags().contains(TAG) && npc.mode()==PlayerNpcEntity.Mode.STAY);
        if(actors.isEmpty()){
            source.sendFailure(Component.literal("附近没有已生成的真实NPC动作展台"));
            return 0;
        }
        for(PlayerNpcEntity npc:actors) npc.showAnimationFor(pose,STAGE_TICKS);
        int count=actors.size();
        source.sendSuccess(()->Component.literal("真实动画预览 "+pose
            +"：已让 "+count+" 个可见NPC实体使用同一个玩家动作资源"),false);
        return count;
    }

    public static int clearCommand(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer operator=source.getPlayerOrException();
        var actors=operator.serverLevel().getEntitiesOfClass(PlayerNpcEntity.class,
            operator.getBoundingBox().inflate(64),
            npc -> npc.getTags().contains(TAG));
        for(PlayerNpcEntity npc:actors) npc.discard();
        int count=actors.size();
        source.sendSuccess(()->Component.literal(
            "仅清理专用动作展台实体："+count+"；不删除其他NPC或世界方块"),true);
        return count;
    }
}
