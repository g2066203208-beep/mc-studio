package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.PublicSafety;
import dev.reme.mcstudio.npc.life.CityJailGate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native server tests for reciprocal physical custody, not a mock event log.
 * Existing legacy prisoners without NpcCustodyOfficer remain compatible.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CustodyOfficerLeashGameTests {
    private CustodyOfficerLeashGameTests() {}

    private static PlayerNpcEntity npc(ServerLevel level, BlockPos feet,
                                       PlayerNpcEntity.Profession job) {
        PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
        if(npc==null)throw new AssertionError("Real NPC entity type missing");
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(job);
        npc.setHomePos(feet);
        npc.setRoamingRadius(64);
        npc.moveTo(feet.getX()+0.5,feet.getY(),feet.getZ()+0.5,0,0);
        if(!level.addFreshEntity(npc))
            throw new AssertionError("Failed to spawn real NPC at "+feet);
        return npc;
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void actual_officer_uuid_not_nearby_impostor_controls_custody(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos base=helper.absolutePos(new BlockPos(8,2,8));
        PlayerNpcEntity prisoner=npc(level,base,PlayerNpcEntity.Profession.RESIDENT);
        PlayerNpcEntity officer=npc(level,base.east(13),
                                    PlayerNpcEntity.Profession.GUARD);
        PlayerNpcEntity bystander=npc(level,base.east(1),
                                      PlayerNpcEntity.Profession.GUARD);
        long tick=level.getGameTime();
        prisoner.detainAt(base.south(4),tick+500);
        // Old saved detainees do not have this optional NBT field.
        if(!PublicSafety.escortedPrisonerMayAdvance(prisoner,level,tick))
            throw new AssertionError("Legacy world prisoner became frozen");
        prisoner.assignCustodyOfficer(officer.getUUID());
        bystander.assignPrisonerEscort(prisoner.getUUID(),tick+500);
        if(PublicSafety.escortedPrisonerMayAdvance(prisoner,level,tick))
            throw new AssertionError("Wrong nearby guard impersonated arresting officer");
        officer.assignPrisonerEscort(prisoner.getUUID(),tick+500);
        if(PublicSafety.escortedPrisonerMayAdvance(prisoner,level,tick))
            throw new AssertionError("Distant officer did not hold the suspect");
        // Save must persist exact officer identity, not the bystander's UUID.
        CompoundTag saved=new CompoundTag();
        prisoner.saveWithoutId(saved);
        if(!saved.hasUUID("NpcCustodyOfficer")
            || !saved.getUUID("NpcCustodyOfficer").equals(officer.getUUID()))
            throw new AssertionError("Reciprocal custody identity not persisted");
        officer.moveTo(base.getX()+2.5,base.getY(),base.getZ()+0.5,0,0);
        if(!PublicSafety.escortedPrisonerMayAdvance(prisoner,level,tick))
            throw new AssertionError("Real nearby arresting officer cannot resume escort");
        officer.clearPrisonerEscort();
        if(PublicSafety.escortedPrisonerMayAdvance(prisoner,level,tick))
            throw new AssertionError("Officer who abandoned case still authorizes movement");
        prisoner.releaseDetention();
        if(prisoner.custodyOfficer()!=null)
            throw new AssertionError("Release left stale saved officer UUID");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=500)
    public static void prisoner_waits_for_actual_walking_officer_then_moves(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos jail=helper.absolutePos(new BlockPos(10,2,10));
        // Include the real south-intake waypoint at z=+4 and its
        // approach. The previous fixture stopped at z=+3, leaving a
        // two-block fall at the mandatory intake and producing
        // REME_NPC_JAIL_ROUTE_BLOCKED while this test still passed.
        for(int x=-2;x<=25;x++)for(int z=-3;z<=6;z++){
            BlockPos tile=jail.offset(x,0,z);
            level.setBlock(tile.below(),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(int y=0;y<3;y++)
                level.setBlock(tile.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        level.setBlock(jail.north(),Blocks.IRON_BARS.defaultBlockState(),2);
        if(!PublicSafety.holdingSpot(level,jail))
            throw new AssertionError("Physical custody holding spot missing");
        PlayerNpcEntity prisoner=npc(level,jail.east(5),
                                     PlayerNpcEntity.Profession.RESIDENT);
        PlayerNpcEntity officer=npc(level,jail.east(22),
                                    PlayerNpcEntity.Profession.GUARD);
        officer.setHomePos(jail);
        long now=level.getGameTime();
        prisoner.detainAt(jail,now+1500);
        prisoner.assignCustodyOfficer(officer.getUUID());
        officer.assignPrisonerEscort(prisoner.getUUID(),now+1500);
        final double initialX=prisoner.getX();
        // The suspect may NOT move independently while officer is >4 blocks
        // away. The officer must actually walk along the stone street.
        helper.runAfterDelay(8,()->{
            if(Math.abs(prisoner.getX()-initialX)>0.5
                || !prisoner.lastAction().contains("waiting for arresting officer"))
                helper.fail("Prisoner walked to cell alone before officer arrived",
                    prisoner.blockPosition());
        });
        helper.runAfterDelay(400,()->{
            if(!prisoner.isAlive()||!officer.isAlive()) {
                helper.fail("Physical escort actor disappeared",jail);
                return;
            }
            // Displacement alone is NOT an escort success: the old test
            // passed after the prisoner fell two blocks into an unpaved
            // intake, while native pathfinding logged ROUTE_BLOCKED.
            if(!prisoner.blockPosition().equals(jail)
                || !prisoner.isDetained(level.getGameTime())
                || officer.distanceToSqr(prisoner)>16.0) {
                helper.fail("Custody must physically reach its real cell on the "
                    +"correct Y level with its own officer; not merely move: "
                    +"officer="+officer.blockPosition()
                    +" prisoner="+prisoner.blockPosition()
                    +" cell="+jail
                    +" action="+prisoner.lastAction(),prisoner.blockPosition());
                return;
            }
            if(Math.abs(prisoner.getX()-initialX)<0.7) {
                helper.fail("Prisoner never moved from arrest point",jail);
                return;
            }
            helper.succeed();
        });
    }

    /** The real arresting officer, not a nearby impostor, must stay close
     * enough to be visible beside the prisoner. No teleport, mount or NBT
     * migration is needed; old saves without an officer remain supported.
     */
    @GameTest(template="empty",timeoutTicks=100)
    public static void linked_prisoner_stops_five_blocks_ahead_and_resumes_at_four(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos base=helper.absolutePos(new BlockPos(8,2,8));
        PlayerNpcEntity prisoner=npc(level,base,PlayerNpcEntity.Profession.RESIDENT);
        PlayerNpcEntity officer=npc(level,base.east(5),PlayerNpcEntity.Profession.GUARD);
        long now=level.getGameTime();
        prisoner.detainAt(base.south(4),now+400);
        prisoner.assignCustodyOfficer(officer.getUUID());
        officer.assignPrisonerEscort(prisoner.getUUID(),now+400);
        if(PublicSafety.escortedPrisonerMayAdvance(prisoner,level,now))
            throw new AssertionError("Five-block officer gap allowed solo custody walking");
        officer.moveTo(base.getX()+4.5,base.getY(),base.getZ()+0.5,0,0);
        if(!PublicSafety.escortedPrisonerMayAdvance(prisoner,level,now))
            throw new AssertionError("Four-block officer gap did not resume foot escort");
        officer.clearPrisonerEscort();
        if(PublicSafety.escortedPrisonerMayAdvance(prisoner,level,now))
            throw new AssertionError("Expired reciprocal escort still allowed movement");
        prisoner.assignCustodyOfficer(null);
        if(!PublicSafety.escortedPrisonerMayAdvance(prisoner,level,now))
            throw new AssertionError("Legacy saved detainee without officer was frozen");
        helper.succeed();
    }

    /** A temporarily absent entity is not proof of release or death.
     * The loaded officer must retain the same persisted prisoner UUID until
     * the existing escort deadline, without spawning or force-loading anyone.
     */
    @GameTest(template="empty",timeoutTicks=100)
    public static void missing_prisoner_keeps_escort_link_until_deadline(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos base=helper.absolutePos(new BlockPos(8,2,8));
        PlayerNpcEntity officer=npc(level,base,PlayerNpcEntity.Profession.GUARD);
        UUID absentPrisoner=UUID.randomUUID();
        long now=level.getGameTime();
        officer.assignPrisonerEscort(absentPrisoner,now+80);
        if(level.getEntity(absentPrisoner)!=null)
            throw new AssertionError("Missing-prisoner fixture unexpectedly loaded");
        if(!PublicSafety.tickGuard(officer,level,now)
            || !absentPrisoner.equals(officer.escortedPrisoner())
            || officer.escortUntil()!=now+80)
            throw new AssertionError("Unloaded prisoner silently lost reciprocal custody");
        CompoundTag saved=new CompoundTag();
        officer.saveWithoutId(saved);
        if(!saved.hasUUID("NpcEscortedPrisoner")
            || !absentPrisoner.equals(saved.getUUID("NpcEscortedPrisoner"))
            || saved.getLong("NpcEscortUntil")!=now+80)
            throw new AssertionError("Paused escort link was not save-compatible");
        if(PublicSafety.tickGuard(officer,level,now+80)
            || officer.escortedPrisoner()!=null)
            throw new AssertionError("Absent-prisoner escort did not expire on time");
        helper.succeed();
    }

    /** Reaching the real cell does not finish an escort with the arresting
     * officer still five blocks away. The last handoff must respect the same
     * four-block leash as the rest of the physical walking route.
     */
    @GameTest(template="empty",timeoutTicks=100)
    public static void real_cell_handoff_waits_for_own_officer_within_four_blocks(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos jail=helper.absolutePos(new BlockPos(10,2,10));
        for(int dx=-1;dx<=7;dx++)for(int dz=-2;dz<=2;dz++) {
            BlockPos tile=jail.offset(dx,0,dz);
            level.setBlock(tile.below(),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(int dy=0;dy<3;dy++)
                level.setBlock(tile.above(dy),Blocks.AIR.defaultBlockState(),2);
        }
        level.setBlock(jail.north(),Blocks.IRON_BARS.defaultBlockState(),2);
        if(!PublicSafety.holdingSpot(level,jail))
            throw new AssertionError("Real iron-bar holding cell fixture invalid");
        PlayerNpcEntity prisoner=npc(level,jail,PlayerNpcEntity.Profession.RESIDENT);
        PlayerNpcEntity officer=npc(level,jail.east(5),PlayerNpcEntity.Profession.GUARD);
        officer.setHomePos(jail);
        long now=level.getGameTime();
        prisoner.detainAt(jail,now+400);
        prisoner.assignCustodyOfficer(officer.getUUID());
        officer.assignPrisonerEscort(prisoner.getUUID(),now+400);
        if(!PublicSafety.tickGuard(officer,level,now)
            || !prisoner.getUUID().equals(officer.escortedPrisoner()))
            throw new AssertionError("Five-block officer gap falsely completed jail escort");
        // Arrange the boundary position in the test fixture only; the
        // production escort must continue using vanilla foot navigation.
        officer.moveTo(jail.getX()+4.5,jail.getY(),jail.getZ()+0.5,0,0);
        if(!PublicSafety.tickGuard(officer,level,now)
            || officer.escortedPrisoner()!=null)
            throw new AssertionError("Officer at four blocks failed to complete real cell handoff");
        if(!prisoner.isDetained(now) || !prisoner.blockPosition().equals(jail))
            throw new AssertionError("Handoff changed real prisoner position or detention");
        helper.succeed();
    }

    /** Real registered NPCs: the legal four-block UUID leash is not enough
     * to start another moving A* segment. Keep one block of walking slack
     * so the native pathfinder can turn while the actual officer catches up.
     */
    @GameTest(template="empty",timeoutTicks=100)
    public static void walking_slack_stops_at_three_and_half_and_resumes_at_two_and_half(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos base=helper.absolutePos(new BlockPos(8,2,8));
        PlayerNpcEntity prisoner=npc(level,base,PlayerNpcEntity.Profession.RESIDENT);
        PlayerNpcEntity officer=npc(level,base.east(4),PlayerNpcEntity.Profession.GUARD);
        long now=level.getGameTime();
        prisoner.detainAt(base.south(4),now+400);
        prisoner.assignCustodyOfficer(officer.getUUID());
        officer.assignPrisonerEscort(prisoner.getUUID(),now+400);
        if(!PublicSafety.escortedPrisonerMayAdvance(prisoner,level,now))
            throw new AssertionError("Four-block custody authorization regressed");
        if(PublicSafety.escortedPrisonerMayWalk(prisoner,level,now))
            throw new AssertionError("Four-block separation began a new physical walking leg");
        officer.moveTo(base.getX()+4.0,base.getY(),base.getZ()+0.5,0,0);
        if(PublicSafety.escortedPrisonerMayWalk(prisoner,level,now))
            throw new AssertionError("Three-and-half-block gap has no pathfinding slack");
        officer.moveTo(base.getX()+3.0,base.getY(),base.getZ()+0.5,0,0);
        if(!PublicSafety.escortedPrisonerMayWalk(prisoner,level,now))
            throw new AssertionError("Real linked guard at two-and-half blocks cannot resume");
        officer.clearPrisonerEscort();
        if(PublicSafety.escortedPrisonerMayWalk(prisoner,level,now))
            throw new AssertionError("Stale officer link bypassed walking slack");
        prisoner.assignCustodyOfficer(null);
        if(!PublicSafety.escortedPrisonerMayWalk(prisoner,level,now))
            throw new AssertionError("Legacy saved prisoner without officer cannot walk");
        helper.succeed();
    }

    /** The inmate arriving at a real iron cell before the escorting officer
     * must not lock the gate and declare custody complete by themselves.
     * The real officer must reach the cell before the door closes.
     */
    @GameTest(template="empty",timeoutTicks=100)
    public static void real_cell_gate_waits_for_officer_handoff_before_locking(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos jail=helper.absolutePos(new BlockPos(10,2,10));
        for(int dx=-2;dx<=8;dx++)for(int dz=-2;dz<=4;dz++) {
            BlockPos tile=jail.offset(dx,0,dz);
            level.setBlock(tile.below(),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(int dy=0;dy<3;dy++)
                level.setBlock(tile.above(dy),Blocks.AIR.defaultBlockState(),2);
        }
        level.setBlock(jail.north(),Blocks.IRON_BARS.defaultBlockState(),2);
        BlockPos gate=CityJailGate.gateFor(jail);
        level.setBlock(gate,Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER)
            .setValue(DoorBlock.OPEN,true),2);
        level.setBlock(gate.above(),Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER)
            .setValue(DoorBlock.OPEN,true),2);
        if(!PublicSafety.holdingSpot(level,jail)
            || !CityJailGate.isOpen(level,jail))
            throw new AssertionError("Real cell gate fixture not open and valid");
        PlayerNpcEntity prisoner=npc(level,jail,PlayerNpcEntity.Profession.RESIDENT);
        PlayerNpcEntity officer=npc(level,jail.east(6),PlayerNpcEntity.Profession.GUARD);
        officer.setHomePos(jail);
        long now=level.getGameTime();
        prisoner.detainAt(jail,now+400);
        prisoner.assignCustodyOfficer(officer.getUUID());
        officer.assignPrisonerEscort(prisoner.getUUID(),now+400);
        if(!PublicSafety.tickPrisoner(prisoner,level,now)
            || !CityJailGate.isOpen(level,jail)
            || !prisoner.getUUID().equals(officer.escortedPrisoner()))
            throw new AssertionError("Prisoner locked real gate before officer arrived");
        officer.moveTo(jail.getX()+3.0,jail.getY(),jail.getZ()+0.5,0,0);
        if(!PublicSafety.tickGuard(officer,level,now)
            || officer.escortedPrisoner()!=null)
            throw new AssertionError("Nearby real officer could not finish handoff");
        if(!PublicSafety.tickPrisoner(prisoner,level,now)
            || CityJailGate.isOpen(level,jail)
            || !prisoner.isDetained(now))
            throw new AssertionError("Gate did not lock after genuine handoff");
        helper.succeed();
    }
}
