package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.MovementPolicy;
import dev.reme.mcstudio.npc.life.SocietyRules;
import dev.reme.mcstudio.npc.life.UrbanServiceJobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UrbanServiceGameTests {
    private UrbanServiceGameTests() {}
    private static PlayerNpcEntity citizen(ServerLevel level,BlockPos at,
        PlayerNpcEntity.Profession job){
        PlayerNpcEntity npc=NpcRegistry.PLAYER_NPC.get().create(level);
        if(npc==null)throw new AssertionError("NPC registry not loaded");
        npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        npc.setHomePos(at);npc.setWorkPos(at);npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(job);
        npc.setIdentity(SocietyRules.civilian("civic_job_test"));
        npc.setDecision(new LivingPlanner.Decision(LivingPlanner.Intent.WORK,99,"physical civic workplace"));
        if(!level.addFreshEntity(npc))throw new AssertionError("Citizen not spawned");
        return npc;
    }
    private static long due(ServerLevel level,PlayerNpcEntity npc){
        for(long t=level.getGameTime();t<level.getGameTime()+241;t++)
            if(MovementPolicy.due(t,npc.getId(),240))return t;
        throw new AssertionError("No bounded work event tick");
    }
    private static BlockPos site(ServerLevel world,GameTestHelper h){
        BlockPos p=h.absolutePos(new BlockPos(6,9,6));
        for(int x=-3;x<=8;x++)for(int z=-3;z<=8;z++){
            BlockPos b=p.offset(x,-1,z);
            world.setBlock(b,Blocks.STONE.defaultBlockState(),3);
            world.setBlock(b.above(),Blocks.AIR.defaultBlockState(),3);
            world.setBlock(b.above(2),Blocks.AIR.defaultBlockState(),3);
        }
        return p;
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void actual_school_teacher_gives_existing_book_to_real_student(GameTestHelper h){
        ServerLevel world=h.getLevel();BlockPos p=site(world,h);
        world.setBlock(p.north(),Blocks.LECTERN.defaultBlockState(),3);
        var teacher=citizen(world,p,PlayerNpcEntity.Profession.TEACHER);
        var student=citizen(world,p.east(2),PlayerNpcEntity.Profession.RESIDENT);
        teacher.backpack().addItem(new ItemStack(Items.BOOK,1));
        if(!UrbanServiceJobs.tick(teacher,world,due(world,teacher))
            ||teacher.backpack().countItem(Items.BOOK)!=0
            ||student.backpack().countItem(Items.BOOK)!=1
            ||student.socialNeed()<=0)
            throw new AssertionError("Physical school book was not conserved/teacher never taught");
        student.backpack().setItem(0,ItemStack.EMPTY);
        UrbanServiceJobs.tick(teacher,world,due(world,teacher)+240);
        if(student.backpack().countItem(Items.BOOK)!=0)
            throw new AssertionError("School regenerated a free book after classroom stock depleted");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void hotel_actual_guest_pays_emerald_for_real_breakfast(GameTestHelper h){
        ServerLevel world=h.getLevel();BlockPos p=site(world,h);
        world.setBlock(p.north(),Blocks.LECTERN.defaultBlockState(),3);
        var hotel=citizen(world,p,PlayerNpcEntity.Profession.HOTELIER);
        var guest=citizen(world,p.east(),PlayerNpcEntity.Profession.RESIDENT);
        hotel.backpack().addItem(new ItemStack(Items.BREAD,1));
        guest.backpack().addItem(new ItemStack(Items.EMERALD,1));
        UrbanServiceJobs.tick(hotel,world,due(world,hotel));
        if(hotel.backpack().countItem(Items.BREAD)!=0
            ||hotel.backpack().countItem(Items.EMERALD)!=1
            ||guest.backpack().countItem(Items.EMERALD)!=0
            ||guest.backpack().countItem(Items.BREAD)!=1)
            throw new AssertionError("Hotel breakfast did not debit actual stock and guest cash");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void government_real_paperwork_sells_limited_existing_paper(GameTestHelper h){
        ServerLevel world=h.getLevel();BlockPos p=site(world,h);
        world.setBlock(p.north(),Blocks.LECTERN.defaultBlockState(),3);
        var clerk=citizen(world,p,PlayerNpcEntity.Profession.CIVIC_CLERK);
        var guest=citizen(world,p.east(),PlayerNpcEntity.Profession.RESIDENT);
        clerk.backpack().addItem(new ItemStack(Items.PAPER,1));
        guest.backpack().addItem(new ItemStack(Items.EMERALD,1));
        UrbanServiceJobs.tick(clerk,world,due(world,clerk));
        if(clerk.backpack().countItem(Items.PAPER)!=0
            ||clerk.backpack().countItem(Items.EMERALD)!=1
            ||guest.backpack().countItem(Items.PAPER)!=1
            ||guest.backpack().countItem(Items.EMERALD)!=0)
            throw new AssertionError("City hall failed real inventory-backed permit exchange");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=120)
    public static void mechanic_requires_actual_loaded_rails_and_workbench(GameTestHelper h){
        ServerLevel world=h.getLevel();BlockPos p=site(world,h);
        var mechanic=citizen(world,p,PlayerNpcEntity.Profession.MECHANIC);
        world.setBlock(p.north(),Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        UrbanServiceJobs.tick(mechanic,world,due(world,mechanic));
        if(mechanic.lastAction().contains("inspecting an actual vanilla rail"))
            throw new AssertionError("Mechanic fabricated inspection of a nonexistent rail");
        world.setBlock(p.east(2),Blocks.RAIL.defaultBlockState(),3);
        UrbanServiceJobs.tick(mechanic,world,due(world,mechanic)+240);
        if(!mechanic.lastAction().contains("inspecting an actual vanilla rail"))
            throw new AssertionError("Mechanic ignored genuine physical rail workshop");
        h.succeed();
    }
}
