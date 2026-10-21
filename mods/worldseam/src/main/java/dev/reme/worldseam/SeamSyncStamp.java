package dev.reme.worldseam;

/**
 * The graph revision alone is insufficient for client state synchronization.
 * Enabling/disabling a saved world does not necessarily edit its dimension
 * stack, so the ON/OFF bit is part of the authoritative protocol stamp.
 */
public record SeamSyncStamp(long stackRevision, boolean enabled) {}
