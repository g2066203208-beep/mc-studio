package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import java.util.List;
import dev.reme.mcstudio.npc.life.SocietyActions;
import dev.reme.mcstudio.npc.life.SupplyLogistics;
import dev.reme.mcstudio.npc.life.RoadNavigator;
import dev.reme.mcstudio.npc.life.LifeJourney;
import dev.reme.mcstudio.npc.life.SafeBuilder;
import dev.reme.mcstudio.npc.life.MovementPolicy;
import dev.reme.mcstudio.npc.life.DailyRoaming;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Server-only resident actions. Production uses real block loot and reserves seeds. */
public final class LivingWorldActions {
    private LivingWorldActions() {}

    public static void tick(PlayerNpcEntity npc, ServerLevel level) {
        if (npc.mode() != PlayerNpcEntity.Mode.LIVE) return;
        long tick = level.getGameTime();
        // Record only actual transit, never stationary NPC ticks. Bounded per-
        // dimension SavedData persists crowd footsteps; physical wear is
        // separately operator-authorized and defaults OFF.
        EmergentFootpaths.observe(npc, level, tick);
        if (MovementPolicy.due(tick, npc.getId(), 200)) {
            npc.changeNeeds(-2, npc.intent() == LivingPlanner.Intent.SLEEP ? 12 : -3,
                npc.intent() == LivingPlanner.Intent.SOCIALIZE ? 2 : -3);
        }
        if (MovementPolicy.due(tick, npc.getId(), 80)) {
            boolean exposed = level.canSeeSky(npc.blockPosition());
            boolean danger = npc.isAfraid(tick) ||
                (npc.profession() != PlayerNpcEntity.Profession.GUARD &&
                 !level.getEntitiesOfClass(Monster.class,
                    npc.getBoundingBox().inflate(10.0),
                    m -> m.isAlive() && npc.hasLineOfSight(m)).isEmpty());
            npc.setDecision(npc.planner().choose(new LivingPlanner.Context(
                level.getDayTime() + npc.dailyShiftTicks(), level.isRaining(), exposed, danger,
                npc.hunger(), npc.energy(),
                Math.max(0, npc.socialNeed() -
                    (npc.character().empathy() > 75 ? 15 : 0)),
                npc.profession() == PlayerNpcEntity.Profession.FARMER ||
                npc.profession() == PlayerNpcEntity.Profession.BAKER ||
                npc.profession() == PlayerNpcEntity.Profession.BUILDER)));
        }
        if (npc.profession() == PlayerNpcEntity.Profession.GUARD)
            CivicSocial.guardLaw(npc, level, tick);
        // NPCs can ask for essentials themselves. The reward is funded from
        // their existing emeralds, never created by the task system.
        if (tick % 600 == Math.floorMod(npc.getId(), 600) &&
            npc.hunger() < 35 &&
            npc.backpack().countItem(Items.BREAD) == 0 &&
            npc.backpack().countItem(Items.CARROT) == 0 &&
            npc.backpack().countItem(Items.POTATO) == 0 &&
            (npc.commission() == null ||
             npc.commission().state() == Commission.State.COMPLETED ||
             npc.commission().state() == Commission.State.CANCELLED)) {
            CivicContracts.post(npc, Commission.Goods.BREAD, 2,
                npc.character().empathy() >= 75 ? 3 : 2);
        }
        // A bounded, operator-authorized mining task is performed through the
        // same survival tool / block break pipeline as a player. Danger can
        // interrupt it; routine AI never creates an unapproved mining target.
        if (PlayerMiningActions.tick(npc, level)) return;
        // Follow the genuine item-entity drops from this NPC's authorized
        // break, rather than treating ground loot as virtual inventory.
        if (PlayerLootPickup.tick(npc, level)) return;
        if (SocietyActions.tick(npc, level, tick)) return;
        if (DailyRoaming.tick(npc, level, tick)) return;
        if (npc.profession() == PlayerNpcEntity.Profession.GUARD &&
            npc.getTarget() != null && npc.getTarget().isAlive() &&
            !npc.isAfraid(tick)) return;
        if (!MovementPolicy.due(tick, npc.getId(), 40)) return;
        switch (npc.intent()) {
            case ESCAPE -> escape(npc, level);
            case EAT -> eat(npc);
            case SLEEP -> {
                // Guards work rotating watches. Rest must not cancel a real
                // active patrol path every 40 ticks; tired guards still rest.
                if (npc.profession() == PlayerNpcEntity.Profession.GUARD &&
                    npc.energy() >= 35) {
                    if (npc.getNavigation().isDone())
                        npc.setAction("guard on watch; awaiting next patrol round");
                } else rest(npc);
            }
            case WORK -> work(npc, level, tick);
            case SOCIALIZE -> {
                if (npc.profession() == PlayerNpcEntity.Profession.GUARD &&
                    npc.energy() >= 35) {
                    if (npc.getNavigation().isDone())
                        npc.setAction("guard on duty; scanning for threats");
                } else socialize(npc, level, tick);
            }
            case SEEK_SHELTER -> returnHome(npc, "returning home to escape rain");
            case IDLE -> { /* standing or looking around is a valid idle action */ }
        }
    }

    private static void escape(PlayerNpcEntity npc, ServerLevel level) {
        List<Monster> threats = level.getEntitiesOfClass(Monster.class,
            npc.getBoundingBox().inflate(11.0), m -> m.isAlive() && npc.hasLineOfSight(m));
        if (threats.isEmpty() && npc.isAfraid(level.getGameTime())) {
            var attacker = npc.recentAggressor() == null ? null :
                level.getEntity(npc.recentAggressor());
            if (attacker != null && npc.distanceToSqr(attacker) < 256) {
                double dx = npc.getX() - attacker.getX();
                double dz = npc.getZ() - attacker.getZ();
                double length = Math.max(Math.sqrt(dx * dx + dz * dz), 0.01);
                npc.getNavigation().moveTo(npc.getX() + dx / length * 12.0,
                    npc.getY(), npc.getZ() + dz / length * 12.0, 1.25);
                npc.setAction("escaping a witnessed attacker or caravan robber");
            }
            return;
        }
        if (threats.isEmpty()) return;
        Monster threat = threats.stream() .min(Comparator.comparingDouble(other ->
                npc.distanceToSqr(other) -
                0.25 * npc.memory().trust(other.getUUID()))).orElseThrow();
        double dx = npc.getX() - threat.getX(), dz = npc.getZ() - threat.getZ();
        double length = Math.max(Math.sqrt(dx * dx + dz * dz), 0.01);
        npc.setTarget(null);
        npc.getNavigation().moveTo(npc.getX() + dx / length * 10.0,
            npc.getY(), npc.getZ() + dz / length * 10.0, 1.3);
        npc.setAction("fleeing from a hostile creature");
    }

    private static void eat(PlayerNpcEntity npc) {
        SimpleContainer bag = npc.backpack();
        for (int i = 0; i < bag.getContainerSize(); i++) {
            ItemStack stack = bag.getItem(i);
            if (stack.isEmpty() || !isFood(stack.getItem())) continue;
            stack.shrink(1);
            bag.setChanged();
            npc.changeNeeds(32, 0, 0);
            npc.setAction("ate an item from own inventory");
            return;
        }
        npc.setAction("hungry: needs food in inventory");
    }

    private static boolean isFood(Item item) {
        return item == Items.BREAD || item == Items.APPLE || item == Items.CARROT ||
            item == Items.POTATO || item == Items.BAKED_POTATO ||
            item == Items.BEETROOT || item == Items.COOKED_BEEF ||
            item == Items.COOKED_PORKCHOP;
    }

    private static void rest(PlayerNpcEntity npc) {
        BlockPos home = npc.homePos();
        if (npc.blockPosition().distSqr(home) > 6) {
            returnHome(npc, "going home to rest");
            return;
        }
        npc.getNavigation().stop();
        npc.changeNeeds(0, 1, 0);
        npc.setAction("resting at home (no bed animation yet)");
    }

    private static void returnHome(PlayerNpcEntity npc, String reason) {
        BlockPos home = npc.homePos();
        if (npc.level() instanceof ServerLevel world)
            RoadNavigator.moveToward(npc, world, home, 1.0);
        npc.setAction(reason);
    }

    private static void work(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if (npc.profession() == PlayerNpcEntity.Profession.BUILDER) {
            SafeBuilder.tick(npc, level, tick);
            return;
        }
        if (npc.profession() == PlayerNpcEntity.Profession.BAKER) {
            bake(npc, level, tick);
            return;
        }
        if (npc.profession() != PlayerNpcEntity.Profession.FARMER) return;
        if (!MovementPolicy.due(tick, npc.getId(), 200)) return;
        if (npc.supplyPos() != null && npc.backpack().countItem(Items.WHEAT) >= 12
            && SupplyLogistics.deposit(npc, level)) return;
        BlockPos anchor = npc.workPos();
        if (npc.blockPosition().distSqr(anchor) > 196) {
            RoadNavigator.moveToward(npc, level, anchor, 1.0);
            npc.setAction("walking to assigned farm");
            return;
        }

        BlockPos candidate = npc.cachedCropTarget();
        if (candidate != null && (!level.hasChunkAt(candidate) ||
            !(level.getBlockState(candidate).getBlock() instanceof CropBlock crop) ||
            !crop.isMaxAge(level.getBlockState(candidate))))
            candidate = null;
        double best = candidate == null ? Double.MAX_VALUE :
            npc.blockPosition().distSqr(candidate);
        if (candidate == null)
        for (BlockPos p : BlockPos.betweenClosed(anchor.offset(-6, -2, -6),
                anchor.offset(6, 2, 6))) {
            if (!level.hasChunkAt(p)) continue;
            BlockState state = level.getBlockState(p);
            if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) continue;
            if (seedFor(state.getBlock()) == null) continue;
            double distance = npc.blockPosition().distSqr(p);
            if (distance < best) {
                best = distance;
                candidate = p.immutable();
            }
        }
        npc.setCachedCropTarget(candidate);
        if (candidate == null) {
            npc.setAction("waiting for real crops to ripen");
            return;
        }
        if (npc.blockPosition().distSqr(candidate) > 5) {
            RoadNavigator.moveToward(npc, level, candidate, 0.95);
            npc.setAction("walking to ripe crop " + candidate.toShortString());
            return;
        }
        harvest(npc, level, candidate);
        npc.setCachedCropTarget(null);
    }

    private static Item seedFor(Block crop) {
        if (crop == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (crop == Blocks.CARROTS) return Items.CARROT;
        if (crop == Blocks.POTATOES) return Items.POTATO;
        if (crop == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        return null;
    }

    private static void harvest(PlayerNpcEntity npc, ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return;
        Item seed = seedFor(state.getBlock());
        if (seed == null) return;

        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
        SimpleContainer bag = npc.backpack();
        SimpleContainer trial = new SimpleContainer(36);
        for (int i = 0; i < 36; i++) trial.setItem(i, bag.getItem(i).copy());
        for (ItemStack drop : drops) {
            if (!trial.addItem(drop.copy()).isEmpty()) {
                npc.setAction("harvest delayed: backpack full");
                return;
            }
        }
        boolean paid = false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = trial.getItem(i);
            if (stack.is(seed) && stack.getCount() > 0) {
                stack.shrink(1);
                paid = true;
                break;
            }
        }
        if (!paid) {
            npc.setAction("harvest delayed: no real replacement seed");
            return;
        }
        if (level.getBlockState(pos) != state) return;
        if (!level.setBlock(pos, crop.defaultBlockState(), 3)) return;
        for (int i = 0; i < 36; i++) bag.setItem(i, trial.getItem(i).copy());
        bag.setChanged();
        npc.changeNeeds(0, -2, 0);
        npc.setAction("harvested and replanted crop " + pos.toShortString());
        npc.journey().succeeded(LifeJourney.Achievement.HARVEST);
    }


    // The baker must work near a real crafting table; the recipe consumes real wheat.
    private static void bake(PlayerNpcEntity baker, ServerLevel level, long tick) {
        if (tick % 100 != Math.floorMod(baker.getId(), 100)) return;
        BlockPos anchor = baker.workPos();
        if (baker.blockPosition().distSqr(anchor) > 36) {
            RoadNavigator.moveToward(baker, level, anchor, 1.0);
            baker.setAction("walking to bakery");
            return;
        }

        boolean bench = false;
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-3, -1, -3),
                anchor.offset(3, 2, 3))) {
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                bench = true;
                break;
            }
        }
        if (!bench) {
            baker.setAction("bakery lacks a real crafting table");
            return;
        }
        SimpleContainer bag = baker.backpack();
        SimpleContainer trial = cloneBag(bag);
        if (remove(trial, Items.WHEAT, 3)) {
            if (trial.addItem(new ItemStack(Items.BREAD)).isEmpty()) {
                apply(bag, trial);
                baker.setAction("baked 1 bread using 3 real wheat at workbench");
                return;
            }
        }

        // Only a designated vanilla supply chest may be used, never arbitrary chests.
        if (SupplyLogistics.withdraw(baker, level, Items.WHEAT, 3)) return;
        // If no wheat, visit another loaded farmer and barter 1 real bread for 3 wheat.
        List<PlayerNpcEntity> farmers = level.getEntitiesOfClass(PlayerNpcEntity.class,
            baker.getBoundingBox().inflate(14), person ->
                person != baker && person.isAlive()
                    && person.profession() == PlayerNpcEntity.Profession.FARMER
                    && !baker.hostileTo(person)
                    && person.identity().region().equals(baker.identity().region()));
        for (PlayerNpcEntity farmer : farmers) {
            if (baker.distanceToSqr(farmer) > 8) {
                RoadNavigator.moveToward(baker, level, farmer.blockPosition(), 0.95);
                baker.setAction("visiting farmer to buy wheat");
                return;
            }
            SimpleContainer farmerTrial = cloneBag(farmer.backpack());
            SimpleContainer bakerTrial = cloneBag(bag);
            if (!remove(farmerTrial, Items.WHEAT, 3)
                || !remove(bakerTrial, Items.BREAD, 1)) continue;
            if (!farmerTrial.addItem(new ItemStack(Items.BREAD)).isEmpty()
                || !bakerTrial.addItem(new ItemStack(Items.WHEAT, 3)).isEmpty()) continue;
            apply(farmer.backpack(), farmerTrial);
            apply(bag, bakerTrial);
            baker.rememberGreeting(farmer, tick);
            farmer.rememberGreeting(baker, tick);
            baker.setAction("bartered 1 bread for 3 wheat with " + farmer.getName().getString());
            farmer.setAction("sold 3 wheat to " + baker.getName().getString());
            baker.journey().succeeded(LifeJourney.Achievement.BARTER);
            return;
        }
        baker.setAction("waiting for wheat and bread to trade");
    }

    private static SimpleContainer cloneBag(SimpleContainer source) {
        SimpleContainer result = new SimpleContainer(source.getContainerSize());
        for (int i = 0; i < source.getContainerSize(); i++)
            result.setItem(i, source.getItem(i).copy());
        return result;
    }

    private static void apply(SimpleContainer target, SimpleContainer source) {
        for (int i = 0; i < target.getContainerSize(); i++)
            target.setItem(i, source.getItem(i).copy());
        target.setChanged();
    }

    private static boolean remove(SimpleContainer bag, Item type, int count) {
        int available = 0;
        for (int i = 0; i < bag.getContainerSize(); i++)
            if (bag.getItem(i).is(type)) available += bag.getItem(i).getCount();
        if (available < count) return false;
        for (int i = 0; i < bag.getContainerSize() && count > 0; i++) {
            ItemStack stack = bag.getItem(i);
            if (!stack.is(type)) continue;
            int taken = Math.min(stack.getCount(), count);
            stack.shrink(taken);
            count -= taken;
        }
        return true;
    }

    private static void socialize(PlayerNpcEntity npc, ServerLevel level, long tick) {
        List<PlayerNpcEntity> neighbors = level.getEntitiesOfClass(PlayerNpcEntity.class,
            npc.getBoundingBox().inflate(12),
            other -> other != npc && other.isAlive()
                && other.mode() == PlayerNpcEntity.Mode.LIVE
                && !npc.hostileTo(other) &&
                npc.memory().trust(other.getUUID()) > -20);
        if (neighbors.isEmpty()) {
            // Bounded daily roaming handles searching for company in daytime.
            npc.setAction("looking around the neighborhood for company");
            return;
        }
        PlayerNpcEntity other = neighbors.stream()
            .filter(person -> npc.escortLeader() != null ||
                DailyRoaming.inRange(npc, person.blockPosition()))
            .min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
        if (other == null) return;
        if (npc.distanceToSqr(other) > 8) {
            RoadNavigator.moveToward(npc, level, other.blockPosition(), 0.90);
            npc.setAction("going to meet " + other.getName().getString());
            return;
        }
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(other, 20.0f, npc.getMaxHeadXRot());
        if (tick < npc.nextSocialTick() || tick < other.nextSocialTick()) return;
        npc.rememberGreeting(other, tick);
        other.rememberGreeting(npc, tick);
        int exchangedReports = CivicSocial.exchangeNews(npc, other, tick);
        npc.setNextSocialTick(tick + 400);
        other.setNextSocialTick(tick + 400);
        npc.changeNeeds(0, 0, 14);
        other.changeNeeds(0, 0, 14);
        npc.setAction("talked with " + other.getName().getString() +
            (exchangedReports > 0 ? " and exchanged " + exchangedReports + " witnessed reports" : ""));
        other.setAction("talked with " + npc.getName().getString() +
            (exchangedReports > 0 ? " about recent events" : ""));
        npc.journey().succeeded(LifeJourney.Achievement.SOCIALIZE);
        other.journey().succeeded(LifeJourney.Achievement.SOCIALIZE);
    }
}
