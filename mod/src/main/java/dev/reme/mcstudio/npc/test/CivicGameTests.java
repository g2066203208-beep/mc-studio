package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CivicMind;
import dev.reme.mcstudio.npc.life.CharacterProfile;
import dev.reme.mcstudio.npc.life.Commission;
import dev.reme.mcstudio.npc.life.CivicContracts;
import dev.reme.mcstudio.npc.life.CivicSocial;
import dev.reme.mcstudio.npc.life.SocietyRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.MobSpawnType;
import dev.reme.mcstudio.npc.life.MovementPolicy;
import dev.reme.mcstudio.npc.life.DailyRoaming;
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
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nearby_gossip_is_not_global(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity witness = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity neighbor = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity outsider = NpcRegistry.PLAYER_NPC.get().create(level);
        if (witness == null || neighbor == null || outsider == null)
            throw new AssertionError("NPC registry missing");
        outsider.setIdentity(SocietyRules.civilian("distant"));
        UUID attacker = UUID.randomUUID();
        witness.civic().witness(UUID.randomUUID(), attacker, witness.getUUID(),
            CivicMind.Kind.ASSAULT, 100);
        if (CivicSocial.exchangeNews(witness, neighbor, 200) != 1)
            throw new AssertionError("local trusted conversation failed");
        if (neighbor.civic().reputation(attacker) != -30)
            throw new AssertionError("hearsay credibility must be weaker");
        if (CivicSocial.exchangeNews(neighbor, outsider, 300) != 0 ||
            outsider.civic().reputation(attacker) != 0)
            throw new AssertionError("regional boundaries or rumor recursion broken");
        if (CivicSocial.exchangeNews(witness, neighbor, 350) != 0)
            throw new AssertionError("same rumor delivered twice");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void job_escrow_moves_real_emeralds(GameTestHelper helper) {
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(helper.getLevel());
        if (npc == null) throw new AssertionError("NPC registry missing");
        npc.backpack().addItem(new ItemStack(Items.EMERALD, 5));
        if (!CivicContracts.post(npc, Commission.Goods.WHEAT, 3, 2) ||
            npc.backpack().countItem(Items.EMERALD) != 3)
            throw new AssertionError("posting did not escrow two actual emeralds");
        if (CivicContracts.post(npc, Commission.Goods.BREAD, 1, 1))
            throw new AssertionError("overwrote an active contract");
        if (!CivicContracts.cancel(npc) ||
            npc.backpack().countItem(Items.EMERALD) != 5)
            throw new AssertionError("cancel did not refund escrow exactly");
        if (CivicContracts.cancel(npc))
            throw new AssertionError("refunded same escrow twice");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void natural_resident_gets_real_home_and_bounded_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity resident = NpcRegistry.PLAYER_NPC.get().create(level);
        if (resident == null) throw new AssertionError("entity type not registered");
        BlockPos position = helper.absolutePos(new BlockPos(2, 1, 2));
        resident.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
        resident.finalizeSpawn(level, level.getCurrentDifficultyAt(position),
            MobSpawnType.NATURAL, null);
        if (!resident.naturalResident() || resident.mode() != PlayerNpcEntity.Mode.LIVE ||
            resident.roamingRadius() < 32 || resident.roamingRadius() > 64 ||
            !resident.homePos().equals(position) ||
            resident.backpack().countItem(Items.BREAD) != 2)
            throw new AssertionError("natural resident missing initial life, shelter or inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mobility_radius_survives_entity_nbt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity before = NpcRegistry.PLAYER_NPC.get().create(level);
        if (before == null) throw new AssertionError("missing entity");
        BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        before.setHomePos(anchor);
        before.setRoamingRadius(42);
        CompoundTag data = new CompoundTag();
        before.addAdditionalSaveData(data);

        PlayerNpcEntity after = NpcRegistry.PLAYER_NPC.get().create(level);
        if (after == null) throw new AssertionError("missing entity after save");
        after.readAdditionalSaveData(data);
        if (!after.homePos().equals(anchor) || after.roamingRadius() != 42 ||
            !DailyRoaming.inRange(after, anchor.offset(30, 0, 20)) ||
            DailyRoaming.inRange(after, anchor.offset(60, 0, 0)))
            throw new AssertionError("saved roaming territory not restored correctly");
        helper.succeed();
    }

}
