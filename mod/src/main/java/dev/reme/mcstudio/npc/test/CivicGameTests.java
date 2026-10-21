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

}
