package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import java.util.List;
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
        if (tick % 200 == Math.floorMod(npc.getId(), 200)) {
            npc.changeNeeds(-2, npc.intent() == LivingPlanner.Intent.SLEEP ? 12 : -3,
                npc.intent() == LivingPlanner.Intent.SOCIALIZE ? 2 : -3);
        }
        if (tick % 40 == Math.floorMod(npc.getId(), 40)) {
            boolean exposed = level.canSeeSky(npc.blockPosition());
            boolean danger = npc.isAfraid(tick) || !level.getEntitiesOfClass(Monster.class,
                    npc.getBoundingBox().inflate(10.0),
                    m -> m.isAlive() && npc.hasLineOfSight(m)).isEmpty();
            npc.setDecision(npc.planner().choose(new LivingPlanner.Context(
                level.getDayTime(), level.isRaining(), exposed, danger,
                npc.hunger(), npc.energy(), npc.socialNeed(),
                npc.profession() == PlayerNpcEntity.Profession.FARMER)));
        }
        if (tick % 20 != Math.floorMod(npc.getId(), 20)) return;
        switch (npc.intent()) {
            case ESCAPE -> escape(npc, level);
            case EAT -> eat(npc);
            case SLEEP -> rest(npc);
            case WORK -> work(npc, level, tick);
            case SOCIALIZE -> socialize(npc, level, tick);
            case SEEK_SHELTER -> returnHome(npc, "returning home to escape rain");
            case IDLE -> { /* standing or looking around is a valid idle action */ }
        }
    }

    private static void escape(PlayerNpcEntity npc, ServerLevel level) {
        List<Monster> threats = level.getEntitiesOfClass(Monster.class,
            npc.getBoundingBox().inflate(11.0), m -> m.isAlive() && npc.hasLineOfSight(m));
        if (threats.isEmpty() && npc.isAfraid(level.getGameTime())) {
            var attacker = level.getPlayerByUUID(npc.recentAggressor());
            if (attacker != null && npc.distanceToSqr(attacker) < 256) {
                double dx = npc.getX() - attacker.getX();
                double dz = npc.getZ() - attacker.getZ();
                double length = Math.max(Math.sqrt(dx * dx + dz * dz), 0.01);
                npc.getNavigation().moveTo(npc.getX() + dx / length * 12.0,
                    npc.getY(), npc.getZ() + dz / length * 12.0, 1.25);
                npc.setAction("escaping an attacking player");
            }
            return;
        }
        if (threats.isEmpty()) return;
        Monster threat = threats.stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElseThrow();
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
        npc.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
        npc.setAction(reason);
    }

    private static void work(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if (npc.profession() != PlayerNpcEntity.Profession.FARMER) return;
        if (tick % 100 != Math.floorMod(npc.getId(), 100)) return;
        BlockPos anchor = npc.workPos();
        if (npc.blockPosition().distSqr(anchor) > 196) {
            npc.getNavigation().moveTo(anchor.getX() + 0.5,
                anchor.getY(), anchor.getZ() + 0.5, 1.0);
            npc.setAction("walking to assigned farm");
            return;
        }

        BlockPos candidate = null;
        double best = Double.MAX_VALUE;
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
        if (candidate == null) {
            npc.setAction("waiting for real crops to ripen");
            return;
        }
        if (npc.blockPosition().distSqr(candidate) > 5) {
            npc.getNavigation().moveTo(candidate.getX() + 0.5,
                candidate.getY(), candidate.getZ() + 0.5, 0.95);
            npc.setAction("walking to ripe crop " + candidate.toShortString());
            return;
        }
        harvest(npc, level, candidate);
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
    }

    private static void socialize(PlayerNpcEntity npc, ServerLevel level, long tick) {
        List<PlayerNpcEntity> neighbors = level.getEntitiesOfClass(PlayerNpcEntity.class,
            npc.getBoundingBox().inflate(12),
            other -> other != npc && other.isAlive() && other.mode() == PlayerNpcEntity.Mode.LIVE);
        if (neighbors.isEmpty()) {
            returnHome(npc, "looking for company");
            return;
        }
        PlayerNpcEntity other = neighbors.stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElseThrow();
        if (npc.distanceToSqr(other) > 8) {
            npc.getNavigation().moveTo(other, 0.90);
            npc.setAction("going to meet " + other.getName().getString());
            return;
        }
        npc.getNavigation().stop();
        npc.getLookControl().setLookAt(other, 20.0f, npc.getMaxHeadXRot());
        if (tick < npc.nextSocialTick() || tick < other.nextSocialTick()) return;
        npc.rememberGreeting(other, tick);
        other.rememberGreeting(npc, tick);
        npc.setNextSocialTick(tick + 400);
        other.setNextSocialTick(tick + 400);
        npc.changeNeeds(0, 0, 14);
        other.changeNeeds(0, 0, 14);
        npc.setAction("talked with " + other.getName().getString());
        other.setAction("talked with " + npc.getName().getString());
    }
}
