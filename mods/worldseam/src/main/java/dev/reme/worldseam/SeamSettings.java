package dev.reme.worldseam;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-specific opt-in. Stored only in the Overworld's SavedData. */
public final class SeamSettings extends SavedData {
    private boolean enabled;
    private boolean carveNewChunks;
    private boolean experimentalNoRespawn;
    private SeamStack stack = SeamStack.defaults();
    private boolean stackChosenByUser;
    private long stackRevision = 1;

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
        result.experimentalNoRespawn = tag.getBoolean("experimentalNoRespawn");
        if (tag.contains("stack",net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            try {
                result.stack = SeamStack.read(tag.getCompound("stack"));
                result.stackChosenByUser = true;
            } catch (IllegalArgumentException ex) {
                com.mojang.logging.LogUtils.getLogger().error(
                    "Invalid WorldSeam save stack; leaving feature OFF",ex);
                result.enabled=false;
            }
        }
        return result;
    }

    public SeamStack stack() { return stack; }
    public long stackRevision() { return stackRevision; }
    public boolean stackChosenByUser() { return stackChosenByUser; }

    public void setStack(SeamStack next, MinecraftServer server) {
        next.validate(server);
        if (!stack.equals(next) || !stackChosenByUser) {
            stack=next;
            stackChosenByUser=true;
            stackRevision++;
            setDirty();
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean carveNewChunks() { return carveNewChunks; }
    public boolean experimentalNoRespawn() { return experimentalNoRespawn; }

    public void setExperimentalNoRespawn(boolean v) {
        if (experimentalNoRespawn != v) { experimentalNoRespawn = v; setDirty(); }
    }

    public void setCarveNewChunks(boolean next) {
        if (carveNewChunks != next) {
            carveNewChunks = next;
            setDirty();
        }
    }

    public void setEnabled(boolean next) {
        // Enabling this seamless-world mod must not silently use the
        // vanilla respawn/loading-screen path. Smooth mode can still
        // be disabled explicitly for debugging.
        if (next && !experimentalNoRespawn) {
            experimentalNoRespawn = true;
            setDirty();
        }
        if (enabled != next) {
            enabled = next;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("enabled", enabled);
        tag.putBoolean("carveNewChunks", carveNewChunks);
        tag.putBoolean("experimentalNoRespawn", experimentalNoRespawn);
        tag.put("stack",stack.save());
        return tag;
    }
}
