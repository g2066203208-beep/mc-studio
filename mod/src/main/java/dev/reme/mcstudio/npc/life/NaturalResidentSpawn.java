package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
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

    public static void register(RegisterSpawnPlacementsEvent event) {
        event.register(NpcRegistry.PLAYER_NPC.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            (type, world, reason, position, random) -> {
                if (reason != MobSpawnType.NATURAL) return false;
                ServerLevel level = world.getLevel();
                if (level.dimension() != Level.OVERWORLD) return false;
                if (position.getY() <= level.getSeaLevel() - 4 ||
                    position.getY() >= level.getMaxBuildHeight() - 3) return false;
                if (!level.hasChunkAt(position) || !level.canSeeSky(position) ||
                    !world.getBlockState(position).isAir() ||
                    !world.getBlockState(position.above()).isAir()) return false;
                var soil = world.getBlockState(position.below());
                if (!(soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT_PATH) ||
                    soil.is(Blocks.PODZOL) || soil.is(Blocks.COARSE_DIRT))) return false;
                if (level.getEntitiesOfClass(PlayerNpcEntity.class,
                    new AABB(position).inflate(72.0),
                    PlayerNpcEntity::isAlive).size() >= MAX_WITHIN_72_BLOCKS)
                    return false;
                return true;
            },
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }
}
