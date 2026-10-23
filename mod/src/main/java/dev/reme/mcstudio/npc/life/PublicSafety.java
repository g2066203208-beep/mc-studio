package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
        // Proximity is NOT an arrest. Native melee animations, bounded real
        // damage and three successive contact hits are required to stun.
        // A miss/blocked damage never advances the takedown counter.
        if (!suspect.isPoliceStunned(tick)) {
            if (!guard.canPoliceSwing(tick)) return true;
            guard.markPoliceSwing(tick);
            guard.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            // A harmless no-damage proximity "hit" is not evidence of a
            // successful physical strike. Never drop health below one heart.
            float nonlethal = Math.min(1.0F, suspect.getHealth() - 2.0F);
            boolean landed = nonlethal > 0.0F
                && suspect.hurt(guard.damageSources().mobAttack(guard), nonlethal);
            if (!landed) {
                guard.setAction("nonlethal baton strike blocked; pursuing suspect");
                return true;
            }
            suspect.knockback(0.18, guard.getX() - suspect.getX(),
                guard.getZ() - suspect.getZ());
            boolean stunned = suspect.registerNonlethalBlow(tick);
            System.out.println("REME_NPC_STORY stage=police_contact tick=" + tick
                + " guard=" + guard.getUUID() + " suspect=" + suspect.getUUID()
                + " blows=" + suspect.policeBlows() + " stunned=" + stunned
                + " guardPos=" + guard.blockPosition().toShortString()
                + " suspectPos=" + suspect.blockPosition().toShortString());
            if (!stunned) {
                guard.setAction("struck fleeing thief; still resisting, pursuit continues");
                return true;
            }
            suspect.getNavigation().stop();
            suspect.setAction("knocked unconscious by three real nonlethal contacts");
            // Twenty REAL ticks of visible collapse before any handcuffs.
            // No invisible instant conversion from impact to custody.
            return true;
        }
        // Officer must remain within real reach during the knockdown.
        // Only perform handcuffs after the visible twenty-tick collapse.
        if (suspect.isPoliceStunned(tick)
            && tick - suspect.policeLastBlow() < 20L) {
            guard.getNavigation().stop();
            guard.setAction("guarding genuinely unconscious suspect before handcuffs");
            return true;
        }
        // Only an actually stunned, evidence-backed suspect can be handcuffed.
        // A native iron cell door is opened before the physical escort.
        CityJailGate.setOpen(level, jail, true);
        suspect.detainAt(jail, tick + CUSTODY_TICKS);
        if (suspect.hasUnpaidShopItem(tick)) suspect.clearUnpaidShopItem();
        System.out.println("REME_NPC_STORY stage=post_stun_arrest tick=" + tick
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
        boolean prisonerAtCell = prisoner.distanceToSqr(Vec3.atCenterOf(jail)) <= 2.5;
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
        if (suspect.distanceToSqr(Vec3.atCenterOf(jail)) <= 2.5) {
            CityJailGate.setOpen(level, jail, false);
            suspect.getNavigation().stop();
            suspect.setAction("held at designated iron-bar holding spot until release");
            return true;
        }
        // A* to the cell centre alone stops on the EAST iron-bar wall:
        // the real police cell has its ONLY entrance on the SOUTH, behind an
        // actual oak rear door and a server-controlled iron gate. Stage the
        // route on foot: public street -> exterior intake -> inside gate ->
        // holding cell. No setPos/moveTo teleport, no invented prison.
        if (CityJailGate.isGate(level, jail) && !CityJailGate.isOpen(level, jail)) {
            // Custody has already been lawfully authorized by tickGuard.
            // The gate is opened only for this still-detained prisoner.
            if (!CityJailGate.setOpen(level, jail, true)) {
                suspect.setAction("real cell gate cannot open; custody route waiting");
                return true;
            }
        }
        BlockPos waypoint = custodyWaypoint(jail, suspect.blockPosition());
        if (!level.hasChunkAt(waypoint) || !level.hasChunkAt(waypoint.below())) {
            suspect.setAction("custody route unloaded; waiting without chunk tickets");
            return true;
        }
        // Never reset an advancing native A* path every 10 ticks. Replan
        // after reaching the waypoint or at a bounded 80-tick interval.
        if (suspect.getNavigation().isDone()
            || MovementPolicy.due(tick, suspect.getId(), 80)) {
            net.minecraft.world.level.pathfinder.Path path =
                suspect.getNavigation().createPath(waypoint, 0);
            if (path != null && path.canReach()
                && suspect.getNavigation().moveTo(path, 0.96)) {
                suspect.setAction("walking real rear intake and open iron gate under custody");
            } else {
                suspect.getNavigation().stop();
                suspect.setAction("native jail intake route blocked; no ghost custody");
                if (MovementPolicy.due(tick, suspect.getId(), 80))
                    com.mojang.logging.LogUtils.getLogger().error(
                        "REME_NPC_JAIL_ROUTE_BLOCKED uuid={} feet={} jail={} "
                        + "waypoint={} gateOpen={} pathNodes={} canReach={}",
                        suspect.getUUID(), suspect.blockPosition().toShortString(),
                        jail.toShortString(), waypoint.toShortString(),
                        CityJailGate.isOpen(level, jail),
                        path == null ? -1 : path.getNodeCount(),
                        path != null && path.canReach());
            }
        }
        return true;
    }

    /** Route stages describe REAL Minecraft block coordinates, not
     * movement. The actual entity must physically reach each waypoint
     * using native GroundPathNavigation; GameTests verify that separately.
     *
     * Jail layout: holding z=0, interior z=+1, iron gate z=+2,
     * oak rear door z=+3, public exterior sidewalk z=+4.
     */
    public static BlockPos custodyWaypoint(BlockPos jail, BlockPos feet) {
        BlockPos interior = jail.south(1);
        BlockPos exterior = jail.south(4);
        if (feet.getY() != jail.getY()) return exterior;
        // Do not confuse an NPC on the outside EAST wall with one inside
        // the cell: the prisoner must line up with the SOUTH intake first.
        if (Math.abs(feet.getX() - jail.getX()) > 1
            || feet.getZ() < jail.getZ() + 2)
            return exterior;
        if (feet.distSqr(interior) > 1)
            return interior;
        return jail;
    }
}
