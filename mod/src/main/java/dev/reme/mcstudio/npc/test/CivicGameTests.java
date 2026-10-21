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
import dev.reme.mcstudio.npc.life.PlayerActionBridge;
import dev.reme.mcstudio.npc.life.PlayerMiningActions;
import dev.reme.mcstudio.npc.life.PlayerLootPickup;
import dev.reme.mcstudio.npc.life.NpcPlayerActor;
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
        level.getServer().getCommands().performPrefixedCommand(
            player.createCommandSourceStack().withPermission(4), "npc demo");
        int found = level.getEntitiesOfClass(PlayerNpcEntity.class,
            new net.minecraft.world.phys.AABB(origin).inflate(16),
            npc -> npc.isAlive() && npc.getCustomName() != null).size();
        if (found < 6) throw new AssertionError(
            "NPC demo claims creation without six actual named server entities");
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
        npc.claimMiningDrops(java.util.Set.of(mined.getUUID()), level.getGameTime() + 80);
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
        npc.claimMiningDrops(java.util.Set.of(id), deadline);
        CompoundTag tag = new CompoundTag();
        npc.addAdditionalSaveData(tag);
        PlayerNpcEntity restored = NpcRegistry.PLAYER_NPC.get().create(level);
        if (restored == null) throw new AssertionError("cannot restore resident");
        restored.readAdditionalSaveData(tag);
        if (!restored.pendingMiningDrops().contains(id)
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

}
