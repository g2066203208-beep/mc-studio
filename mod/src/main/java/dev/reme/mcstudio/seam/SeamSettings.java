package dev.reme.mcstudio.seam;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-specific opt-in. Stored only in the Overworld's SavedData. */
public final class SeamSettings extends SavedData {
    private boolean enabled;

    private SeamSettings() {
        enabled = false;
    }

    public static SeamSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(SeamSettings::new, SeamSettings::load, null),
            "reme_native_seam"
        );
    }

    private static SeamSettings load(CompoundTag tag, HolderLookup.Provider registries) {
        SeamSettings result = new SeamSettings();
        result.enabled = tag.getBoolean("enabled");
        return result;
    }

    public boolean enabled() {
        return enabled;
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
        return tag;
    }
}
