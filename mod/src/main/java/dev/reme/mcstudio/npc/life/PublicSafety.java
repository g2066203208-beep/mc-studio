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
        // Two-way persisted custody identity: a suspect arrested by a real
        // officer must not silently run alone to the jail while that officer
        // is stranded on the opposite side of town.
        suspect.assignCustodyOfficer(guard.getUUID());
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
        if (found == null) {
            // getEntity(UUID) sees only loaded entities. A temporarily
            // unloaded prisoner is NOT an abandoned case: clearing this
            // reciprocal link strands the prisoner on its next chunk load.
            // Keep the existing bounded escort deadline; never force-load,
            // fabricate an entity, or move the officer to unloaded terrain.
            guard.getNavigation().stop();
            guard.setTarget(null);
            guard.setAction("escorting paused: prisoner entity not loaded");
            return true;
        }
        if (!(found instanceof PlayerNpcEntity prisoner) || !prisoner.isAlive()
            || !prisoner.isDetained(tick)) {
            guard.clearPrisonerEscort();
            return false;
        }
        if (!level.hasChunkAt(prisoner.blockPosition())) {
            guard.getNavigation().stop();
            guard.setTarget(null);
            guard.setAction("escorting paused: prisoner chunk unloaded");
            return true;
        }
        BlockPos jail = prisoner.detentionJail();
        if (jail != null && !level.hasChunkAt(jail)) {
            // A remote cell's chunk can unload while the prisoner and
            // arresting officer remain loaded. Unloaded != demolished.
            // Keep the persisted two-way UUID custody link, stop walking,
            // and resume only when the real cell chunk becomes available.
            // Never force-load the prison or release the detainee here.
            guard.getNavigation().stop();
            guard.setAction("escorting paused: authorized jail chunk unloaded");
            return true;
        }
        if (jail == null || !holdingSpot(level, jail)
            || !DailyRoaming.inRange(guard, jail)) {
            guard.clearPrisonerEscort();
            guard.setAction("prison escort cancelled: jail removed or out of jurisdiction");
            return false;
        }
        guard.setTarget(null);
        // Bounded evidence trace: the same pair UUIDs, their actual feet
        // coordinates, squared distance and navigation status. GUI footage
        // alone cannot prove that a nearby unrelated officer was the escort.
        if (MovementPolicy.due(tick, guard.getId(), 40)) {
            System.out.println("REME_NPC_ESCORT_PAIR tick=" + tick
                + " officer=" + guard.getUUID()
                + " prisoner=" + prisoner.getUUID()
                + " officerFeet=" + guard.blockPosition().toShortString()
                + " prisonerFeet=" + prisoner.blockPosition().toShortString()
                + " distanceSq=" + guard.distanceToSqr(prisoner)
                + " prisonerMayAdvance=" + escortedPrisonerMayAdvance(prisoner, level, tick)
                + " officerPathDone=" + guard.getNavigation().isDone());
        }
        boolean prisonerAtCell = prisoner.blockPosition().equals(jail);
        // A cell arrival is not an escorted handoff while the officer is
        // still five or six blocks away. Use the same four-block reciprocal
        // leash enforced during the walk, including at the final cell.
        if (prisonerAtCell && guard.distanceToSqr(prisoner) <= 16.0) {
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

    /** Real spatial escort leash, not a teleport or passenger mount.
     * A linked detainee pauses if the arresting officer is not alive, no
     * longer owns the same case, or is more than four blocks away. This is
     * checked only against the persisted UUID: no nearby guard may
     * impersonate the actual arresting officer. Legacy saved detainees
     * without a custody officer continue to use the old custody route.
     */
    public static boolean escortedPrisonerMayAdvance(PlayerNpcEntity prisoner,
                                                      ServerLevel level, long tick) {
        UUID officerId = prisoner.custodyOfficer();
        if (officerId == null) return true;
        var found = level.getEntity(officerId);
        return found instanceof PlayerNpcEntity officer
            && officer.isAlive()
            && officer.mode() == PlayerNpcEntity.Mode.LIVE
            && officer.profession() == PlayerNpcEntity.Profession.GUARD
            && prisoner.getUUID().equals(officer.escortedPrisoner())
            && tick < officer.escortUntil()
            && officer.distanceToSqr(prisoner) <= 16.0;
    }

    /** Real-world physical walk to a recognized holding spot (not a teleport).
     * Stops robbery/work while detained; expires automatically even on reload.
     */
    public static boolean tickPrisoner(PlayerNpcEntity suspect, ServerLevel level, long tick) {
        BlockPos jail = suspect.detentionJail();
        if (jail == null) return false;
        if (tick >= suspect.detainedUntil()) {
            // A sentence expires on game time even if its remote jail is
            // unloaded; never force-load chunks or detain indefinitely.
            // The physical door is opened when it is actually loaded.
            if (level.hasChunkAt(jail)) CityJailGate.setOpen(level, jail, true);
            suspect.releaseDetention();
            suspect.setAction("released after lawful custody sentence expired");
            return false;
        }
        if (!level.hasChunkAt(jail)) {
            // CRITICAL: holdingSpot() returns false for unloaded chunks.
            // The old code treated that as a destroyed jail and silently
            // released an evidence-backed prisoner, clearing the officer
            // UUID and corrupting a legitimate escort across chunk borders.
            // Pause the NPC in the real world until the existing jail loads.
            suspect.getNavigation().stop();
            suspect.setAction("custody paused: authorized jail chunk unloaded");
            return true;
        }
        if (!holdingSpot(level, jail)) {
            // A genuinely removed/invalid jail in a LOADED chunk is not a
            // lawful holding cell. Release without teleport or ghost bars.
            suspect.releaseDetention();
            suspect.setAction("released: real holding cell was removed");
            return false;
        }
        suspect.setTarget(null);
        if (suspect.blockPosition().equals(jail)) {
            CityJailGate.setOpen(level, jail, false);
            suspect.getNavigation().stop();
            suspect.setAction("held at designated iron-bar holding spot until release");
            return true;
        }
        if (!escortedPrisonerMayAdvance(suspect, level, tick)) {
            suspect.getNavigation().stop();
            suspect.setAction("waiting for arresting officer to resume real foot escort");
            if (MovementPolicy.due(tick, suspect.getId(), 80)) {
                com.mojang.logging.LogUtils.getLogger().info(
                    "REME_NPC_ESCORT_WAIT prisoner={} officer={} feet={} jail={}",
                    suspect.getUUID(), suspect.custodyOfficer(),
                    suspect.blockPosition().toShortString(), jail.toShortString());
            }
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
        // The built city's south intake is reached from OUTSIDE the rear
        // wall. A prisoner on the east side of the cell can be INSIDE the
        // station, with the only exit at the east front door. Native A* to
        // the rear intake then correctly reports unreachable through the
        // station wall. Walk out of the REAL front door, follow the eastern
        // exterior facade, and turn west along the south sidewalk. Derive
        // stages from feet and real doors; no teleport or saved state.
        BlockPos waypoint = custodyCityDetourWaypoint(
            level, jail, suspect.blockPosition());
        if (!level.hasChunkAt(waypoint) || !level.hasChunkAt(waypoint.below())) {
            suspect.setAction("custody route unloaded; waiting without chunk tickets");
            return true;
        }
        // The original GUI client exposed a real failure: a thief arrested
        // 35+ blocks from jail could not A* directly to its intake (native
        // search returns a short 20-node UNREACHABLE partial path). The
        // previous code stopped forever at the arrest site while a different
        // second thief, arrested near jail, completed custody. Only the
        // far-away EXTERIOR leg may use bounded real-road sub-waypoints.
        BlockPos exterior = jail.south(4);
        boolean exteriorLeg = waypoint.equals(exterior);
        if (exteriorLeg && suspect.blockPosition().distSqr(exterior) > 14 * 14) {
            if (RoadNavigator.moveTowardCustodyIntake(suspect, level, exterior, 0.96)) {
                suspect.setAction("walking successive reachable real road tiles toward jail");
            } else if (MovementPolicy.due(tick, suspect.getId(), 80)) {
                com.mojang.logging.LogUtils.getLogger().warn(
                    "REME_NPC_JAIL_ROAD_STALLED uuid={} feet={} intake={} jail={}",
                    suspect.getUUID(), suspect.blockPosition().toShortString(),
                    exterior.toShortString(), jail.toShortString());
            }
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
            } else if (exteriorLeg
                && RoadNavigator.moveTowardCustodyIntake(suspect, level, exterior, 0.96)) {
                suspect.setAction("following reachable street detour to real jail intake");
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

    /**
     * Only the materialized 41x41 city police station has this geometry:
     * front east door at (cell +7 X, -3 Z), rear oak door at +3 Z,
     * iron gate at +2 Z. In that layout an arrested resident at the EAST
     * iron-bar wall is INSIDE the station, and cannot walk straight south
     * through the exterior wall. Use the actual east door before the
     * public southern sidewalk. Generic operator-built jails retain the
     * original direct SOUTH entrance routing.
     *
     * The chosen points are navigation goals, never entity coordinates
     * assigned by the server. Unloaded doors do not trigger a detour.
     */
    public static BlockPos custodyCityDetourWaypoint(
        ServerLevel level, BlockPos jail, BlockPos feet) {
        BlockPos frontDoor = jail.offset(7, 0, -3);
        BlockPos rearDoor = jail.south(3);
        if (!level.hasChunkAt(frontDoor) || !level.hasChunkAt(rearDoor)
            || !CityJailGate.isGate(level, jail)
            || !level.getBlockState(frontDoor).is(Blocks.OAK_DOOR)
            || !level.getBlockState(rearDoor).is(Blocks.OAK_DOOR))
            return custodyWaypoint(jail, feet);

        // The eastern station wall lies at X+7. Go through its REAL
        // open front door (X+7,Z-3) before navigating the outside wall.
        // Do not redirect people who are already on the south sidewalk.
        // IMPORTANT: a thief arrested 30+ blocks away must still use
        // RoadNavigator's short, reachable road legs to the rear exterior.
        // The special east-door detour is ONLY for feet physically within
        // the station east corridor or immediately outside its east wall.
        // Previously it also redirected a distant thief to an unreachable
        // 22-node A* path to the southeast corner, freezing all custody.
        if (feet.getY() == jail.getY()) {
            boolean insideEastCorridor =
                feet.getX() > jail.getX() + 1
                && feet.getX() < jail.getX() + 7
                && feet.getZ() >= jail.getZ() - 9
                && feet.getZ() < jail.getZ() + 4;
            if (insideEastCorridor)
                return jail.offset(8, 0, -3);
            boolean justOutsideEastWall =
                feet.getX() >= jail.getX() + 7
                && feet.getX() <= jail.getX() + 10
                && feet.getZ() >= jail.getZ() - 4
                && feet.getZ() < jail.getZ() + 4;
            if (justOutsideEastWall)
                return jail.offset(8, 0, 4);
        }
        return custodyWaypoint(jail, feet);
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
        if (Math.abs(feet.getX() - jail.getX()) > 1)
            return exterior;
        // Already at the inner aisle: finish the last real step into the
        // cell. Do NOT bounce back out through the rear entrance.
        if (feet.getZ() == jail.getZ() + 1)
            return jail;
        // From the SOUTH public street or actual iron-door tile, continue
        // through the open gate into the inner aisle.
        if (feet.getZ() >= jail.getZ() + 2)
            return interior;
        // Aligned but still outside the cell's south entrance corridor.
        return exterior;
    }
}
