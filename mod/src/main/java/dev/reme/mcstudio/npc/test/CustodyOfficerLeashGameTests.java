package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.PublicSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
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
        // The suspect may NOT move independently while officer is >10 blocks
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
                || officer.distanceToSqr(prisoner)>100.0) {
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
}
