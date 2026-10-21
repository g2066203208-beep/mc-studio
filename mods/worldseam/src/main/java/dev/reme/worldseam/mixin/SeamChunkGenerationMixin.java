package dev.reme.worldseam.mixin;

import dev.reme.worldseam.SeamTerrain;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Exact Minecraft 1.21.1 NeoForge generation stage used after vanilla
 * features; avoids applying bedrock changes to ordinary loaded old chunks.
 */
@Mixin(ChunkStatusTasks.class)
public class SeamChunkGenerationMixin {
    @Inject(method = "generateSpawn", at = @At("HEAD"))
    private static void worldseam$openFreshGenerationEdges(
        WorldGenContext context, ChunkStep step,
        StaticCache2D<GenerationChunkHolder> cache,
        ChunkAccess chunk,
        CallbackInfoReturnable<CompletableFuture<ChunkAccess>> callback
    ) {
        SeamTerrain.replaceGeneratedBedrock(context.level(), chunk);
    }
}
