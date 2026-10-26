package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityJailGate;
import dev.reme.mcstudio.npc.life.PublicSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression for the REAL GUI stall at the rear cell gate: the detainee
 * occupied the iron-door tile and the officer remained diagonally outside
 * the three-block walking leash for hundreds of ticks.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityEscortIntakeGameTests {
    private CityEscortIntakeGameTests() {}

    private static PlayerNpcEntity spawn(ServerLevel level, BlockPos feet,
                                          PlayerNpcEntity.Profession job,
                                          BlockPos jail) {
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC entity type missing");
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(job);
        npc.setHomePos(jail);
        npc.setRoamingRadius(64);
        npc.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 0);
        if (!level.addFreshEntity(npc))
            throw new AssertionError("Could not spawn real custody actor");
        return npc;
    }

    @GameTest(template = "empty", timeoutTicks = 460)
    public static void officer_walks_to_rear_door_when_prisoner_blocks_iron_gate(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos jail = helper.absolutePos(new BlockPos(11, 2, 11));
        // Real collision: stone rear wall, one open oak intake, an open
        // vanilla iron door, and iron bars on the other three cell sides.
        for (int x = -5; x <= 5; x++)
            for (int z = -4; z <= 8; z++) {
                BlockPos tile = jail.offset(x, 0, z);
                level.setBlock(tile.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y <= 2; y++)
                    level.setBlock(tile.above(y), Blocks.AIR.defaultBlockState(), 2);
            }
        for (int z = -1; z <= 2; z++)
            for (int x : new int[] {-1, 1})
                for (int y = 0; y <= 2; y++)
                    level.setBlock(jail.offset(x, y, z),
                        Blocks.IRON_BARS.defaultBlockState(), 2);
        for (int x = -1; x <= 1; x++)
            for (int y = 0; y <= 2; y++)
                level.setBlock(jail.offset(x, y, -1),
                    Blocks.IRON_BARS.defaultBlockState(), 2);
        for (int x = -4; x <= 4; x++)
            if (x != 0)
                for (int y = 0; y <= 2; y++)
                    level.setBlock(jail.offset(x, y, 3),
                        Blocks.STONE_BRICKS.defaultBlockState(), 2);
        BlockPos gate = jail.south(2);
        level.setBlock(gate, Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(gate.above(), Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        BlockPos rear = jail.south(3);
        level.setBlock(rear, Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.OPEN, true)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(rear.above(), Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.OPEN, true)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        if (!PublicSafety.holdingSpot(level, jail)
            || !CityJailGate.setOpen(level, jail, true))
            throw new AssertionError("Real open iron gate fixture invalid");

        PlayerNpcEntity prisoner = spawn(level, gate,
            PlayerNpcEntity.Profession.RESIDENT, jail);
        PlayerNpcEntity officer = spawn(level, jail.offset(3, 0, 4),
            PlayerNpcEntity.Profession.GUARD, jail);
        long now = level.getGameTime();
        prisoner.detainAt(jail, now + 850);
        prisoner.assignCustodyOfficer(officer.getUUID());
        officer.assignPrisonerEscort(prisoner.getUUID(), now + 850);
        if (officer.distanceToSqr(prisoner) <= 9.0
            || prisoner.blockPosition().equals(jail))
            throw new AssertionError("Fixture skipped the GUI three-block leash stall");

        final net.minecraft.world.phys.Vec3[] last = {prisoner.position()};
        for (int at = 10; at <= 310; at += 10)
            helper.runAfterDelay(at, () -> {
                if (prisoner.position().distanceToSqr(last[0]) > 36)
                    helper.fail("Detainee teleported instead of walking",
                        prisoner.blockPosition());
                last[0] = prisoner.position();
            });
        helper.runAfterDelay(340, () -> {
            if (!prisoner.isAlive() || !officer.isAlive()
                || !prisoner.blockPosition().equals(jail)
                || prisoner.custodyOfficer() != null
                || officer.escortedPrisoner() != null
                || CityJailGate.isOpen(level, jail)) {
                helper.fail("Real rear-door escort never completed handoff and lock:"
                    + " prisoner=" + prisoner.blockPosition()
                    + " officer=" + officer.blockPosition()
                    + " cell=" + jail
                    + " prisonerAction=" + prisoner.lastAction()
                    + " officerAction=" + officer.lastAction(), jail);
                return;
            }
            helper.succeed();
        });
    }
}
