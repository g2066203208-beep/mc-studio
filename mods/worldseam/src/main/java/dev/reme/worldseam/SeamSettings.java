package dev.reme.worldseam;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-specific opt-in. Stored only in the Overworld's SavedData. */
public final class SeamSettings extends SavedData {
    private boolean enabled;
    private boolean carveNewChunks;

    private SeamSettings() {
        enabled = false;
    }

    public static SeamSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(SeamSettings::new, SeamSettings::load, null),
            "worldseam_settings"
        );
    }

    private static SeamSettings load(CompoundTag tag, HolderLookup.Provider registries) {
        SeamSettings result = new SeamSettings();
        result.enabled = tag.getBoolean("enabled");
        result.carveNewChunks = tag.getBoolean("carveNewChunks");
        return result;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean carveNewChunks() { return carveNewChunks; }

    public void setCarveNewChunks(boolean next) {
        if (carveNewChunks != next) {
            carveNewChunks = next;
            setDirty();
        }
    }

    public void setEnabled(boolean next) {
        if (enabled != next) {
            enabled = next;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("carveNewChunks", carveNewChunks);
        return tag;
    }
}
