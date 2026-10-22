package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import dev.reme.mcstudio.npc.life.PublicSafety;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The police station's external solid walls, door and iron-bar gate are
 *  PHYSICAL vanilla blocks; this test checks actual server AI traversal.
 *  Arrest is a declared fixture, and no positions are changed after start.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhysicalCityPoliceJailGameTests {
    private PhysicalCityPoliceJailGameTests() {}

    @GameTest(template="empty",timeoutTicks=950)
    public static void arrested_inmate_must_walk_around_back_wall_through_real_door_into_cell(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=helper.absolutePos(new BlockPos(23,4,23));
        for(int x=-20;x<=20;x++) for(int z=-20;z<=20;z++) {
            level.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=0;y<=10;y++)
                level.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        CityDistrictDemo.Result built=CityDistrictDemo.build(level,center,null);
        if(!built.success()||built.citizens()!=13)
            throw new AssertionError("Cannot prepare REAL city station and its citizens: "+built);
        BlockPos jail=center.offset(-13,1,15);
        if(!PublicSafety.holdingSpot(level,jail))
            throw new AssertionError("Jail isn't an actual walkable holding spot");
        List<PlayerNpcEntity> people=level.getEntitiesOfClass(PlayerNpcEntity.class,
            new AABB(center.getX()-20,center.getY(),center.getZ()-20,
                center.getX()+21,center.getY()+11,center.getZ()+21));
        PlayerNpcEntity thief=people.stream()
            .filter(n->n.getTags().contains("reme_city_shoplifter"))
            .findFirst().orElseThrow();
        PlayerNpcEntity guard=people.stream()
            .filter(n->n.getTags().contains("reme_city_guard"))
            .findFirst().orElseThrow();
        // Stage an arrest just OUTSIDE the station's SOUTH solid exterior wall,
        // reproducing the actual city X11 recording where vanilla navigation
        // tried to walk directly THROUGH the wall. This is not a chase test.
        BlockPos outside=jail.offset(0,0,6);
        thief.moveTo(outside.getX()+0.5,outside.getY(),outside.getZ()+0.5,0,0);
        guard.moveTo(outside.getX()+1.5,outside.getY(),outside.getZ()+0.5,0,0);
        long tick=level.getGameTime();
        thief.detainAt(jail,tick+900);
        guard.assignPrisonerEscort(thief.getUUID(),tick+900);
        BlockPos waypoint=PublicSafety.accessibleJailWaypoint(thief,level,jail);
        if(waypoint.equals(jail))
            throw new AssertionError("City prisoner mistakenly targets jail through solid police station wall");
        final double originalDistance=thief.distanceToSqr(Vec3.atCenterOf(jail));
        helper.runAfterDelay(700,()->{
            final double arrived=thief.distanceToSqr(Vec3.atCenterOf(jail));
            System.out.println("REME_NPC_REAL_CITY_JAIL_ROUTE"
                +" ticks=700 startDist2="+originalDistance
                +" endDist2="+arrived
                +" prisoner="+thief.blockPosition().toShortString()
                +" jail="+jail.toShortString()
                +" officer="+guard.blockPosition().toShortString()
                +" guardEscorting="+(guard.escortedPrisoner()!=null));
            if(!thief.isAlive()||!guard.isAlive())
                helper.fail("Guard or inmate died during ACTUAL physical street/wall navigation",outside);
            else if(arrived>2.5)
                helper.fail("Prisoner stuck outside the REAL police wall, never got through its oak door and iron-bar gap",jail);
            else if(guard.escortedPrisoner()!=null)
                helper.fail("Guard abandoned physical escort protocol even though inmate entered cell",jail);
            else helper.succeed();
        });
    }
}
