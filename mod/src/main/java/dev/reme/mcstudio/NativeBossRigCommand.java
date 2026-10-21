package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.joml.Vector3d;

/**
 * /rpms bossrig: inspect a REAL physical foundation model in-game.
 *
 * In a flat open area, spawn a four-footed sublevel in front of an operator.
 * The player can knock it or remove feet; gravity and world collisions
 * are calculated by the installed Sable/Rapier engine.
 */
public final class NativeBossRigCommand {
    private NativeBossRigCommand() {}
    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(
                Commands.literal("rpms").requires(source->source.hasPermission(2))
                        .then(Commands.literal("bossrig")
                                .executes(ctx->spawn(ctx.getSource()))));
    }
    private static int spawn(CommandSourceStack source) {
        ServerLevel level=source.getLevel();
        Direction f=Direction.fromYRot(source.getRotation().y);
        BlockPos eye=BlockPos.containing(source.getPosition());
        BlockPos near=eye.relative(f,5);
        if(!level.hasChunkAt(near)){
            source.sendFailure(Component.literal("Boss P0: 请先加载前方区块"));
            return 0;
        }
        int groundY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,near.getX(),near.getZ());
        // Rig local center is +1,+1, so place footprint in front of the player.
        Vector3d origin=new Vector3d(near.getX()-1,groundY+3,near.getZ()-1);
        try{
            var rig=NativeSableStandingRig.spawn(level,origin);
            source.sendSuccess(()->Component.literal(
                    "Boss P0 真实物理四足机已在前方生成！"+
                    " 机身 "+rig.bodyCount()+" 块，四条承重腿 "+rig.footCount()+" 块。"+
                    "此模型没有锁定坐标、悬浮力或隐藏轮子，依靠 Sable 地面碰撞支撑。"+
                    "初版为一体式刚性腿，不会自己走路。"),true);
            return 1;
        }catch(RuntimeException ex){
            LogUtils.getLogger().error("RPMS_BOSS_P0 spawn failed",ex);
            source.sendFailure(Component.literal("Boss 物理生成失败: "+ex.getMessage()));
            return 0;
        }
    }
}
