package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.NpcPose;
import dev.reme.mcstudio.npc.player.RealNpcServerPlayer;
import dev.reme.mcstudio.npc.NpcPoseSelection;
import dev.reme.mcstudio.npc.life.CivicMind;
import dev.reme.mcstudio.npc.life.CharacterProfile;
import dev.reme.mcstudio.npc.life.Commission;
import dev.reme.mcstudio.npc.life.CivicContracts;
import dev.reme.mcstudio.npc.life.CivicSocial;
import dev.reme.mcstudio.npc.life.SocietyRules;
import dev.reme.mcstudio.npc.life.PlayerActionBridge;
import dev.reme.mcstudio.npc.life.PlayerMiningActions;
import dev.reme.mcstudio.npc.life.PlayerLootPickup;
import dev.reme.mcstudio.npc.life.NpcPlayerActor;
import dev.reme.mcstudio.npc.life.FootTrafficData;
import dev.reme.mcstudio.npc.life.EmergentFootpaths;
import dev.reme.mcstudio.npc.life.EmergentSettlements;
import dev.reme.mcstudio.npc.life.BanditActions;
import dev.reme.mcstudio.npc.life.PublicSafety;
import dev.reme.mcstudio.npc.life.CaravanRoutes;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import dev.reme.mcstudio.npc.life.SettlementLedger;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import dev.reme.mcstudio.npc.life.MovementPolicy;
import dev.reme.mcstudio.npc.life.DailyRoaming;
import dev.reme.mcstudio.npc.life.NaturalResidentSpawn;
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
            !resident.identity().region().equals(
                MovementPolicy.region(position.getX(), position.getZ())) ||
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

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void local_spawn_cap_prevents_overpopulation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        // GameTests share one live level; another test's NPCs may be nearby.
        // Do not assume the global fixture starts empty.
        for (int i = 0; i < NaturalResidentSpawn.MAX_WITHIN_72_BLOCKS; i++) {
            PlayerNpcEntity resident = NpcRegistry.PLAYER_NPC.get().create(level);
            if (resident == null) throw new AssertionError("entity type missing");
            resident.moveTo(base.getX() + i + 0.5, base.getY(),
                base.getZ() + 0.5, 0, 0);
            if (!level.addFreshEntity(resident))
                throw new AssertionError("failed adding test resident");
        }
        if (!NaturalResidentSpawn.atCapacity(level, base))
            throw new AssertionError("natural spawn population cap not enforced");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void real_plains_biome_contains_live_npc_spawn_rule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var plains = level.registryAccess().registryOrThrow(Registries.BIOME)
            .getHolderOrThrow(Biomes.PLAINS).value();
        boolean registered = plains.getMobSettings().getMobs(MobCategory.CREATURE)
            .unwrap().stream().anyMatch(data -> data.type == NpcRegistry.PLAYER_NPC.get());
        if (!registered) throw new AssertionError(
            "Natural NPC biome modifier did not apply to actual plains registry");
        if (!NaturalResidentSpawn.allowedNaturalReason(MobSpawnType.CHUNK_GENERATION) ||
            !NaturalResidentSpawn.allowedNaturalReason(MobSpawnType.NATURAL) ||
            NaturalResidentSpawn.allowedNaturalReason(MobSpawnType.MOB_SUMMONED))
            throw new AssertionError("natural spawn reason is incorrectly filtered");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void actual_npc_pathfinding_moves_entity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        for (int dx = 0; dx < 10; dx++) {
            BlockPos foot = origin.offset(dx, -1, 0);
            level.setBlock(foot, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(foot.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("entity missing");
        npc.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        npc.setHomePos(origin);
        npc.setWorkPos(origin);
        npc.setMode(PlayerNpcEntity.Mode.STAY); // isolate vanilla nav from schedules
        if (!level.addFreshEntity(npc)) throw new AssertionError("cannot spawn NPC");
        helper.runAfterDelay(3, () -> {
            boolean route = npc.getNavigation().moveTo(
                origin.getX() + 8.5, origin.getY(), origin.getZ() + 0.5, 1.0);
            if (!route) helper.fail("NPC navigation could not construct path", origin);
        });
        helper.runAfterDelay(100, () -> {
            if (npc.distanceToSqr(origin.getX() + 0.5, origin.getY(),
                origin.getZ() + 0.5) < 4.0)
                helper.fail("NPC remained immobile despite walkable stone path", origin);
            else helper.succeed();
        });
    }


    @GameTest(template = "empty", timeoutTicks = 90)
    public static void actual_npc_demo_command_creates_six_named_residents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(5, 1, 5));
        // Simulate a player command in the real Minecraft command dispatcher.
        var player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(origin.getX() + 0.5, origin.getY(),
            origin.getZ() + 0.5, 0, 0);
        // Regression: this unrelated NPC is intentionally closer to Dev
        // than the three allied demo settler homes. Census must find the
        // community, not just inspect the nearest, unaffiliated resident.
        level.getServer().getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(4), "npc spawn Solitary");
        level.getServer().getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(4), "npc demo");
        int found = level.getEntitiesOfClass(PlayerNpcEntity.class,
            new net.minecraft.world.phys.AABB(origin).inflate(16),
            npc -> npc.isAlive() && npc.getCustomName() != null).size();
        if (found < 6) throw new AssertionError(
            "NPC demo claims creation without six actual named server entities");
        var actualResidents = level.getEntitiesOfClass(PlayerNpcEntity.class,
            new net.minecraft.world.phys.AABB(origin).inflate(16),
            npc -> npc.isAlive() && npc.identity().faction()
                == SocietyRules.Faction.SETTLERS);
        boolean formed = actualResidents.stream().anyMatch(npc -> {
            var census = EmergentSettlements.survey(level, npc);
            return census != null && census.households() >= 3;
        });
        if (!formed) throw new AssertionError(
            "Demo's distinct real settlers never actually formed a hamlet");
        level.getServer().getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(4), "npc settlement");
        if (SettlementLedger.get(level).knownSettlements() == 0)
            throw new AssertionError("Nearest solitary NPC suppressed a valid community census");
        helper.succeed();
    }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void diagnostic_command_is_visible_without_cheats(GameTestHelper helper) {
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        if (dispatcher.getRoot().getChild("npc") == null ||
            dispatcher.getRoot().getChild("remecheck") == null ||
            dispatcher.getRoot().getChild("npc").getChild("spawn") == null ||
            dispatcher.getRoot().getChild("npc").getChild("demo") == null)
            throw new AssertionError("NPC core commands are not present on real dispatcher");
        var source = helper.getLevel().getServer().createCommandSourceStack().withPermission(0);
        try {
            var parsed = dispatcher.parse("remecheck", source);
            int result = dispatcher.execute(parsed);
            if (result != 1) throw new AssertionError("Non-op diagnostic command failed");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new AssertionError("Diagnostic command rejected at permission 0", failure);
        }
        helper.succeed();
    }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chunk_generated_npc_has_full_life_identity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC entity registration absent");
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
            MobSpawnType.CHUNK_GENERATION, null);

        if (!npc.naturalResident() ||
            !npc.homePos().equals(pos) ||
            !npc.workPos().equals(pos) ||
            !npc.identity().region().equals(
                MovementPolicy.region(pos.getX(), pos.getZ())) ||
            npc.roamingRadius() < 32 || npc.roamingRadius() > 64 ||
            npc.backpack().countItem(Items.BREAD) != 2 ||
            npc.getCustomName() == null || npc.mode() != PlayerNpcEntity.Mode.LIVE)
            throw new AssertionError("CHUNK_GENERATION skipped resident life initialization");

        CompoundTag save = new CompoundTag();
        npc.addAdditionalSaveData(save);
        PlayerNpcEntity reloaded = NpcRegistry.PLAYER_NPC.get().create(level);
        if (reloaded == null) throw new AssertionError("failed to create reloaded NPC");
        reloaded.readAdditionalSaveData(save);
        if (!reloaded.naturalResident() ||
            !reloaded.homePos().equals(pos) ||
            !reloaded.identity().region().equals(npc.identity().region()) ||
            reloaded.roamingRadius() != npc.roamingRadius())
            throw new AssertionError("chunk-generated resident NBT identity did not survive");
        helper.succeed();
    }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void real_owner_rightclick_npc_updates_social_memory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC entity type missing");
        var player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        npc.moveTo(origin.getX() + 1.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        npc.setOwner(player);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setHomePos(origin);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!level.addFreshEntity(npc)) throw new AssertionError("NPC cannot enter test server");
        int before = npc.memory().trust(player.getUUID());
        var result = npc.interact(player, InteractionHand.MAIN_HAND);
        if (!result.consumesAction() || npc.memory().trust(player.getUUID()) <= before
            || npc.memory().recentEvents().stream().noneMatch(e ->
                e.actor().equals(player.getUUID()) &&
                e.kind() == dev.reme.mcstudio.npc.life.ResidentMemory.Kind.GREETING))
            throw new AssertionError("Right-click on live owner NPC failed to generate real greeting and friendship");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void npc_skin_is_synced_and_persisted_per_character(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC entity registration absent");
        npc.setSkinId(4);
        if (npc.skinId() != 4) throw new AssertionError("EntityData skin not assigned");
        CompoundTag saved = new CompoundTag();
        npc.addAdditionalSaveData(saved);
        PlayerNpcEntity recreated = NpcRegistry.PLAYER_NPC.get().create(level);
        if (recreated == null) throw new AssertionError("NPC entity reload failed");
        recreated.readAdditionalSaveData(saved);
        if (recreated.skinId() != 4 || !saved.contains("NpcSkinId"))
            throw new AssertionError("NPC skin selection lost during NBT save/reload");
        npc.setSkinId(900);
        if (npc.skinId() != 5) throw new AssertionError("Out-of-range skin id unbounded");
        npc.setSkinId(-999);
        if (npc.skinId() != 0) throw new AssertionError("Negative skin id unbounded");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 195)
    public static void guard_actually_walks_patrol_in_live_mode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Keep the arena twelve blocks above the other concurrently running
        // GameTests: their world edits must not erase this patrol platform.
        BlockPos center = helper.absolutePos(new BlockPos(10, 12, 10));
        // A genuine traversable 21x21 stone lane; no fake navigation mock.
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                BlockPos floor = center.offset(dx, -1, dz);
                level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        PlayerNpcEntity guard = NpcRegistry.PLAYER_NPC.get().create(level);
        if (guard == null) throw new AssertionError("guard cannot be created");
        guard.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0, 0);
        guard.setHomePos(center);
        guard.setWorkPos(center);
        guard.setRoamingRadius(32);
        guard.setProfession(PlayerNpcEntity.Profession.GUARD);
        guard.setMode(PlayerNpcEntity.Mode.LIVE);
        if (!level.addFreshEntity(guard))
            throw new AssertionError("guard failed to enter real server");
        helper.runAfterDelay(150, () -> {
            // Verify actual horizontal walking, not gravity or a teleport.
            double dx = guard.getX() - (center.getX() + 0.5);
            double dz = guard.getZ() - (center.getZ() + 0.5);
            if (dx * dx + dz * dz < 2.25) {
                helper.fail("Guard failed to patrol: action=" + guard.lastAction()
                    + ", energy=" + guard.energy() + ", hunger=" + guard.hunger()
                    + ", navigationDone=" + guard.getNavigation().isDone()
                    + ", position=" + guard.blockPosition().toShortString(), center);
            } else if (Math.abs(guard.getY() - center.getY()) > 1.5) {
                helper.fail("Guard left the walkable patrol platform vertically", center);
            } else helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void real_player_skin_command_changes_owned_npc(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        var player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(origin.getX()+0.5, origin.getY(), origin.getZ()+0.5, 0, 0);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("No NPC type");
        npc.moveTo(origin.getX()+1.5, origin.getY(), origin.getZ()+0.5, 0, 0);
        npc.setOwner(player);
        npc.setSkinId(0);
        if (!level.addFreshEntity(npc)) throw new AssertionError("No NPC entity");
        level.getServer().getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(4), "npc skin 4");
        if (npc.skinId()!=4) throw new AssertionError(
            "The REAL /npc skin 4 dispatcher failed to mutate owned NPC synchronized skin");
        CompoundTag save = new CompoundTag();
        npc.addAdditionalSaveData(save);
        if (save.getInt("NpcSkinId") != 4)
            throw new AssertionError("Command-selected skin not persisted");
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void player_item_pipeline_places_a_real_npc_owned_block(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Raised standalone platform: avoid touching the other concurrent tests.
        BlockPos target = helper.absolutePos(new BlockPos(4, 9, 4));
        level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC missing");
        npc.moveTo(target.getX() + 2.5, target.getY(),
            target.getZ() + 0.5, 0, 0);
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        npc.setHomePos(target);
        npc.setWorkPos(target);
        npc.backpack().setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        if (!level.addFreshEntity(npc)) throw new AssertionError("Cannot add NPC");

        if (PlayerActionBridge.placeOwnedBlock(npc, level, target, 0, Blocks.COBBLESTONE))
            throw new AssertionError("Unarmed NPC could modify blocks");
        if (!level.getBlockState(target).isAir() ||
            npc.backpack().getItem(0).getCount() != 2)
            throw new AssertionError("Denied build changed world or consumed materials");

        npc.setBuildEnabled(true);
        if (!PlayerActionBridge.placeOwnedBlock(npc, level, target, 0, Blocks.COBBLESTONE))
            throw new AssertionError("Real BlockItem interaction failed to place block");
        if (!level.getBlockState(target).is(Blocks.COBBLESTONE)
            || npc.backpack().getItem(0).getCount() != 1)
            throw new AssertionError("Vanilla placement did not conserve NPC material");

        if (PlayerActionBridge.placeOwnedBlock(npc, level, target, 0, Blocks.COBBLESTONE)
            || npc.backpack().getItem(0).getCount() != 1)
            throw new AssertionError("Placed block twice into occupied target");
        BlockPos second = target.north();
        level.setBlock(second.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(second, Blocks.AIR.defaultBlockState(), 3);
        if (PlayerActionBridge.placeOwnedBlock(npc, level, second, 0, Blocks.OAK_PLANKS)
            || npc.backpack().getItem(0).getCount() != 1
            || !level.getBlockState(second).isAir())
            throw new AssertionError("Wrong item type bypassed BlockItem inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 190)
    public static void player_mining_real_tool_progress_drops_and_wear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(4, 9, 4));
        BlockPos home = target.east(2);
        for (int x = -3; x <= 4; x++) for (int z = -3; z <= 3; z++) {
            BlockPos foot = target.offset(x, -1, z);
            level.setBlock(foot, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(foot.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("Mining NPC missing");
        var operator = FakePlayerFactory.getMinecraft(level);
        operator.moveTo(home.getX() + 1.5, home.getY(),
            home.getZ() + 0.5, 0, 0);
        npc.moveTo(home.getX() + 0.5, home.getY(),
            home.getZ() + 0.5, 0, 0);
        npc.setOwner(operator);
        npc.setHomePos(home);
        npc.setWorkPos(home);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        if (!level.addFreshEntity(npc)) throw new AssertionError("NPC not loaded");
        String order = "npc mine " + target.getX() + " " + target.getY()
            + " " + target.getZ();
        level.getServer().getCommands().performPrefixedCommand(
            operator.createCommandSourceStack().withPermission(4), order);
        if (!target.equals(npc.miningTarget()))
            throw new AssertionError("Real /npc mine dispatcher did not accept authorized order");

        helper.runAfterDelay(3, () -> {
            if (level.getBlockState(target).isAir())
                helper.fail("Mining ignored survival tool speed and destroyed instantly", target);
        });
        helper.runAfterDelay(140, () -> {
            if (!level.getBlockState(target).isAir())
                helper.fail("Vanilla break pipeline did not remove assigned stone; lastAction="
                    + npc.lastAction(), target);
            else if (npc.miningTarget() != null || npc.getMainHandItem().isEmpty()
                    || npc.getMainHandItem().getDamageValue() < 1)
                helper.fail("Mining target or real pickaxe durability was not updated", target);
            else {
                long cobble = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(target).inflate(3.0),
                    drop -> drop.getItem().is(Items.COBBLESTONE))
                    .stream().mapToLong(drop -> drop.getItem().getCount()).sum();
                long stored = npc.backpack().countItem(Items.COBBLESTONE);
                if (cobble + stored != 1)
                    helper.fail("Real mining loot not conserved across world and backpack: "
                        + "world=" + cobble + ", bag=" + stored, target);
                else helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mining_wrong_tool_never_modifies_world(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 9, 4));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, Blocks.IRON_ORE.defaultBlockState(), 3);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC missing");
        npc.moveTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        npc.setHomePos(pos);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_PICKAXE));
        npc.setMiningTarget(pos);
        if (!level.addFreshEntity(npc)) throw new AssertionError("NPC not loaded");
        helper.runAfterDelay(15, () -> {
            if (!level.getBlockState(pos).is(Blocks.IRON_ORE)
                || npc.miningTarget() != null
                || npc.getMainHandItem().getDamageValue() != 0)
                helper.fail("Wrong-tier mining damaged tool/ore or failed to cancel", pos);
            else helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mining_order_nbt_restores_target_but_not_partial_progress(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC missing");
        BlockPos target = helper.absolutePos(new BlockPos(5, 9, 5));
        npc.setMiningTarget(target);
        npc.advanceMiningProgress(0.65F);
        CompoundTag tag = new CompoundTag();
        npc.addAdditionalSaveData(tag);
        PlayerNpcEntity reload = NpcRegistry.PLAYER_NPC.get().create(level);
        if (reload == null) throw new AssertionError("NPC reload create missing");
        reload.readAdditionalSaveData(tag);
        if (!target.equals(reload.miningTarget()) || reload.miningProgress() != 0.0F)
            throw new AssertionError("Reload must retain order but restart break duration");
        reload.clearMiningTarget();
        CompoundTag empty = new CompoundTag();
        reload.addAdditionalSaveData(empty);
        if (empty.contains("NpcMiningTarget"))
            throw new AssertionError("Cancelled mining orders must not persist");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 185)
    public static void mining_respects_real_neoforge_protection_break_cancel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 9, 4));
        BlockPos stand = pos.east(2);
        for (int x = -1; x <= 4; x++) for (int z = -2; z <= 2; z++) {
            BlockPos ground = pos.offset(x, -1, z);
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(ground.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("Missing protected mining NPC");
        npc.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
        npc.setHomePos(stand);
        npc.setWorkPos(stand);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        npc.setMiningTarget(pos);
        if (!level.addFreshEntity(npc)) throw new AssertionError("Protected NPC not added");
        AtomicBoolean observed = new AtomicBoolean(false);
        Consumer<BlockEvent.BreakEvent> veto = event -> {
            if (event.getPos().equals(pos) && event.getPlayer().getUUID().equals(npc.getUUID())) {
                observed.set(true);
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(veto);
        helper.runAfterDelay(140, () -> {
            NeoForge.EVENT_BUS.unregister(veto);
            if (!observed.get())
                helper.fail("FakePlayer bypassed actual NeoForge BlockEvent.BreakEvent", pos);
            else if (!level.getBlockState(pos).is(Blocks.STONE))
                helper.fail("Protection cancellation ignored: stone was destroyed", pos);
            else if (npc.getMainHandItem().getDamageValue() != 0 || npc.miningTarget() != null)
                helper.fail("Canceled mining consumed tool or retried a denied order", pos);
            else helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void player_action_context_reuses_one_fake_player_per_npc(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("Missing NPC");
        var before = NpcPlayerActor.forNpc(npc, level);
        var after = NpcPlayerActor.forNpc(npc, level);
        if (before != after || !before.getUUID().equals(npc.getUUID()))
            throw new AssertionError("Player actions created inconsistent FakePlayer identities");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mined_drop_pickup_conserves_partial_stack_and_skips_unrelated_loot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 9, 4));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("missing player-like resident");
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        npc.setHomePos(pos);
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        if (!level.addFreshEntity(npc)) throw new AssertionError("NPC not loaded");

        // 35 other full stacks and exactly one spare cobblestone slot.
        npc.backpack().setItem(0, new ItemStack(Items.COBBLESTONE, 63));
        for (int i = 1; i < 36; i++)
            npc.backpack().setItem(i, new ItemStack(Items.DIRT, 64));

        ItemEntity mined = new ItemEntity(level, npc.getX(), npc.getY(),
            npc.getZ(), new ItemStack(Items.COBBLESTONE, 4));
        mined.setNoPickUpDelay();
        ItemEntity unrelated = new ItemEntity(level, npc.getX(), npc.getY(),
            npc.getZ(), new ItemStack(Items.DIAMOND, 2));
        unrelated.setNoPickUpDelay();
        if (!level.addFreshEntity(mined) || !level.addFreshEntity(unrelated))
            throw new AssertionError("Real item entities failed to spawn");
        npc.claimMiningDrops(java.util.Map.of(mined.getUUID(), 4), level.getGameTime() + 80);
        if (!PlayerLootPickup.pickUp(npc, mined))
            throw new AssertionError("Claimed mined item could not partially enter real bag");
        if (npc.backpack().countItem(Items.COBBLESTONE) != 64
            || !mined.isAlive() || mined.getItem().getCount() != 3
            || !unrelated.isAlive() || unrelated.getItem().getCount() != 2)
            throw new AssertionError("Partial pickup duplicated/deleted items or stole unrelated loot");

        if (PlayerLootPickup.pickUp(npc, mined)
            || !mined.isAlive() || mined.getItem().getCount() != 3)
            throw new AssertionError("Full backpack removed or vacuumed an item");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void mined_drop_claims_survive_reload_and_expire_without_items(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("missing resident");
        UUID id = UUID.randomUUID();
        long deadline = level.getGameTime() + 30;
        npc.claimMiningDrops(java.util.Map.of(id, 3), deadline);
        CompoundTag tag = new CompoundTag();
        npc.addAdditionalSaveData(tag);
        PlayerNpcEntity restored = NpcRegistry.PLAYER_NPC.get().create(level);
        if (restored == null) throw new AssertionError("cannot restore resident");
        restored.readAdditionalSaveData(tag);
        if (!restored.pendingMiningDrops().contains(id)
            || restored.remainingMiningDrop(id) != 3
            || restored.miningLootExpiry() != deadline)
            throw new AssertionError("Lost exact item UUID claim on NBT save/load");
        restored.forgetMiningDrop(id);
        CompoundTag after = new CompoundTag();
        restored.addAdditionalSaveData(after);
        if (after.contains("NpcMiningDrops"))
            throw new AssertionError("Completed claim should not remain in saved NBT");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 230)
    public static void real_mined_stone_becomes_one_stored_cobblestone_no_double_pickup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ore = helper.absolutePos(new BlockPos(4, 9, 4));
        BlockPos stand = ore.east(2);
        for (int x = -3; x <= 4; x++) for (int z = -3; z <= 3; z++) {
            BlockPos ground = ore.offset(x, -1, z);
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(ground.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(ore, Blocks.STONE.defaultBlockState(), 3);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("Cannot create mining resident");
        npc.moveTo(stand.getX() + 0.5, stand.getY(),
            stand.getZ() + 0.5, 0, 0);
        npc.setHomePos(stand);
        npc.setWorkPos(stand);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        npc.setMiningTarget(ore);
        if (!level.addFreshEntity(npc)) throw new AssertionError("Cannot spawn mining resident");
        // Do not shortcut the block/item pipeline by directly calling pickup
        // on artificial drops. Wait for true survival mining + entity pickup.
        helper.runAfterDelay(180, () -> {
            long bag = npc.backpack().countItem(Items.COBBLESTONE);
            long world = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(ore).inflate(4), drop -> drop.getItem().is(Items.COBBLESTONE))
                .stream().mapToLong(drop -> drop.getItem().getCount()).sum();
            if (!level.getBlockState(ore).isAir())
                helper.fail("NPC did not mine the physical stone", ore);
            else if (npc.getMainHandItem().getDamageValue() != 1)
                helper.fail("Real iron pickaxe not worn exactly once", ore);
            else if (bag != 1 || world != 0)
                helper.fail("Mined drop not picked up exactly once: bag=" + bag
                    + " world=" + world + " claims=" + npc.pendingMiningDrops().size()
                    + " action=" + npc.lastAction(), ore);
            else helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void merged_item_entity_does_not_expand_npc_mined_entitlement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 9, 4));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("Missing NPC");
        npc.moveTo(pos.getX() + 0.5, pos.getY(),
            pos.getZ() + 0.5, 0, 0);
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        if (!level.addFreshEntity(npc)) throw new AssertionError("NPC spawn failed");
        ItemEntity mixed = new ItemEntity(level, npc.getX(), npc.getY(),
            npc.getZ(), new ItemStack(Items.COBBLESTONE, 20));
        mixed.setNoPickUpDelay();
        if (!level.addFreshEntity(mixed)) throw new AssertionError("Drop spawn failed");
        // Only three blocks of this mixed stack came from this NPC's mining.
        // Its other seventeen belong to pre-existing world inventory.
        npc.claimMiningDrops(java.util.Map.of(mixed.getUUID(), 3),
            level.getGameTime() + 80);
        if (!PlayerLootPickup.pickUp(npc, mixed)
            || npc.backpack().countItem(Items.COBBLESTONE) != 3
            || mixed.getItem().getCount() != 17
            || npc.remainingMiningDrop(mixed.getUUID()) != 0)
            throw new AssertionError("Merged ground items expanded claim quantity");
        if (PlayerLootPickup.pickUp(npc, mixed)
            || npc.backpack().countItem(Items.COBBLESTONE) != 3
            || mixed.getItem().getCount() != 17)
            throw new AssertionError("Already paid claim could collect remaining player-owned items");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void organic_foot_traffic_needs_real_repeat_walkers_and_is_persistent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(4, 9, 4));
        FootTrafficData data = FootTrafficData.get(level);
        boolean originalOptIn = data.physicalPathsEnabled();
        data.setPhysicalPathsEnabled(false);
        UUID one = UUID.randomUUID();
        if (!data.countPassage(one, foot, 100)
            || data.countPassage(one, foot, 101)
            || data.visits(foot) != 1)
            throw new AssertionError("Standing NPC duplicated traffic without a new walker");
        for (int i = 1; i < FootTrafficData.PATH_THRESHOLD; i++)
            if (!data.countPassage(UUID.randomUUID(), foot, 100 + i))
                throw new AssertionError("Actual distinct residents did not add foot traffic");
        if (data.visits(foot) != FootTrafficData.PATH_THRESHOLD)
            throw new AssertionError("Seven real traversals should create a desire path");
        CompoundTag saved = new CompoundTag();
        data.save(saved, level.registryAccess());
        FootTrafficData reloaded = FootTrafficData.load(saved, level.registryAccess());
        if (reloaded.visits(foot) != FootTrafficData.PATH_THRESHOLD
            || reloaded.physicalPathsEnabled() ||
            reloaded.trackedCells() < 1)
            throw new AssertionError("Per-dimension road memory lost its saved heatmap");
        if (!reloaded.countPassage(one, foot, 500) || reloaded.visits(foot) != 8)
            throw new AssertionError("Returning after the debounce must count");
        data.setPhysicalPathsEnabled(originalOptIn);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void crowd_created_path_uses_real_vanilla_shovel_and_terrain_opt_in(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(4, 9, 4));
        BlockPos ground = foot.below();
        level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(foot, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), 3);
        var resident = NpcRegistry.PLAYER_NPC.get().create(level);
        if (resident == null) throw new AssertionError("No live resident");
        resident.moveTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, 0, 0);
        resident.setOnGround(true);
        resident.setMode(PlayerNpcEntity.Mode.STAY);
        if (!level.addFreshEntity(resident)) throw new AssertionError("Resident spawn failed");
        FootTrafficData data = FootTrafficData.get(level);
        boolean previous = data.physicalPathsEnabled();
        data.setPhysicalPathsEnabled(false);
        for (int i = 0; i < FootTrafficData.PATH_THRESHOLD; i++)
            if (!data.countPassage(UUID.randomUUID(), foot, 10L + i))
                throw new AssertionError("Real cumulative traffic did not reach path threshold");
        if (EmergentFootpaths.tryWearPath(resident, level, foot)
            || !level.getBlockState(ground).is(Blocks.GRASS_BLOCK))
            throw new AssertionError("Road changed even when physical paths were disabled");
        data.setPhysicalPathsEnabled(true);
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            data.setPhysicalPathsEnabled(previous);
            helper.fail("Test world unexpectedly disables mobGriefing", ground);
            return;
        }
        if (!EmergentFootpaths.tryWearPath(resident, level, foot)
            || !level.getBlockState(ground).is(Blocks.DIRT_PATH)) {
            data.setPhysicalPathsEnabled(previous);
            helper.fail("Real shovel item use did not form a dirt path on eligible grass", ground);
            return;
        }
        // Never trample cultivated farm fields.
        BlockPos farmland = foot.east(2);
        level.setBlock(farmland.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(farmland, Blocks.AIR.defaultBlockState(), 3);
        if (EmergentFootpaths.tryWearPath(resident, level, farmland)
            || !level.getBlockState(farmland.below()).is(Blocks.FARMLAND)) {
            data.setPhysicalPathsEnabled(previous);
            helper.fail("Physical desire path trampled farmland", farmland);
            return;
        }
        data.setPhysicalPathsEnabled(previous);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void organic_hamlet_requires_three_distinct_allied_homes_and_records_history(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 9, 4));
        PlayerNpcEntity[] residents = new PlayerNpcEntity[3];
        for (int i = 0; i < 3; i++) {
            PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
            if (npc == null) throw new AssertionError("NPC type unregistered");
            BlockPos home = center.offset(i * 3, 0, 0);
            npc.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
            npc.setIdentity(SocietyRules.civilian("organic_test"));
            npc.setHomePos(home);
            npc.setWorkPos(home);
            npc.setMode(PlayerNpcEntity.Mode.LIVE);
            if (!level.addFreshEntity(npc)) throw new AssertionError("Cannot add real household");
            residents[i] = npc;
        }
        var established = EmergentSettlements.survey(level, residents[0]);
        if (established == null || established.households() != 3
            || !established.tier().equals("hamlet"))
            throw new AssertionError("Three allied independent resident homes did not form a hamlet");
        var ledger = SettlementLedger.get(level);
        ledger.record(established.founder(), established.hub(),
            established.households(), level.getGameTime(), established.culture());
        if (ledger.get(established.founder()) == null)
            throw new AssertionError("Hamlet founding not recorded");
        CompoundTag save = new CompoundTag();
        ledger.save(save, level.registryAccess());
        var restored = SettlementLedger.load(save, level.registryAccess());
        if (restored.get(established.founder()) == null
            || restored.get(established.founder()).households() != 3)
            throw new AssertionError("Real household founding history lost on save/reload");
        residents[2].setIdentity(new SocietyRules.Identity(
            SocietyRules.Nation.OUTLANDS, SocietyRules.Faction.RAIDERS, "organic_test"));
        if (EmergentSettlements.survey(level, residents[0]) != null)
            throw new AssertionError("Hostile raider was counted as an allied household");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hostile_raider_steals_only_real_caravan_goods_and_creates_evidence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 9, 5));
        PlayerNpcEntity courier = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity raider = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity witness = NpcRegistry.PLAYER_NPC.get().create(level);
        if (courier == null || raider == null || witness == null)
            throw new AssertionError("NPC registry missing");
        for (int i = 0; i < 3; i++) {
            BlockPos ground = pos.offset(i, -1, 0);
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(ground.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        courier.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        raider.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        witness.moveTo(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        courier.setIdentity(SocietyRules.civilian("caravan_case"));
        witness.setIdentity(SocietyRules.civilian("caravan_case"));
        raider.setIdentity(new SocietyRules.Identity(
            SocietyRules.Nation.OUTLANDS, SocietyRules.Faction.RAIDERS, "caravan_case"));
        courier.setProfession(PlayerNpcEntity.Profession.COURIER);
        courier.setMode(PlayerNpcEntity.Mode.STAY);
        raider.setMode(PlayerNpcEntity.Mode.LIVE);
        witness.setMode(PlayerNpcEntity.Mode.STAY);
        raider.setHomePos(pos);
        courier.backpack().addItem(new ItemStack(Items.WHEAT, 5));
        if (!level.addFreshEntity(courier) || !level.addFreshEntity(raider)
            || !level.addFreshEntity(witness)) throw new AssertionError("NPC add failed");
        long now = level.getGameTime();
        if (!BanditActions.tick(raider, level, now))
            throw new AssertionError("Real nearby hostile caravan robbery did not happen");
        if (courier.backpack().countItem(Items.WHEAT) != 3
            || raider.backpack().countItem(Items.WHEAT) != 2
            || courier.civic().reputation(raider.getUUID()) >= 0
            || !courier.isAfraid(now)
            || witness.civic().reputation(raider.getUUID()) >= 0)
            throw new AssertionError("Theft duplicated wheat or failed local witness evidence");
        if (BanditActions.tick(raider, level, now + 1)
            || courier.backpack().countItem(Items.WHEAT) != 3
            || raider.backpack().countItem(Items.WHEAT) != 2)
            throw new AssertionError("Raider stole a second time before cooldown");
        CompoundTag tag = new CompoundTag();
        raider.addAdditionalSaveData(tag);
        PlayerNpcEntity reloaded = NpcRegistry.PLAYER_NPC.get().create(level);
        if (reloaded == null) throw new AssertionError("No raider reload");
        reloaded.readAdditionalSaveData(tag);
        if (reloaded.nextRaidTick() != raider.nextRaidTick())
            throw new AssertionError("Raider cooldown was lost when world saves");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void full_raider_backpack_cannot_steal_courier_wheat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 9, 5));
        PlayerNpcEntity courier = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity raider = NpcRegistry.PLAYER_NPC.get().create(level);
        if (courier == null || raider == null) throw new AssertionError("Missing NPCs");
        courier.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        raider.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        courier.setProfession(PlayerNpcEntity.Profession.COURIER);
        courier.setIdentity(SocietyRules.civilian("raid_supply"));
        raider.setIdentity(new SocietyRules.Identity(
            SocietyRules.Nation.OUTLANDS, SocietyRules.Faction.RAIDERS, "raid_supply"));
        raider.setHomePos(pos);
        raider.setMode(PlayerNpcEntity.Mode.LIVE);
        courier.setMode(PlayerNpcEntity.Mode.STAY);
        courier.backpack().addItem(new ItemStack(Items.WHEAT, 3));
        for (int i = 0; i < raider.backpack().getContainerSize(); i++)
            raider.backpack().setItem(i, new ItemStack(Items.DIRT, 64));
        if (!level.addFreshEntity(raider) || !level.addFreshEntity(courier))
            throw new AssertionError("Cannot spawn raider and courier");
        if (BanditActions.tick(raider, level, level.getGameTime())
            || courier.backpack().countItem(Items.WHEAT) != 3
            || raider.backpack().countItem(Items.WHEAT) != 0)
            throw new AssertionError("No room in raider inventory but wheat was duplicated/lost");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void terrain_protection_mod_can_cancel_emergent_path_tool_event(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(4, 9, 4));
        BlockPos ground = foot.below();
        level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        level.setBlock(foot, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(foot.above(), Blocks.AIR.defaultBlockState(), 3);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC type not registered");
        npc.moveTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, 0, 0);
        npc.setOnGround(true);
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        if (!level.addFreshEntity(npc)) throw new AssertionError("Cannot spawn traffic NPC");
        FootTrafficData data = FootTrafficData.get(level);
        boolean previous = data.physicalPathsEnabled();
        for (int i = 0; i < FootTrafficData.PATH_THRESHOLD; i++)
            data.countPassage(UUID.randomUUID(), foot, level.getGameTime() + i + 1);
        data.setPhysicalPathsEnabled(true);
        AtomicBoolean fired = new AtomicBoolean(false);
        Consumer<BlockEvent.BlockToolModificationEvent> veto = event -> {
            if (!event.isSimulated() && event.getPos().equals(ground)
                && event.getPlayer() != null
                && event.getPlayer().getUUID().equals(npc.getUUID())) {
                fired.set(true);
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            if (EmergentFootpaths.tryWearPath(npc, level, foot)
                || !level.getBlockState(ground).is(Blocks.GRASS_BLOCK))
                throw new AssertionError("Canceled vanilla shovel tool event modified protected grass");
            if (!fired.get())
                throw new AssertionError("Desire path bypassed NeoForge BlockToolModificationEvent");
        } finally {
            NeoForge.EVENT_BUS.unregister(veto);
            data.setPhysicalPathsEnabled(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void autonomous_caravan_moves_real_wheat_between_two_authorized_chests(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pickup = helper.absolutePos(new BlockPos(2, 9, 5));
        BlockPos destination = helper.absolutePos(new BlockPos(8, 9, 5));
        for (int x = -1; x <= 10; x++) for (int z = -3; z <= 3; z++) {
            BlockPos ground = pickup.offset(x, -1, z);
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(ground.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pickup, Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(destination, Blocks.CHEST.defaultBlockState(), 3);
        if (!(level.getBlockEntity(pickup) instanceof ChestBlockEntity source)
            || !(level.getBlockEntity(destination) instanceof ChestBlockEntity target))
            throw new AssertionError("Real vanilla chests did not register");
        source.setItem(0, new ItemStack(Items.WHEAT, 10));
        PlayerNpcEntity courier = NpcRegistry.PLAYER_NPC.get().create(level);
        if (courier == null) throw new AssertionError("NPC missing");
        courier.moveTo(pickup.getX() + 1.5, pickup.getY(),
            pickup.getZ() + 0.5, 0, 0);
        courier.setHomePos(pickup);
        courier.setWorkPos(pickup);
        courier.setRoamingRadius(32);
        courier.setProfession(PlayerNpcEntity.Profession.COURIER);
        courier.setMode(PlayerNpcEntity.Mode.LIVE);
        courier.setCaravanPickup(pickup);
        courier.setCaravanDropoff(destination);
        if (!level.addFreshEntity(courier)) throw new AssertionError("Courier spawn failed");
        if (!CaravanRoutes.tick(courier, level)
            || courier.backpack().countItem(Items.WHEAT) != 8
            || source.countItem(Items.WHEAT) != 2)
            throw new AssertionError("Loading from physical source chest created/lost wheat");
        // Next pulse gives vanilla navigation a real job; no courier teleport.
        if (!CaravanRoutes.tick(courier, level))
            throw new AssertionError("Real inter-depot travel not started");
        helper.runAfterDelay(160, () -> {
            int sourceLeft = source.countItem(Items.WHEAT);
            int destinationReceived = target.countItem(Items.WHEAT);
            int bagLeft = courier.backpack().countItem(Items.WHEAT);
            if (sourceLeft + destinationReceived + bagLeft != 10)
                helper.fail("Real chest-to-chest logistics duplicated wheat", pickup);
            else if (destinationReceived < 8 || courier.parcelsDelivered() < 1)
                helper.fail("Courier failed actual chest-to-chest delivery: source="
                    + sourceLeft + ", destination=" + destinationReceived
                    + ", bag=" + bagLeft + ", deliveries=" + courier.parcelsDelivered()
                    + ", action=" + courier.lastAction() + ", location="
                    + courier.blockPosition().toShortString(), destination);
            else helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void full_caravan_destination_never_deletes_existing_cargo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(2, 9, 5));
        BlockPos targetPos = helper.absolutePos(new BlockPos(5, 9, 5));
        level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), 3);
        if (!(level.getBlockEntity(targetPos) instanceof ChestBlockEntity destination))
            throw new AssertionError("Chest not created");
        for (int i = 0; i < destination.getContainerSize(); i++)
            destination.setItem(i, new ItemStack(Items.DIRT, 64));
        PlayerNpcEntity courier = NpcRegistry.PLAYER_NPC.get().create(level);
        if (courier == null) throw new AssertionError("No courier");
        courier.moveTo(targetPos.getX() + 1.5, targetPos.getY(),
            targetPos.getZ() + 0.5, 0, 0);
        courier.setMode(PlayerNpcEntity.Mode.LIVE);
        courier.setProfession(PlayerNpcEntity.Profession.COURIER);
        courier.setHomePos(sourcePos);
        courier.setCaravanPickup(sourcePos);
        courier.setCaravanDropoff(targetPos);
        courier.backpack().addItem(new ItemStack(Items.WHEAT, 4));
        if (!level.addFreshEntity(courier)) throw new AssertionError("Courier spawn failed");
        if (!CaravanRoutes.tick(courier, level)
            || courier.backpack().countItem(Items.WHEAT) != 4
            || destination.countItem(Items.WHEAT) != 0
            || destination.countItem(Items.DIRT) != destination.getContainerSize() * 64)
            throw new AssertionError("Full real destination chest stole/destroyed wheat");
        CompoundTag tag = new CompoundTag();
        courier.addAdditionalSaveData(tag);
        PlayerNpcEntity reloaded = NpcRegistry.PLAYER_NPC.get().create(level);
        if (reloaded == null) throw new AssertionError("Courier cannot reload");
        reloaded.readAdditionalSaveData(tag);
        if (!targetPos.equals(reloaded.caravanDropoff())
            || !sourcePos.equals(reloaded.caravanPickup())
            || reloaded.backpack().countItem(Items.WHEAT) != 4)
            throw new AssertionError("Route/cargo lost or duplicated after save/reload");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 350)
    public static void actual_different_professions_found_hamlet_without_player_command(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 9, 4));
        PlayerNpcEntity[] households = new PlayerNpcEntity[3];
        PlayerNpcEntity.Profession[] jobs = {
            PlayerNpcEntity.Profession.FARMER,
            PlayerNpcEntity.Profession.BAKER,
            PlayerNpcEntity.Profession.COURIER
        };
        for (int x = -2; x <= 10; x++) for (int z = -3; z <= 3; z++) {
            BlockPos ground = origin.offset(x, -1, z);
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(ground.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int n = 0; n < 3; n++) {
            PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
            if (npc == null) throw new AssertionError("Missing actual resident entity");
            BlockPos home = origin.east(n * 3);
            npc.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
            npc.setHomePos(home);
            npc.setWorkPos(home);
            npc.setProfession(jobs[n]);
            npc.setIdentity(SocietyRules.civilian("self_founded"));
            npc.setMode(PlayerNpcEntity.Mode.LIVE);
            if (!level.addFreshEntity(npc)) throw new AssertionError("Could not spawn citizen");
            households[n] = npc;
        }
        // No /npc settlement command and no direct observe() call here:
        // only their regular actual server tick may establish the chronicle.
        var census = EmergentSettlements.survey(level, households[0]);
        if (census == null || census.households() != 3)
            throw new AssertionError("Three autonomous homes did not exist");
        var foundingId = census.founder();
        if (SettlementLedger.get(level).get(foundingId) != null)
            throw new AssertionError("Hamlet was registered before residents lived their first day");
        helper.runAfterDelay(275, () -> {
            var chronicle = SettlementLedger.get(level).get(foundingId);
            if (chronicle == null || chronicle.households() < 3)
                helper.fail("Three independent LIVE occupations never autonomously registered their settlement", origin);
            else helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void npc_expressive_pose_contract_is_networked_and_bounds_checked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("Entity registry missing");
        if (!level.addFreshEntity(npc)) throw new AssertionError("NPC spawn failed");
        if (npc.visualPose() != NpcPose.IDLE
            || NpcPose.fromNetworkId(-1) != NpcPose.IDLE
            || NpcPose.fromNetworkId(Integer.MAX_VALUE) != NpcPose.IDLE)
            throw new AssertionError("Invalid visual pose network default");
        npc.setVisualPose(NpcPose.SOCIALIZE);
        if (npc.visualPose() != NpcPose.SOCIALIZE)
            throw new AssertionError("Real entity's synched pose did not update");
        npc.setVisualPose(NpcPose.WORK);
        if (npc.visualPose() != NpcPose.WORK)
            throw new AssertionError("NPC work pose failed state sync");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void npc_animation_priority_avoids_fake_gestures_during_fear_or_work(GameTestHelper helper) {
        if (NpcPoseSelection.classify(true, true, true, true, true, false)
                != NpcPose.AFRAID
            || NpcPoseSelection.classify(false, true, true, true, true, false)
                != NpcPose.GUARD
            || NpcPoseSelection.classify(false, false, true, true, true, true)
                != NpcPose.WORK
            || NpcPoseSelection.classify(false, false, false, true, true, true)
                != NpcPose.CARRY
            || NpcPoseSelection.classify(false, false, false, false, false, true)
                != NpcPose.REST
            || NpcPoseSelection.classify(false, false, false, false, true, false)
                != NpcPose.SOCIALIZE
            || NpcPoseSelection.classify(false, false, false, false, false, false)
                != NpcPose.IDLE)
            throw new AssertionError("Humanlike NPC visual pose action ordering regressed");
        helper.succeed();
    }

    /**
     * Architecture safety probe: a FakePlayer is a ServerPlayer subclass,
     * but the currently rendered resident is NOT a player. This test must
     * remain truthful until we replace the resident with a networked
     * ServerPlayer, rather than mark a proxy actor as full player parity.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void full_player_mod_parity_requires_real_networked_player_entity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC entity registry missing");
        BlockPos foot = helper.absolutePos(new BlockPos(4, 9, 4));
        npc.moveTo(foot.getX() + 0.5, foot.getY(), foot.getZ() + 0.5, 0, 0);
        if (!level.addFreshEntity(npc)) throw new AssertionError("Resident failed to spawn");
        var actor = NpcPlayerActor.forNpc(npc, level);
        if (!(actor instanceof net.minecraft.server.level.ServerPlayer))
            throw new AssertionError("NPC action bridge lost server player event identity");
        if (net.minecraft.world.entity.player.Player.class.isInstance(npc))
            throw new AssertionError("Architecture probe is obsolete; document real player migration");
        if (!actor.getUUID().equals(npc.getUUID()))
            throw new AssertionError("Bridge action player differs from NPC identity");
        if (level.getServer().getPlayerList().getPlayer(npc.getUUID()) != null)
            throw new AssertionError("Unsafe UUID collision: proxy and registered player coexist");
        if (level.getEntity(npc.getUUID()) != npc)
            throw new AssertionError("A fake player replaced the living NPC in world entity index");
        // A successful GameTest confirms the CURRENT compatibility gap:
        // the visual entity is a Mob, while only its momentary world-action
        // delegate passes ServerPlayer instanceof checks. It is not 1:1.
        helper.succeed();
    }

    /**
     * The target architecture's first isolated construction test.
     * This is NOT player-login/client-render validation. It proves that a
     * real ServerPlayer subclass can hold the actual vanilla hunger/inventory
     * and has a distinct safe identity before migrating a legacy living Mob.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void true_server_player_npc_prototype_has_vanilla_player_inventory_and_no_uuid_collision(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerNpcEntity oldMob = NpcRegistry.PLAYER_NPC.get().create(level);
        if (oldMob == null) throw new AssertionError("Legacy resident missing");
        oldMob.moveTo(helper.absolutePos(new BlockPos(4, 9, 4)).getCenter().x,
            helper.absolutePos(new BlockPos(4, 9, 4)).getY(),
            helper.absolutePos(new BlockPos(4, 9, 4)).getCenter().z, 0, 0);
        if (!level.addFreshEntity(oldMob))
            throw new AssertionError("Cannot create old world resident");
        RealNpcServerPlayer player =
            RealNpcServerPlayer.detachedForGameTest(level, oldMob.getUUID());
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)
            || !net.minecraft.world.entity.player.Player.class.isInstance(player))
            throw new AssertionError("Prototype does not really inherit vanilla Player");
        if (player.getUUID().equals(oldMob.getUUID())
            || !player.originalResidentId().equals(oldMob.getUUID())
            || level.getEntity(oldMob.getUUID()) != oldMob)
            throw new AssertionError("Prototype overwrote a live old NPC's UUID");
        player.getInventory().add(new ItemStack(Items.WHEAT, 5));
        player.getFoodData().setFoodLevel(14);
        if (player.getInventory().countItem(Items.WHEAT) != 5
            || oldMob.backpack().countItem(Items.WHEAT) != 0
            || player.getFoodData().getFoodLevel() != 14)
            throw new AssertionError("Vanilla real player inventory/hunger not independent");
        if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != null)
            throw new AssertionError("Detached prototype improperly registered as connected Player");
        // The final migration test will need to replace this last assertion:
        // successful real PlayerList registration, remote player rendering,
        // player events, persistence, teardown. Currently NOT implemented.
        player.getInventory().clearContent();
        helper.succeed();
    }

    /**
     * No fake crime flag: the actual raider takes two existing wheat from a
     * courier, first-hand witness reaches a nearby real guard, which can
     * detain ONLY a nearby NPC raider at a legitimate iron-bar holding spot.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void eyewitness_robbery_dispatches_guard_and_physically_arrests_real_npc_raider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(4, 8, 4));
        for (int dx = -1; dx <= 5; dx++) for (int dz = -1; dz <= 3; dz++) {
            BlockPos floor = ground.offset(dx, 0, dz);
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos victimPos = ground.above();
        BlockPos suspectPos = victimPos.east();
        BlockPos officerPos = victimPos.south();
        BlockPos jail = victimPos.east(4);
        level.setBlock(jail.east(), Blocks.IRON_BARS.defaultBlockState(), 3);
        if (!PublicSafety.holdingSpot(level, jail))
            throw new AssertionError("Actual solid-floor/iron-bar jail fixture failed");
        PlayerNpcEntity victim = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity suspect = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity officer = NpcRegistry.PLAYER_NPC.get().create(level);
        if (victim == null || suspect == null || officer == null)
            throw new AssertionError("No real NPC citizens");
        victim.moveTo(victimPos.getX() + 0.5, victimPos.getY(), victimPos.getZ() + 0.5, 0, 0);
        suspect.moveTo(suspectPos.getX() + 0.5, suspectPos.getY(), suspectPos.getZ() + 0.5, 0, 0);
        officer.moveTo(officerPos.getX() + 0.5, officerPos.getY(), officerPos.getZ() + 0.5, 0, 0);
        victim.setIdentity(SocietyRules.civilian("crime_village"));
        officer.setIdentity(SocietyRules.civilian("crime_village"));
        suspect.setIdentity(new SocietyRules.Identity(SocietyRules.Nation.OUTLANDS,
            SocietyRules.Faction.RAIDERS, "crime_village"));
        victim.setProfession(PlayerNpcEntity.Profession.COURIER);
        officer.setProfession(PlayerNpcEntity.Profession.GUARD);
        suspect.setProfession(PlayerNpcEntity.Profession.RESIDENT);
        victim.setMode(PlayerNpcEntity.Mode.STAY);
        officer.setMode(PlayerNpcEntity.Mode.LIVE);
        suspect.setMode(PlayerNpcEntity.Mode.LIVE);
        suspect.setHomePos(victimPos);
        officer.setHomePos(victimPos);
        officer.setPoliceJail(jail);
        victim.backpack().addItem(new ItemStack(Items.WHEAT, 5));
        if (!level.addFreshEntity(victim) || !level.addFreshEntity(suspect)
            || !level.addFreshEntity(officer))
            throw new AssertionError("Cannot spawn citizen, thief and guard");
        long now = level.getGameTime();
        if (!BanditActions.tick(suspect, level, now)
            || victim.backpack().countItem(Items.WHEAT) != 3
            || suspect.backpack().countItem(Items.WHEAT) != 2
            || !suspect.getUUID().equals(officer.policeSuspect()))
            throw new AssertionError("Actual stolen inventory did not trigger local police dispatch");
        long due = now + Math.floorMod(
            MovementPolicy.phase(officer.getId(), 10) - now, 10);
        if (!PublicSafety.tickGuard(officer, level, due)
            || !suspect.isDetained(due) || !jail.equals(suspect.detentionJail())
            || officer.policeSuspect() != null)
            throw new AssertionError("Guard could not complete genuine nonlethal close-range arrest");
        // Custody can NEVER create/destroy the stolen wheat; it is still
        // physically present in the real suspect backpack for later recovery.
        if (suspect.backpack().countItem(Items.WHEAT) != 2)
            throw new AssertionError("Arrest generated or confiscated virtual loot");
        CompoundTag saved = new CompoundTag();
        suspect.addAdditionalSaveData(saved);
        PlayerNpcEntity restored = NpcRegistry.PLAYER_NPC.get().create(level);
        if (restored == null) throw new AssertionError("Cannot reload detainee");
        restored.readAdditionalSaveData(saved);
        if (!restored.isDetained(due) || !jail.equals(restored.detentionJail())
            || restored.backpack().countItem(Items.WHEAT) != 2)
            throw new AssertionError("Sentence, jail or contraband lost during NBT reload");
        if (PublicSafety.tickPrisoner(suspect, level, due + PublicSafety.CUSTODY_TICKS + 1)
            || suspect.isDetained(due + PublicSafety.CUSTODY_TICKS + 1))
            throw new AssertionError("Finite custody failed to release NPC after expiry");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rumors_alone_and_unbuilt_jails_cannot_arrest_anyone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 9, 4));
        PlayerNpcEntity victim = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity suspect = NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity officer = NpcRegistry.PLAYER_NPC.get().create(level);
        if (victim == null || suspect == null || officer == null)
            throw new AssertionError("NPC registry missing");
        victim.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        suspect.moveTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        officer.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5, 0, 0);
        victim.setIdentity(SocietyRules.civilian("justice_guardrail"));
        officer.setIdentity(SocietyRules.civilian("justice_guardrail"));
        suspect.setIdentity(new SocietyRules.Identity(SocietyRules.Nation.OUTLANDS,
            SocietyRules.Faction.RAIDERS, "justice_guardrail"));
        officer.setProfession(PlayerNpcEntity.Profession.GUARD);
        officer.setMode(PlayerNpcEntity.Mode.LIVE);
        officer.setHomePos(pos);
        suspect.setHomePos(pos);
        if (!level.addFreshEntity(victim) || !level.addFreshEntity(suspect)
            || !level.addFreshEntity(officer)) throw new AssertionError("cannot add citizens");
        UUID fictitious = UUID.randomUUID();
        if (PublicSafety.reportTheft(victim, suspect, level, fictitious,
                level.getGameTime()) != 0 || officer.policeSuspect() != null)
            throw new AssertionError("Police arrested an unreported/fictional crime");
        // Even if the local guard receives a real thief allegation, without
        // a real authorized bars-adjacent holding position no arrest happens.
        officer.assignPoliceCase(suspect.getUUID(), level.getGameTime() + 400);
        officer.setPoliceJail(pos.east(3)); // ordinary air without iron bars
        long now = level.getGameTime();
        long due = now + Math.floorMod(
            MovementPolicy.phase(officer.getId(), 10) - now, 10);
        if (!PublicSafety.tickGuard(officer, level, due)
            || suspect.isDetained(due))
            throw new AssertionError("Guard detained suspect without an actual validated jail");
        if (level.getServer().getPlayerList().getPlayer(suspect.getUUID()) != null)
            throw new AssertionError("Criminal NPC incorrectly treated as logged-in human");
        helper.succeed();
    }

}
