package dev.reme.npcstudio.npc.life;

import dev.reme.npcstudio.npc.NpcRegistry;
import dev.reme.npcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/** Natural, vanilla mob-spawner-driven residents in a few surface biomes.
 * Caps are evaluated only on potential NPC spawns; no scanning every tick.
 */
public final class NaturalResidentSpawn {
    public static final int MAX_WITHIN_72_BLOCKS = 4;
    private NaturalResidentSpawn() {}

    public static boolean allowedNaturalReason(MobSpawnType reason) {
        // Passive mobs are populated both at runtime and while chunks generate.
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    public static boolean atCapacity(ServerLevel level, BlockPos position) {
        return level.getEntitiesOfClass(PlayerNpcEntity.class,
            new AABB(position).inflate(72.0),
            PlayerNpcEntity::isAlive).size() >= MAX_WITHIN_72_BLOCKS;
    }

    public static void register(RegisterSpawnPlacementsEvent event) {
        event.register(NpcRegistry.PLAYER_NPC.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, world, reason, position, random) -> {
                if (!allowedNaturalReason(reason)) return false;
                ServerLevel level = world.getLevel();
                if (level.dimension() != Level.OVERWORLD) return false;
                if (position.getY() <= level.getSeaLevel() - 4 ||
                    position.getY() >= level.getMaxBuildHeight() - 3) return false;
                if ((reason == MobSpawnType.NATURAL && !level.hasChunkAt(position)) ||
                    !world.canSeeSky(position) ||
                    !world.getBlockState(position).isAir() ||
                    !world.getBlockState(position.above()).isAir()) return false;
                var soil = world.getBlockState(position.below());
                if (!(soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT_PATH) ||
                    soil.is(Blocks.PODZOL) || soil.is(Blocks.COARSE_DIRT))) return false;
                // Apply the same local safeguard to both spawn paths; this only
                // reads already loaded entities and never requests distant chunks.
                if (atCapacity(level, position)) return false;
                return true;
            },
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }
}
