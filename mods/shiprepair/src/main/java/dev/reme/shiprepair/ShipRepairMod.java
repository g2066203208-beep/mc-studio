package dev.reme.shiprepair;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ShipRepairMod.ID)
public final class ShipRepairMod {
    public static final String ID="shiprepair";
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ID);
    public static final DeferredBlock<RepairStationBlock> STATION=BLOCKS.register(
            "repair_station",()->new RepairStationBlock(Block.Properties.of().strength(5f,9f)));
    public static final DeferredItem<BlockItem> STATION_ITEM=ITEMS.register(
            "repair_station",()->new BlockItem(STATION.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<RepairStationBlockEntity>>
            STATION_ENTITY=ENTITIES.register("repair_station",
                    ()->BlockEntityType.Builder.of(RepairStationBlockEntity::new,STATION.get()).build(null));
    public ShipRepairMod(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(this::creative);
    }
    private void creative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(STATION_ITEM);
    }
}
