package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.PublicSafety;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Covers the transition missing UUID -> loaded real NPC -> resumed reciprocal
 * escort. The older missing-UUID test only checked that the guard waited.
 * This is NOT a full chunk save/unload/reload integration test.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CustodyEscortEntityReappearGameTests {
    private CustodyEscortEntityReappearGameTests() {}

    private static PlayerNpcEntity spawn(ServerLevel level, BlockPos feet,
                                         PlayerNpcEntity.Profession profession,
                                         UUID uuid) {
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("Real NPC type not registered");
        if (uuid != null) npc.setUUID(uuid); // Must precede entity manager insertion.
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(profession);
        npc.setHomePos(feet);
        npc.setRoamingRadius(64);
        npc.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 0);
        if (!level.addFreshEntity(npc))
            throw new AssertionError("Could not spawn real NPC at " + feet);
        return npc;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void absent_prisoner_reappears_with_same_uuid_and_escort_resumes(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos jail = base.south(4);
        level.setBlock(jail.below(), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        level.setBlock(jail, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(jail.above(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(jail.east(), Blocks.IRON_BARS.defaultBlockState(), 2);
        if (!PublicSafety.holdingSpot(level, jail))
            throw new AssertionError("Fixture lacks real iron-bar holding spot");

        PlayerNpcEntity officer = spawn(level, base, PlayerNpcEntity.Profession.GUARD, null);
        UUID prisonerId = UUID.randomUUID();
        long now = level.getGameTime();
        officer.assignPrisonerEscort(prisonerId, now + 80);
        if (level.getEntity(prisonerId) != null)
            throw new AssertionError("Absent-prisoner fixture was loaded");
        if (!PublicSafety.tickGuard(officer, level, now)
            || !prisonerId.equals(officer.escortedPrisoner()))
            throw new AssertionError("Officer dropped unloaded prisoner UUID");

        // Reappearance is simulated by registering a REAL NPC with the same
        // persisted UUID. No teleport, force-load, or duplicate entity.
        PlayerNpcEntity prisoner = spawn(level, base.south(1),
            PlayerNpcEntity.Profession.RESIDENT, prisonerId);
        prisoner.detainAt(jail, now + 80);
        prisoner.assignCustodyOfficer(officer.getUUID());
        if (level.getEntity(prisonerId) != prisoner)
            throw new AssertionError("Reloaded UUID not registered as a real entity");
        if (!PublicSafety.tickGuard(officer, level, now + 1)
            || !prisonerId.equals(officer.escortedPrisoner())
            || !PublicSafety.escortedPrisonerMayAdvance(prisoner, level, now + 1))
            throw new AssertionError("Officer failed to resume reciprocal on-foot custody");

        if (PublicSafety.tickGuard(officer, level, now + 80)
            || officer.escortedPrisoner() != null)
            throw new AssertionError("Reappeared prisoner bypassed original escort deadline");
        helper.succeed();
    }
}
