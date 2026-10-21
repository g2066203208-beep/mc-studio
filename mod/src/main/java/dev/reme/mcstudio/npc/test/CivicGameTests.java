package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CivicMind;
import dev.reme.mcstudio.npc.life.CharacterProfile;
import dev.reme.mcstudio.npc.life.Commission;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

/** Real game-server registry + serialization checks (not fake pure Java tests). */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CivicGameTests {
    private CivicGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void resident_can_spawn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("registered NPC missing");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        npc.setHomePos(pos);
        npc.setWorkPos(pos);
        if (!level.addFreshEntity(npc) || !npc.isAlive())
            throw new AssertionError("cannot spawn living-world NPC in game server");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void social_and_contract_survive_nbt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("registry not loaded");
        UUID actor = UUID.randomUUID();
        npc.setCharacter(new CharacterProfile(80, 92, 60, 73));
        npc.civic().witness(UUID.randomUUID(), actor, npc.getUUID(),
            CivicMind.Kind.ASSAULT, 1234);
        Commission commission = new Commission(Commission.Goods.WHEAT, 5, 3);
        if (!commission.accept(actor)) throw new AssertionError("cannot accept");
        npc.setCommission(commission);
        CompoundTag saved = new CompoundTag();
        npc.addAdditionalSaveData(saved);

        PlayerNpcEntity loaded = NpcRegistry.PLAYER_NPC.get().create(level);
        if (loaded == null) throw new AssertionError("registry missing after saving");
        loaded.readAdditionalSaveData(saved);
        if (loaded.character().justice() != 92 ||
            loaded.civic().reputation(actor) != -60 ||
            loaded.commission() == null ||
            !loaded.commission().canComplete(actor) ||
            loaded.commission().emeraldReward() != 3)
            throw new AssertionError("identity, evidence or escrow state lost on NBT round-trip");
        helper.succeed();
    }
}
