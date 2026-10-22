package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded, nonlethal NPC-on-NPC community justice slice:
 * real robbery -> victim eyewitness complaint -> nearby guard responds ->
 * guard physically reaches the suspect -> limited custody at a pre-authorized
 * physical holding spot alongside existing iron bars -> timed release.
 *
 * Never captures real human players, accuses arbitrary NPCs from rumors,
 * invents a prison, force-loads chunks or teleports suspects into cells.
 * Does not confiscate inventory; restitution/courts are separate future work.
 */
public final class PublicSafety {
    private PublicSafety() {}
    public static final int REPORT_RADIUS = 16;
    public static final int INVESTIGATION_TICKS = 400;
    public static final int CUSTODY_TICKS = 1200;

    public static boolean holdingSpot(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
            || !level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())
            || !level.getBlockState(pos).isAir()
            || !level.getBlockState(pos.above()).isAir()
            || !level.getBlockState(pos.below()).isSolid()) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos bar = pos.relative(side);
            if (level.hasChunkAt(bar) && level.getBlockState(bar).is(Blocks.IRON_BARS))
                return true;
        }
        return false;
    }

    /** A guard can be assigned ONLY a currently walkable spot beside iron bars,
     * selected by a server operator. No auto-generated or virtual prison.
     */
    public static BlockPos nearestHoldingSpot(ServerLevel level, BlockPos bars,
                                                PlayerNpcEntity guard) {
        if (!level.hasChunkAt(bars) || !level.getBlockState(bars).is(Blocks.IRON_BARS))
            return null;
        BlockPos best = null;
        double score = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = bars.relative(dir);
            if (!holdingSpot(level, candidate)
                || !DailyRoaming.inRange(guard, candidate)) continue;
            double d = guard.distanceToSqr(Vec3.atCenterOf(candidate));
            if (d < score) { score = d; best = candidate.immutable(); }
        }
        return best;
    }

    /** An actual NPC robbery provides a unique, FIRSTHAND crime report.
     * Only guards physically close to the robbed resident can receive it.
     * The report is NOT a server-wide crime list.
     */
    public static int reportTheft(PlayerNpcEntity victim, PlayerNpcEntity suspect,
                                  ServerLevel level, UUID incident, long tick) {
        if (!level.getServer().isSameThread() || !victim.isAlive()
            || !suspect.isAlive() || victim == suspect
            || !(suspect.identity().faction() == SocietyRules.Faction.RAIDERS
                 && suspect.hostileTo(victim)
                 || suspect.unpaidFrom(victim.getUUID(), tick)))
            return 0;
        var firstHand = victim.civic().reports().stream()
            .filter(report -> report.id().equals(incident)
                && report.direct() && report.actor().equals(suspect.getUUID())
                && report.kind() == CivicMind.Kind.THEFT)
            .findFirst().orElse(null);
        if (firstHand == null) return 0;
        int dispatched = 0;
        for (PlayerNpcEntity guard : level.getEntitiesOfClass(
                PlayerNpcEntity.class, victim.getBoundingBox().inflate(REPORT_RADIUS),
                candidate -> candidate != victim && candidate != suspect
                    && candidate.isAlive()
                    && candidate.mode() == PlayerNpcEntity.Mode.LIVE
                    && candidate.profession() == PlayerNpcEntity.Profession.GUARD
                    && candidate.character().justice() >= 45
                    && candidate.identity().region().equals(victim.identity().region())
                    && !candidate.hostileTo(victim)
                    && candidate.hasLineOfSight(victim))) {
            if (!guard.civic().hear(firstHand, tick, true, true)) continue;
            if (guard.policeSuspect() == null || tick >= guard.policeAlertUntil()) {
                guard.assignPoliceCase(suspect.getUUID(), tick + INVESTIGATION_TICKS);
                guard.setAction("received firsthand robbery complaint; investigating a nearby raider");
                dispatched++;
            }
        }
        // The village bell is a real player-built bell, not a global police
        // siren. Ring only after a verified firsthand incident was actually
        // delivered to at least one local guard.
        if (dispatched > 0) {
            boolean bell = TownEvents.ringAlarm(level, victim.blockPosition());
            System.out.println("REME_NPC_STORY stage=guard_dispatch tick=" + tick
                + " victim=" + victim.getUUID() + " suspect=" + suspect.getUUID()
                + " guards=" + dispatched + " bell=" + bell);
        }
        return dispatched;
    }

    /** Returns true if guard investigation owns its movement this tick. */
    public static boolean tickGuard(PlayerNpcEntity guard, ServerLevel level, long tick) {
        if (guard.profession() != PlayerNpcEntity.Profession.GUARD
            || guard.mode() != PlayerNpcEntity.Mode.LIVE) return false;
        // Do not abandon a prisoner after the handcuff event. Escort goal
        // owns on-foot navigation until the REAL holding spot is reached.
        if (guard.escortedPrisoner() != null)
            return tickEscortGuard(guard, level, tick);
        if (guard.policeSuspect() == null) return false;
        if (tick >= guard.policeAlertUntil()) {
            guard.clearPoliceCase();
            return false;
        }
        // A previous vanilla guard melee goal must NEVER attack a person
        // while a nonlethal arrest mission is active, even between scans.
        guard.setTarget(null);
        // Not even a navigation attempt unless a human OP designated a
        // physical cell with walkable floor beside real iron bars.
        BlockPos jail = guard.policeJail();
        if (jail == null || !holdingSpot(level, jail)) {
            guard.setAction("crime reported but custody unavailable: no valid iron-bar holding cell");
            return true;
        }
        if (!MovementPolicy.due(tick, guard.getId(), 10)) return true;
        var offender = level.getEntity(guard.policeSuspect());
        if (!(offender instanceof PlayerNpcEntity suspect)
            || !suspect.isAlive() || suspect.isDetained(tick)
            || !(suspect.identity().faction() == SocietyRules.Faction.RAIDERS
                && guard.hostileTo(suspect)
                || suspect.hasUnpaidShopItem(tick))) {
            guard.clearPoliceCase();
            return false;
        }
        if (!level.hasChunkAt(suspect.blockPosition())
            || guard.distanceToSqr(suspect) > 22 * 22
            || !DailyRoaming.inRange(guard, suspect.blockPosition())) {
            guard.clearPoliceCase();
            return false;
        }
        guard.setTarget(null); // lawful arrest is NONLETHAL, not guard melee
        if (guard.distanceToSqr(suspect) > 3.24
            || !guard.hasLineOfSight(suspect)) {
            if (RoadNavigator.moveToward(guard, level, suspect.blockPosition(), 1.08)
                || guard.getNavigation().moveTo(suspect, 1.08)) {
                guard.setAction("following reported raider on foot; no teleport");
            }
            return true;
        }
        // Only here, actually within reach and line of sight, custody starts.
        // A native iron cell door is physically opened before the walk;
        // the prisoner is never moved through walls by setPos/teleport.
        CityJailGate.setOpen(level, jail, true);
        suspect.detainAt(jail, tick + CUSTODY_TICKS);
        if (suspect.hasUnpaidShopItem(tick)) suspect.clearUnpaidShopItem();
        System.out.println("REME_NPC_STORY stage=nonlethal_arrest tick=" + tick
            + " guard=" + guard.getUUID() + " suspect=" + suspect.getUUID()
            + " holding=" + jail.toShortString());
        suspect.setAction("nonlethally arrested by nearby guard; walking to authorized holding spot");
        guard.setAction("arrested reported raider at close range; escorting to jail area");
        guard.clearPoliceCase();
        guard.assignPrisonerEscort(suspect.getUUID(), tick + CUSTODY_TICKS);
        return true;
    }

    /** Realistic close-range foot escort after actual lawful arrest. */
    private static boolean tickEscortGuard(PlayerNpcEntity guard, ServerLevel level, long tick) {
        if (tick >= guard.escortUntil()) {
            guard.clearPrisonerEscort();
            return false;
        }
        var found = level.getEntity(guard.escortedPrisoner());
        if (!(found instanceof PlayerNpcEntity prisoner) || !prisoner.isAlive()
            || !prisoner.isDetained(tick)
            || !level.hasChunkAt(prisoner.blockPosition())) {
            guard.clearPrisonerEscort();
            return false;
        }
        BlockPos jail = prisoner.detentionJail();
        if (jail == null || !holdingSpot(level, jail)
            || !DailyRoaming.inRange(guard, jail)) {
            guard.clearPrisonerEscort();
            guard.setAction("prison escort cancelled: jail removed or out of jurisdiction");
            return false;
        }
        guard.setTarget(null);
        boolean prisonerAtCell = CityJailRoute.actuallyInCell(prisoner, jail);
        if (prisonerAtCell && guard.distanceToSqr(prisoner) <= 36) {
            guard.getNavigation().stop();
            guard.setAction("on-foot escort completed: prisoner reached REAL iron-bar cell");
            System.out.println("REME_NPC_STORY stage=jail_reached tick=" + tick
                + " guard=" + guard.getUUID() + " prisoner=" + prisoner.getUUID()
                + " cell=" + jail.toShortString());
            guard.clearPrisonerEscort();
            return true;
        }
        if (!MovementPolicy.due(tick, guard.getId(), 10)) return true;
        if (guard.distanceToSqr(prisoner) > 5.0) {
            RoadNavigator.moveToward(guard, level, prisoner.blockPosition(), 0.98);
            guard.setAction("escorting handcuffed resident ON FOOT along loaded streets");
        } else {
            // Keep safe body-distance, do not physically block the inmate's
            // own vanilla pathfinder at a 1-block door or bar opening.
            guard.getNavigation().stop();
            guard.setAction("escorting resident beside real local jail corridor");
        }
        return true;
    }

    /** Real-world physical walk to a recognized holding spot (not a teleport).
     * Stops robbery/work while detained; expires automatically even on reload.
     */
    public static boolean tickPrisoner(PlayerNpcEntity suspect, ServerLevel level, long tick) {
        BlockPos jail = suspect.detentionJail();
        if (jail == null) return false;
        if (tick >= suspect.detainedUntil() || !holdingSpot(level, jail)) {
            // Completed sentence unlocks the physical exit. The ex-prisoner
            // is free to navigate back into society; no teleport on release.
            CityJailGate.setOpen(level, jail, true);
            suspect.releaseDetention();
            suspect.setAction("released from temporary community detention");
            return false;
        }
        suspect.setTarget(null);
        if (CityJailRoute.actuallyInCell(suspect, jail)) {
            CityJailGate.setOpen(level, jail, false);
            suspect.getNavigation().stop();
            suspect.setAction("held at designated iron-bar holding spot until release");
            return true;
        }
        // A prisoner is NOT allowed to physically open a CLOSED iron door.
        // Only the guard toggles the authenticated existing door on arrest.
        // Once OPEN, vanilla PathfinderMob's default navigation may still
        // label its node as impassable unless setCanPassDoors(true) is set.
        // This was verified in the real GameTest: the inmate reached the
        // open iron door but moveTo(next cell node) always returned false.
        if (CityJailGate.isOpen(level, jail)
            && suspect.getNavigation() instanceof GroundPathNavigation ground)
            ground.setCanPassDoors(true);

        // Real multi-stage native path, bounded to the existing urban
        // station. Before this fix the NPC stalled 4 blocks south of the
        // cell because it kept routing DIRECTLY into the solid station wall.
        if (MovementPolicy.due(tick, suspect.getId(), 10)) {
            BlockPos waypoint = CityJailRoute.nextPhysicalWaypoint(suspect, level, jail);
            if (level.hasChunkAt(waypoint) && level.hasChunkAt(waypoint.below())
                && level.getBlockState(waypoint.below()).isSolid()) {
                boolean requested = suspect.getNavigation().moveTo(
                    waypoint.getX() + 0.5, waypoint.getY(),
                    waypoint.getZ() + 0.5, 0.96);
                suspect.setAction("physically escorting via police station route "
                    + waypoint.toShortString() + (requested ? "" : "; route not found"));
                if (MovementPolicy.due(tick, suspect.getId(), 80)) {
                    System.out.println("REME_NPC_JAIL_ROUTE tick=" + tick
                        + " prisoner=" + suspect.getUUID()
                        + " actual=" + suspect.blockPosition().toShortString()
                        + " destination=" + waypoint.toShortString()
                        + " pathAccepted=" + requested
                        + " navDone=" + suspect.getNavigation().isDone()
                        + " ironGateOpen=" + CityJailGate.isOpen(level, jail));
                }
            } else suspect.setAction("jail escort blocked: real waypoint unsafe or unloaded");
        }
        return true;
    }
}
