package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Physical, collision-aware route into the existing demo police station.
 * From outside: reach the EAST street-facing public door, cross the lobby,
 * walk to the cell vestibule, cross an actually OPEN iron gate, then enter
 * the cell. No teleport, no forced chunks, no world edits.
 *
 * This plan is enabled ONLY for the precisely detected real demonstrator
 * station with its native oak front door and authenticated cell iron gate.
 * Custom-world jails use ordinary vanilla navigation as before.
 */
public final class CityJailRoute {
    private CityJailRoute() {}

    public static BlockPos nextPhysicalWaypoint(PlayerNpcEntity prisoner,
                                                  ServerLevel level,
                                                  BlockPos holding) {
        if (!CityJailGate.isGate(level, holding))
            return holding;
        BlockPos frontDoor = holding.east(7).north(3);
        if (!level.hasChunkAt(frontDoor)
            || !level.getBlockState(frontDoor).is(Blocks.OAK_DOOR))
            return holding;

        // Station: east entrance on x=holding.x+7, north wall z=-9,
        // south wall z=+3, west wall x=-5, all relative to the holding cell.
        BlockPos p=prisoner.blockPosition();
        boolean inside = p.getX() < holding.getX()+7
            && p.getX() > holding.getX()-5
            && p.getZ() > holding.getZ()-9
            && p.getZ() < holding.getZ()+3;

        // From outside its SOUTH wall (the reproduced bug), trying to path
        // directly to the cell sends the NPC into solid bricks. Route around
        // the building first to the actual external public doorway.
        BlockPos streetEntrance=holding.east(8).north(3);
        BlockPos lobby=holding.east(4).north(3);
        BlockPos vestibule=holding.east(3).south(1);
        BlockPos insideCell=holding.south();

        if (!inside) {
            if (prisoner.distanceToSqr(Vec3.atCenterOf(streetEntrance)) > 2.6
                && p.getX() >= holding.getX()+7)
                return streetEntrance;
            // South/west exterior: walk to street-facing door first, NEVER
            // snap directly through the solid police-station south wall.
            if (p.getZ() >= holding.getZ()+3
                || p.getX() <= holding.getX()-5) return streetEntrance;
            return lobby;
        }

        // The lobby and its corridor are unobstructed native Minecraft air,
        // and the cell gate is the only valid path through the iron bars.
        if (p.getZ() < holding.getZ() || p.getX() > holding.getX()+3)
            return vestibule;
        // Once ACTUALLY near the inner vestibule, move through the open
        // cell gate. Without this distance check the prisoner loops forever
        // re-pathing toward the vestibule point it already occupies.
        if (prisoner.distanceToSqr(Vec3.atCenterOf(vestibule)) > 2.5
            && p.getX() >= holding.getX()+2)
            return vestibule;
        if (p.getX() >= holding.getX()+1)
            return insideCell;
        return holding;
    }

    /** No 'goal reached' unless the ENTITY (not only its navigation node)
     * is actually inside the real iron-bar cell at the real target.
     */
    public static boolean actuallyInCell(PlayerNpcEntity prisoner, BlockPos holding) {
        return prisoner.distanceToSqr(Vec3.atCenterOf(holding)) <= 2.5
            && prisoner.getX() < holding.getX()+1.0
            && prisoner.getZ() >= holding.getZ()-0.2
            && prisoner.getZ() < holding.getZ()+2.0;
    }
}
