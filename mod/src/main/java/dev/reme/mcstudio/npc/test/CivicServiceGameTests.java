package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.CivicServiceJobs;
import dev.reme.mcstudio.npc.life.SocietyRules;
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
public final class CivicServiceGameTests {
    private CivicServiceGameTests() {}
    private static PlayerNpcEntity person(ServerLevel level, BlockPos pos,
                                          PlayerNpcEntity.Profession job) {
        PlayerNpcEntity p=NpcRegistry.PLAYER_NPC.get().create(level);
        if(p==null)throw new AssertionError("Entity type unavailable");
        p.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
        p.setProfession(job);
        p.setMode(PlayerNpcEntity.Mode.LIVE);
        p.setHomePos(pos);
        p.setWorkPos(pos);
        p.setIdentity(SocietyRules.civilian("services_test"));
        if(!level.addFreshEntity(p))throw new AssertionError("No real NPC spawn");
        return p;
    }

    @GameTest(template="empty",timeoutTicks=90)
    public static void medics_consume_real_medicine_and_repair_actual_npc_health(
        GameTestHelper helper) {
        ServerLevel world=helper.getLevel();
        BlockPos origin=helper.absolutePos(new BlockPos(5,9,5));
        for(int x=0;x<4;x++){
            world.setBlock(origin.east(x).below(),Blocks.STONE.defaultBlockState(),3);
            world.setBlock(origin.east(x),Blocks.AIR.defaultBlockState(),3);
        }
        PlayerNpcEntity medic=person(world,origin,PlayerNpcEntity.Profession.MEDIC);
        PlayerNpcEntity patient=person(world,origin.east(2),PlayerNpcEntity.Profession.RESIDENT);
        medic.backpack().addItem(new ItemStack(Items.GOLDEN_APPLE,2));
        patient.setHealth(11);
        if(!CivicServiceJobs.treatInjured(medic,world,world.getGameTime())
            || patient.getHealth()<=11
            || medic.backpack().countItem(Items.GOLDEN_APPLE)!=1)
            throw new AssertionError("Clinical treatment did not heal a real patient using one existing apple");
        if(CivicServiceJobs.treatInjured(medic,world,world.getGameTime())
            && patient.getHealth()>=patient.getMaxHealth())
            throw new AssertionError("Medical stock consumed treating an already healthy NPC");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=90)
    public static void librarian_makes_real_emerald_book_swap_and_denies_unfunded_customer(
        GameTestHelper helper) {
        ServerLevel world=helper.getLevel();
        BlockPos origin=helper.absolutePos(new BlockPos(5,9,5));
        for(int x=0;x<4;x++){
            world.setBlock(origin.east(x).below(),Blocks.STONE.defaultBlockState(),3);
            world.setBlock(origin.east(x),Blocks.AIR.defaultBlockState(),3);
        }
        PlayerNpcEntity librarian=person(world,origin,PlayerNpcEntity.Profession.LIBRARIAN);
        PlayerNpcEntity reader=person(world,origin.east(2),PlayerNpcEntity.Profession.RESIDENT);
        librarian.backpack().addItem(new ItemStack(Items.BOOK,2));
        reader.backpack().addItem(new ItemStack(Items.EMERALD,1));
        if(!CivicServiceJobs.loanBook(librarian,world,world.getGameTime())
            || librarian.backpack().countItem(Items.BOOK)!=1
            || librarian.backpack().countItem(Items.EMERALD)!=1
            || reader.backpack().countItem(Items.BOOK)!=1
            || reader.backpack().countItem(Items.EMERALD)!=0)
            throw new AssertionError("Real library failed atomic 1 emerald -> 1 book");
        if(CivicServiceJobs.loanBook(librarian,world,world.getGameTime())
            || librarian.backpack().countItem(Items.BOOK)!=1)
            throw new AssertionError("Unfunded reader generated books");
        helper.succeed();
    }
}
