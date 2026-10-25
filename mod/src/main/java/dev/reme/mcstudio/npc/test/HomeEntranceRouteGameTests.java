package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.HomeEntranceRoute;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.RestPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native 1.21.1 GameTests: a bedroom behind a REAL facade has an actual door,
 * not a fictional shortcut through solid stone or an NPC teleport.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeEntranceRouteGameTests {
    private HomeEntranceRouteGameTests() {}

    private static BlockPos house(GameTestHelper helper, boolean doorOpen) {
        ServerLevel level=helper.getLevel();
        BlockPos center=helper.absolutePos(new BlockPos(12,3,12));
        for(int dx=-6;dx<=6;dx++)for(int dz=-6;dz<=6;dz++) {
            BlockPos at=center.offset(dx,0,dz);
            level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
            for(int dy=0;dy<4;dy++)
                level.setBlock(at.above(dy),Blocks.AIR.defaultBlockState(),2);
        }
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {
            if(x!=-3&&x!=3&&z!=-3&&z!=3)continue;
            for(int y=0;y<3;y++)
                level.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        }
        BlockPos door=center.south(3);
        level.setBlock(door,Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER)
            .setValue(DoorBlock.OPEN,doorOpen),3);
        level.setBlock(door.above(),Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER)
            .setValue(DoorBlock.OPEN,doorOpen),3);
        BlockPos bed=center.north().west();
        level.setBlock(bed,Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING,Direction.NORTH)
            .setValue(BedBlock.PART,BedPart.HEAD),3);
        level.setBlock(bed.south(),Blocks.RED_BED.defaultBlockState()
            .setValue(BedBlock.FACING,Direction.NORTH)
            .setValue(BedBlock.PART,BedPart.FOOT),3);
        return center;
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void open_residential_door_has_two_real_standing_stages(
        GameTestHelper helper) {
        BlockPos center=house(helper,true);
        var entry=HomeEntranceRoute.nearestOpenDoor(helper.getLevel(),center.north());
        if(entry==null
            ||!entry.outside().equals(center.south(4))
            ||!entry.inside().equals(center.south(2)))
            throw new AssertionError("Home entry must be actual exterior and interior open-door foot blocks");
        // No bypass through a closed wooden door or locked police iron gate.
        helper.getLevel().setBlock(center.south(3),
            helper.getLevel().getBlockState(center.south(3))
                .setValue(DoorBlock.OPEN,false),3);
        helper.getLevel().setBlock(center.south(3).above(),
            helper.getLevel().getBlockState(center.south(3).above())
                .setValue(DoorBlock.OPEN,false),3);
        if(HomeEntranceRoute.nearestOpenDoor(helper.getLevel(),center.north())!=null)
            throw new AssertionError("Closed door incorrectly accepted as open physical entry");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=70)
    public static void indoor_citizen_does_not_reverse_back_out_of_real_open_home_door(
        GameTestHelper helper) {
        BlockPos center = house(helper, true);
        HomeEntranceRoute.Entrance entry =
            HomeEntranceRoute.nearestOpenDoor(helper.getLevel(), center.north());
        if (entry == null) throw new AssertionError("Actual open house door not found");
        if (!HomeEntranceRoute.nextDoorStage(entry, center).equals(entry.inside())
            || !HomeEntranceRoute.nextDoorStage(entry, center.south(1)).equals(entry.inside()))
            throw new AssertionError("Indoor citizen was routed back onto the street");
        if (!HomeEntranceRoute.nextDoorStage(entry, entry.outside()).equals(entry.inside())
            || !HomeEntranceRoute.nextDoorStage(entry, center.south(5)).equals(entry.inside()))
            throw new AssertionError("Citizen standing at the physical entrance did not enter");
        if (!HomeEntranceRoute.nextDoorStage(entry, center.south(6)).equals(entry.outside()))
            throw new AssertionError("Distant outdoor citizen skipped the exterior approach");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=40)
    public static void tired_night_watch_officer_recovers_only_in_real_bed(
        GameTestHelper helper) {
        if (RestPolicy.lateWatchRecovery(true,16000,42)
            || RestPolicy.lateWatchRecovery(true,19500,65)
            || RestPolicy.lateWatchRecovery(false,19500,42)
            || !RestPolicy.lateWatchRecovery(true,19500,42)
            || RestPolicy.energyDelta(LivingPlanner.Intent.SLEEP,true,42,
                true,false,false,false)!=12
            || RestPolicy.energyDelta(LivingPlanner.Intent.SLEEP,true,42,
                false,false,false,false)!=-3)
            throw new AssertionError("Night guard recovered without physically sleeping, "
                +"or real native bed sleep incorrectly drained energy");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=320)
    public static void exhausted_resident_walks_through_real_door_and_sleeps(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=house(helper,true);
        BlockPos bed=center.north().west();
        PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
        if(npc==null)throw new AssertionError("NPC registration missing");
        BlockPos start=center.south(5);
        npc.moveTo(start.getX()+0.5,start.getY(),start.getZ()+0.5,0,0);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(PlayerNpcEntity.Profession.RESIDENT);
        npc.setHomePos(center.north());
        npc.setAssignedBedPos(bed);
        npc.changeNeeds(0,-95,0);
        npc.setDecision(new LivingPlanner.Decision(
            LivingPlanner.Intent.SLEEP,1000,"physical night rest"));
        if(!level.addFreshEntity(npc))throw new AssertionError("Cannot spawn real resident");
        final boolean[] crossed={false};
        final boolean[] moved={false};
        final double[] last={npc.getZ()};
        for(int t=5;t<=250;t+=5)helper.runAfterDelay(t,()->{
            if(!npc.isAlive())helper.fail("Resident vanished on route to bed",start);
            double z=npc.getZ();
            if(Math.abs(z-last[0])>3.0)
                helper.fail("Resident position jumped without vanilla foot navigation",npc.blockPosition());
            if(Math.abs(z-last[0])>.04)moved[0]=true;
            if(z<center.south(3).getZ()+.1)crossed[0]=true;
            last[0]=z;
        });
        helper.runAfterDelay(260,()->{
            if(!moved[0]||!crossed[0]||!npc.isSleeping()
                ||!npc.getSleepingPos().orElse(BlockPos.ZERO).equals(bed))
                helper.fail("NPC never physically crossed open doorway into its assigned vanilla bed: "
                    +"pos="+npc.blockPosition()+" moved="+moved[0]
                    +" crossed="+crossed[0]+" sleeping="+npc.isSleeping()
                    +" action="+npc.lastAction(),npc.blockPosition());
            else helper.succeed();
        });
    }

    /** A real wooden home door starts CLOSED. Only an NPC actually at its
     * physical entrance may open it; iron gates are never silently unlocked.
     * DoorBlock.setOpen must update both halves as vanilla would.
     */
    @GameTest(template="empty",timeoutTicks=120)
    public static void resident_opens_real_closed_wooden_door_only_when_nearby(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=house(helper,false);
        BlockPos door=center.south(3);
        PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
        if(npc==null)throw new AssertionError("NPC registration missing");
        BlockPos far=center.south(8);
        npc.moveTo(far.getX()+0.5,far.getY(),far.getZ()+0.5,0,0);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(PlayerNpcEntity.Profession.RESIDENT);
        npc.setHomePos(center.north());
        if(!level.addFreshEntity(npc))throw new AssertionError("Cannot spawn real resident");
        HomeEntranceRoute.moveTowardEntrance(npc,level,center.north(),1.0);
        if(level.getBlockState(door).getValue(DoorBlock.OPEN))
            throw new AssertionError("Distant NPC remotely opened a closed door");
        BlockPos outside=center.south(4);
        npc.moveTo(outside.getX()+0.5,outside.getY(),
            outside.getZ()+0.5,0,0);
        HomeEntranceRoute.moveTowardEntrance(npc,level,center.north(),1.0);
        if(!level.getBlockState(door).getValue(DoorBlock.OPEN)
            ||!level.getBlockState(door.above()).getValue(DoorBlock.OPEN))
            throw new AssertionError("Nearby NPC failed to physically open both wooden door halves");
        // A locked iron door is not an acceptable replacement home entrance.
        level.setBlock(door,Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER)
            .setValue(DoorBlock.OPEN,false),3);
        level.setBlock(door.above(),Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING,Direction.SOUTH)
            .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER)
            .setValue(DoorBlock.OPEN,false),3);
        HomeEntranceRoute.moveTowardEntrance(npc,level,center.north(),1.0);
        if(level.getBlockState(door).getValue(DoorBlock.OPEN))
            throw new AssertionError("NPC illegally opened a real iron security door");
        helper.succeed();
    }

    /** Full in-world regression: unlike an already-open showcase entrance,
     * this door starts shut. A tired resident must open the VANILLA wooden
     * block, physically cross its threshold and sleep in the actual bed.
     */
    @GameTest(template="empty",timeoutTicks=400)
    public static void exhausted_resident_opens_closed_door_walks_and_sleeps(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=house(helper,false);
        BlockPos door=center.south(3);
        BlockPos bed=center.north().west();
        PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
        if(npc==null)throw new AssertionError("NPC registration missing");
        BlockPos start=center.south(5);
        npc.moveTo(start.getX()+0.5,start.getY(),start.getZ()+0.5,0,0);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(PlayerNpcEntity.Profession.RESIDENT);
        npc.setHomePos(center.north());
        npc.setAssignedBedPos(bed);
        npc.changeNeeds(0,-95,0);
        npc.setDecision(new LivingPlanner.Decision(
            LivingPlanner.Intent.SLEEP,1000,"physical night rest"));
        if(!level.addFreshEntity(npc))throw new AssertionError("Cannot spawn real resident");
        final boolean[] opened={false}, crossed={false}, moved={false};
        final double[] last={npc.getZ()};
        for(int t=5;t<=280;t+=5)helper.runAfterDelay(t,()->{
            if(!npc.isAlive())helper.fail("Resident vanished on closed-door route",start);
            if(level.getBlockState(door).getValue(DoorBlock.OPEN))opened[0]=true;
            double z=npc.getZ();
            if(Math.abs(z-last[0])>3.0)
                helper.fail("Resident jumped rather than walking through the door",
                    npc.blockPosition());
            if(Math.abs(z-last[0])>.04)moved[0]=true;
            if(z<center.south(3).getZ()+.1)crossed[0]=true;
            last[0]=z;
        });
        helper.runAfterDelay(290,()->{
            if(!opened[0]||!moved[0]||!crossed[0]||!npc.isSleeping()
                ||!npc.getSleepingPos().orElse(BlockPos.ZERO).equals(bed))
                helper.fail("Closed wooden door was not opened and crossed into real bed: "
                    +"pos="+npc.blockPosition()+" opened="+opened[0]
                    +" moved="+moved[0]+" crossed="+crossed[0]
                    +" sleeping="+npc.isSleeping()+" action="+npc.lastAction(),
                    npc.blockPosition());
        });
        helper.runAfterDelay(340,()->{
            if(level.getBlockState(door).getValue(DoorBlock.OPEN))
                helper.fail("Resident reached real bed but left its own wooden home door open",
                    door);
            else helper.succeed();
        });
    }

    /** A second citizen physically occupying the entrance prevents closure.
     * Once both have cleared it, the NPC-opened vanilla door closes again.
     * Entity relocation below is TEST SETUP only, never production travel.
     */
    @GameTest(template="empty",timeoutTicks=190)
    public static void npc_door_waits_for_bystander_then_closes(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=house(helper,false);
        BlockPos door=center.south(3);
        BlockPos outside=center.south(4);
        BlockPos inside=center.south(2);
        PlayerNpcEntity opener=NpcRegistry.PLAYER_NPC.get().create(level);
        PlayerNpcEntity bystander=NpcRegistry.PLAYER_NPC.get().create(level);
        if(opener==null||bystander==null)
            throw new AssertionError("NPC registration missing");
        opener.moveTo(outside.getX()+.5,outside.getY(),outside.getZ()+.5,0,0);
        bystander.moveTo(inside.getX()+.5,inside.getY(),inside.getZ()+.5,0,0);
        opener.setMode(PlayerNpcEntity.Mode.STAY);
        bystander.setMode(PlayerNpcEntity.Mode.STAY);
        opener.setHomePos(center.north());
        bystander.setHomePos(center.north());
        if(!level.addFreshEntity(opener)||!level.addFreshEntity(bystander))
            throw new AssertionError("Could not spawn both real doorway users");
        HomeEntranceRoute.moveTowardEntrance(opener,level,center.north(),1.0);
        opener.getNavigation().stop();
        if(!level.getBlockState(door).getValue(DoorBlock.OPEN))
            throw new AssertionError("Resident never opened its wooden door");
        helper.runAfterDelay(80,()->{
            if(!level.getBlockState(door).getValue(DoorBlock.OPEN))
                helper.fail("Door shut on the real second resident standing in threshold",door);
            opener.getNavigation().stop();
            bystander.getNavigation().stop();
            BlockPos farOutside=center.south(6);
            BlockPos farInside=center.north();
            opener.moveTo(farOutside.getX()+.5,farOutside.getY(),
                farOutside.getZ()+.5,0,0);
            bystander.moveTo(farInside.getX()+.5,farInside.getY(),
                farInside.getZ()+.5,0,0);
        });
        helper.runAfterDelay(145,()->{
            if(level.getBlockState(door).getValue(DoorBlock.OPEN)
                ||level.getBlockState(door.above()).getValue(DoorBlock.OPEN))
                helper.fail("NPC-opened door did not close after both residents left",door);
            else helper.succeed();
        });
    }

    /** Do not claim ownership of an already open player-controlled door. */
    @GameTest(template="empty",timeoutTicks=130)
    public static void already_open_wooden_door_is_not_auto_closed(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos center=house(helper,true);
        BlockPos door=center.south(3);
        PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
        if(npc==null)throw new AssertionError("NPC registration missing");
        BlockPos far=center.south(6);
        npc.moveTo(far.getX()+.5,far.getY(),far.getZ()+.5,0,0);
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        if(!level.addFreshEntity(npc))throw new AssertionError("Cannot spawn resident");
        helper.runAfterDelay(90,()->{
            if(!level.getBlockState(door).getValue(DoorBlock.OPEN))
                helper.fail("Untracked, already-open wooden door was modified",door);
            else helper.succeed();
        });
    }

    /** Door maintenance must not depend on a living NPC ticking. A resident
     * can open its home door, then die/despawn or leave the dimension before
     * the 50-tick grace period ends. The server-level tick must still close
     * the actual wooden door when its threshold is empty.
     */
    @GameTest(template="empty",timeoutTicks=150)
    public static void npc_opened_door_closes_even_after_last_resident_disappears(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = house(helper,false);
        BlockPos door = center.south(3);
        BlockPos outside = center.south(4);
        PlayerNpcEntity resident = NpcRegistry.PLAYER_NPC.get().create(level);
        if (resident == null) throw new AssertionError("NPC registration missing");
        resident.moveTo(outside.getX()+.5,outside.getY(),outside.getZ()+.5,0,0);
        resident.setMode(PlayerNpcEntity.Mode.STAY);
        resident.setHomePos(center.north());
        if (!level.addFreshEntity(resident))
            throw new AssertionError("Cannot spawn actual resident");
        HomeEntranceRoute.moveTowardEntrance(resident,level,center.north(),1.0);
        resident.getNavigation().stop();
        if (!level.getBlockState(door).getValue(DoorBlock.OPEN))
            throw new AssertionError("Resident did not physically open the door");
        // Test-only entity removal, not production NPC navigation.
        resident.discard();
        helper.runAfterDelay(90,()->{
            if (resident.isAlive() || level.getBlockState(door).getValue(DoorBlock.OPEN)
                || level.getBlockState(door.above()).getValue(DoorBlock.OPEN))
                helper.fail("NPC-opened home door stayed open after its last resident vanished",door);
            else helper.succeed();
        });
    }

    /** Vanilla normalizes a door's facing when one half changes. Replacing
     * the upper half with a real solid block instead models a genuinely
     * damaged entrance; the route must not target the missing doorway.
     */
    @GameTest(template="empty",timeoutTicks=80)
    public static void missing_upper_door_half_is_not_a_real_home_entrance(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = house(helper,true);
        BlockPos door = center.south(3);
        level.setBlock(door.above(),Blocks.STONE.defaultBlockState(),3);
        if (!level.getBlockState(door.above()).is(Blocks.STONE))
            throw new AssertionError("Broken-door test fixture did not persist");
        if (HomeEntranceRoute.nearestOpenDoor(level,center.north()) != null)
            throw new AssertionError("Damaged door was accepted as a real open entrance");
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC registration missing");
        BlockPos outside = center.south(4);
        npc.moveTo(outside.getX()+.5,outside.getY(),outside.getZ()+.5,0,0);
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        npc.setHomePos(center.north());
        if (!level.addFreshEntity(npc)) throw new AssertionError("Cannot spawn resident");
        if (HomeEntranceRoute.moveTowardEntrance(npc,level,center.north(),1.0))
            throw new AssertionError("Resident routed into a damaged two-block doorway");
        helper.succeed();
    }

    /** A powered, still-closed wooden door is not NPC-owned or safe to
     * operate. The fixture simulates a transient inconsistent redstone state
     * without spawning a synthetic door or bypassing the normal route code.
     */
    @GameTest(template="empty",timeoutTicks=80)
    public static void powered_closed_wooden_door_is_not_forced_open_by_npc(
        GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = house(helper,false);
        BlockPos door = center.south(3);
        level.setBlock(door,level.getBlockState(door)
            .setValue(DoorBlock.POWERED,true),2);
        level.setBlock(door.above(),level.getBlockState(door.above())
            .setValue(DoorBlock.POWERED,true),2);
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC registration missing");
        BlockPos outside = center.south(4);
        npc.moveTo(outside.getX()+.5,outside.getY(),outside.getZ()+.5,0,0);
        npc.setMode(PlayerNpcEntity.Mode.STAY);
        npc.setHomePos(center.north());
        if (!level.addFreshEntity(npc)) throw new AssertionError("Cannot spawn resident");
        if (HomeEntranceRoute.moveTowardEntrance(npc,level,center.north(),1.0)
            || level.getBlockState(door).getValue(DoorBlock.OPEN))
            throw new AssertionError("Resident forced a powered closed wooden door open");
        helper.succeed();
    }
}
