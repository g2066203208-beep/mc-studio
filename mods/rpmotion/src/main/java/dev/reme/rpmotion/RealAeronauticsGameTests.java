package dev.reme.rpmotion;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real runtime integration: actual installed Aeronautics + Sable, not a fake physics simulator. */
@GameTestHolder(RPMotionMod.ID)
@PrefixGameTestTemplate(false)
public final class RealAeronauticsGameTests {
    private RealAeronauticsGameTests(){}
    private static net.minecraft.world.level.block.Block realBlock(String name){
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("simulated",name));
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void realPublishedAeronauticsComponentsExist(GameTestHelper helper) {
        for(String name:new String[]{"swivel_bearing","physics_assembler","analog_transmission",
            "gimbal_sensor","velocity_sensor","optical_sensor","torsion_spring"}) {
            helper.assertTrue(!realBlock(name).equals(Blocks.AIR),
                "Missing original Aeronautics block: simulated:"+name);
        }
        var pos=helper.absolutePos(new BlockPos(2,1,2));
        helper.getLevel().setBlock(pos,realBlock("swivel_bearing").defaultBlockState(),3);
        helper.assertTrue(NativeBearingProbe.read(helper.getLevel(),pos).installed(),
            "Must be the ACTUAL Simulated SwivelBearingBlockEntity from official JAR");
        helper.succeed();
    }
    @GameTest(template="empty",timeoutTicks=160)
    public static void realNativeBearingAssemblesPhysicalSableSublevel(GameTestHelper helper) {
        var level=helper.getLevel();
        var pos=helper.absolutePos(new BlockPos(2,1,2));
        var originalBearing=realBlock("swivel_bearing");
        level.setBlock(pos,originalBearing.defaultBlockState()
            .setValue(BlockStateProperties.FACING,Direction.UP),3);
        level.setBlock(pos.above(),Blocks.IRON_BLOCK.defaultBlockState(),3);
        helper.runAtTickTime(8,()->NativeBearingProbe.assembleNative(level,pos));
        helper.runAtTickTime(65,()->{
            var state=NativeBearingProbe.read(level,pos);
            helper.assertTrue(state.installed() && state.assembled(),
                "Original built-in Aeronautics bearing must assemble with actual Sable runtime");
            helper.assertTrue(state.subLevelId()!=null,
                "Original bearing must allocate actual physical Sable UUID");
            helper.assertTrue(state.platePos()!=null,
                "Original bearing must create its plate in the physical plot");
            helper.assertTrue(level.getBlockState(pos).is(originalBearing),
                "No replacement/fake block must be used");
            helper.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=40)
    public static void rpmotionOnlyContainsSystemCode(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.BLOCK.keySet().stream()
            .noneMatch(id->id.getNamespace().equals(RPMotionMod.ID)),
            "RPMotion must register ZERO blocks; mechanic devices all belong to Aeronautics");
        helper.succeed();
    }
}
