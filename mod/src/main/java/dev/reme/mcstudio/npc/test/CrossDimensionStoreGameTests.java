package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.ShopCounter;
import dev.reme.mcstudio.npc.life.SocietyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real dimension identities, real NPC backpacks and vanilla items.
 * Equal numeric XYZ in separate levels must NEVER authorize a purchase.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrossDimensionStoreGameTests {
    private CrossDimensionStoreGameTests() {}

    private static PlayerNpcEntity citizen(ServerLevel level, BlockPos at,
                                            PlayerNpcEntity.Profession profession,
                                            boolean register) {
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
        if (npc == null) throw new AssertionError("NPC type missing");
        npc.moveTo(at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0);
        npc.setHomePos(at);
        npc.setWorkPos(at);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setProfession(profession);
        npc.setIdentity(SocietyRules.civilian("dimension_trade_test"));
        if (register && !level.addFreshEntity(npc))
            throw new AssertionError("Could not register local NPC");
        return npc;
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void equal_xyz_in_nether_cannot_transfer_emeralds_or_goods(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        if (nether == null || nether == overworld)
            throw new AssertionError("Two real dimension instances required");
        BlockPos at = helper.absolutePos(new BlockPos(5, 9, 5));
        PlayerNpcEntity shop = citizen(overworld, at,
            PlayerNpcEntity.Profession.SHOPKEEPER, true);
        PlayerNpcEntity farmer = citizen(overworld, at.east(),
            PlayerNpcEntity.Profession.FARMER, true);
        PlayerNpcEntity buyer = citizen(overworld, at.south(),
            PlayerNpcEntity.Profession.RESIDENT, true);
        // A real Nether entity instance at exactly the same numeric coordinates.
        // Do not generate a remote chunk or create a duplicate resident there.
        PlayerNpcEntity remoteFarmer = citizen(nether, at.east(),
            PlayerNpcEntity.Profession.FARMER, false);
        PlayerNpcEntity remoteBuyer = citizen(nether, at.south(),
            PlayerNpcEntity.Profession.RESIDENT, false);
        shop.setDecision(new LivingPlanner.Decision(
            LivingPlanner.Intent.WORK, 100, "staffed counter"));
        shop.backpack().addItem(new ItemStack(Items.EMERALD, 3));
        shop.backpack().addItem(new ItemStack(Items.BREAD, 2));
        farmer.backpack().addItem(new ItemStack(Items.CARROT, 3));
        buyer.backpack().addItem(new ItemStack(Items.EMERALD, 1));
        remoteFarmer.backpack().addItem(new ItemStack(Items.CARROT, 3));
        remoteBuyer.backpack().addItem(new ItemStack(Items.EMERALD, 1));

        if (!ShopCounter.samePhysicalWorld(shop, farmer)
            || !ShopCounter.samePhysicalWorld(shop, buyer)
            || ShopCounter.samePhysicalWorld(shop, remoteFarmer)
            || ShopCounter.samePhysicalWorld(shop, remoteBuyer))
            throw new AssertionError("Cross-dimension entity identity gate is incorrect");
        long tick = overworld.getGameTime();
        if (ShopCounter.buyHarvestFromFarmer(shop, remoteFarmer, tick)
            || ShopCounter.purchaseByResident(shop, remoteBuyer, tick)
            || shop.backpack().countItem(Items.EMERALD) != 3
            || shop.backpack().countItem(Items.BREAD) != 2
            || shop.backpack().countItem(Items.CARROT) != 0
            || remoteFarmer.backpack().countItem(Items.CARROT) != 3
            || remoteFarmer.backpack().countItem(Items.EMERALD) != 0
            || remoteBuyer.backpack().countItem(Items.EMERALD) != 1
            || remoteBuyer.backpack().countItem(Items.BREAD) != 0)
            throw new AssertionError("Remote dimension transferred real inventory");

        // Positive controls: the exact same stock, price, identities and
        // positions must allow transactions when both entities share a world.
        if (!ShopCounter.buyHarvestFromFarmer(shop, farmer, tick)
            || !ShopCounter.purchaseByResident(shop, buyer, tick)
            || shop.backpack().countItem(Items.EMERALD) != 3
            || farmer.backpack().countItem(Items.EMERALD) != 1
            || buyer.backpack().countItem(Items.EMERALD) != 0
            || buyer.backpack().countItem(Items.BREAD) != 1)
            throw new AssertionError("Same-dimension real transactions regressed");
        helper.succeed();
    }
}
