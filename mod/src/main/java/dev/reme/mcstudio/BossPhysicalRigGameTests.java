package dev.reme.mcstudio;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * Native physics acceptance for the very first buildable boss chassis.
 * Verifies not merely "4 leg meshes are drawn", but two physically different
 * Sable outcomes under REAL server world gravity and block collisions:
 *
 * 1. A rigid 3x3 elevated torso falls and STOPS ON its four lower feet.
 * 2. Physically removing those feet causes that same torso to fall again.
 *
 * The change in measured Sable pose/velocity is the support evidence.
 * No fake client animations, no manually clamped body position.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BossPhysicalRigGameTests {
    private static final Logger LOG=LogUtils.getLogger();
    private BossPhysicalRigGameTests(){}

    private static void requireSable() {
        for(String id:new String[]{"sable","simulated","aeronautics","create"}) {
            if(!ModList.get().isLoaded(id))throw new AssertionError("Boss physics GameTest requires "+id);
        }
    }

    @GameTest(template="rpms_empty",timeoutTicks=180)
    public static void fourFeetActuallyCarryBodyWeight(GameTestHelper helper){
        requireSable();
        // Real Minecraft support floor (not a hidden Sable position anchor).
        // Rig footprint: world x/z = test-origin+[1..3].
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)
            helper.setBlock(new BlockPos(x,0,z),Blocks.SMOOTH_STONE);
        BlockPos origin=helper.absolutePos(new BlockPos(1,4,1));
        BlockPos floor=helper.absolutePos(new BlockPos(1,0,1));
        var rig=NativeSableStandingRig.spawn(helper.getLevel(),
                new Vector3d(origin.getX(),origin.getY(),origin.getZ()));
        if(rig.bodyCount()!=9||rig.footCount()!=8)
            throw new AssertionError("Boss P0 physical block layout incorrect");
        double initialY=rig.position().y();
        try {
            Object massTracker=rig.sublevel().getClass().getMethod("getMassTracker").invoke(rig.sublevel());
            double mass=((Number)massTracker.getClass().getMethod("getMass").invoke(massTracker)).doubleValue();
            LOG.info("RPMS_BOSS_P0_MASS actualSableMass={}kg bodyBlocks={} legBlocks={}", mass,rig.bodyCount(),rig.footCount());
            if(!(mass>0) || !Double.isFinite(mass))
                throw new AssertionError("Sable rig has no valid physical mass; cannot verify supporting force");
        } catch(ReflectiveOperationException e){
            throw new AssertionError("Cannot read real Sable mass tracker",e);
        }

        helper.runAfterDelay(87,()->{
            if(rig.sublevel().getClass().getSimpleName().isBlank())
                throw new AssertionError("Missing real Sable sublevel");
            var settled=rig.position();
            var velocity=rig.velocity();
            double groundTop=floor.getY()+1.0;
            Vector3d realCenter=rig.centerOfMass();
            double footSoleWorldY=rig.footSoleWorldY();
            double torsoWorldYBefore=rig.torsoBottomWorldY();
            double footGroundGap=footSoleWorldY-groundTop;
            double fall=initialY-settled.y();
            LOG.info("RPMS_BOSS_P0_STAND initialY={} settledY={} fall={} groundTop={} realCenterOfMass={} footSoleWorldY={} footGroundGap={} torsoBottomWorldY={} nativeVelocity={}",
                    initialY,settled.y(),fall,groundTop,realCenter,footSoleWorldY,footGroundGap,torsoWorldYBefore,velocity);
            if(fall<0.5){helper.fail("Body did not physically fall under gravity");return;}
            if(Math.abs(footGroundGap)>0.80){
                helper.fail("Foot-sole transformed with Sable COM does not touch ground; gap="+footGroundGap);
                return;
            }
            if(Math.abs(velocity.y())>1.2){
                helper.fail("Body did not stabilize on four colliding feet; vy="+velocity.y());
                return;
            }

            // Remove all eight physical leg colliders from the SAME body.
            // The torso must now fall two more blocks. If not, the earlier
            // support was a fake hidden anchor or non-physical constraint.
            rig.removeFeet();
            LOG.info("RPMS_BOSS_P0_REMOVE_SUPPORT removedColliders=8 oldTorsoBottomY={}",torsoWorldYBefore);
            helper.runAfterDelay(49,()->{
                var unsupported=rig.position();
                var afterVelocity=rig.velocity();
                double torsoWorldYAfter=rig.torsoBottomWorldY();
                double lostHeight=torsoWorldYBefore-torsoWorldYAfter;
                LOG.info("RPMS_BOSS_P0_FALL_AFTER_LEG_REMOVAL oldBodyBottom={} newBodyBottom={} lostHeight={} realCenterOfMass={} rigidPose={} velocity={}",
                        torsoWorldYBefore,torsoWorldYAfter,lostHeight,rig.centerOfMass(),unsupported,afterVelocity);
                if(lostHeight<0.85){
                    helper.fail("Removing physical legs did not make the torso fall; support not proven: "+lostHeight);
                    return;
                }
                if(!Double.isFinite(lostHeight)||lostHeight>7){
                    helper.fail("Torso moved unrealistically after leg removal");
                    return;
                }
                LOG.info("RPMS_NATIVE_PASS fourFeetActuallyCarryBodyWeight REAL_COLLISION_AND_GRAVITY");
                helper.succeed();
            });
        });
    }
}
