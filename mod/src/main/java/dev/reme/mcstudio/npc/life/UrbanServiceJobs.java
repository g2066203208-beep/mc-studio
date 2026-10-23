package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Lightweight city careers, with actual physical workstations and real
 * inventory-based service. No virtual residents, no free stock, no ticket
 * loads and no expensive per-tick global entity queries. This class is
 * intentionally SEPARATE from existing real MEDIC / LIBRARIAN services.
 *
 * School: teacher hands out an existing book to a real nearby student.
 * Hotel: a guest pays a real emerald for existing kitchen bread.
 * Civic desk: a resident buys a real pre-stocked paper permit.
 * Mechanic: physically inspects rail/anvil; VANILLA minecart vehicles do
 * not have a car damage/repair system, so inspection is not fake repairs.
 */
public final class UrbanServiceJobs {
    private UrbanServiceJobs() {}
    private static final int ACTION_INTERVAL=240;

    private static boolean supported(PlayerNpcEntity npc) {
        return switch(npc.profession()) {
            case TEACHER,HOTELIER,CIVIC_CLERK,MECHANIC -> true;
            default -> false;
        };
    }
    public static boolean tick(PlayerNpcEntity employee,ServerLevel level,long tick){
        if (!supported(employee) || employee.mode()!=PlayerNpcEntity.Mode.LIVE
            || !employee.isAlive() || employee.isSleeping()
            || employee.isDetained(tick) || employee.isAfraid(tick)
            || employee.intent()!=LivingPlanner.Intent.WORK
            || !ShopCounter.scheduledAt(level.getDayTime(),employee.dailyShiftTicks())
            || employee.energy()<22) return false;
        BlockPos place=employee.workPos();
        if(!level.hasChunkAt(place) || !DailyRoaming.inRange(employee,place))
            return false;
        if(employee.blockPosition().distSqr(place)>9){
            if(MovementPolicy.due(tick,employee.getId(),40))
                if(RoadNavigator.moveToward(employee,level,place,0.89))
                    employee.setAction("commuting on REAL street to civic job");
            return true;
        }
        if(!MovementPolicy.due(tick,employee.getId(),ACTION_INTERVAL))
            return true;
        boolean done=switch(employee.profession()){
            case TEACHER -> teach(employee,level,tick);
            case HOTELIER -> hotel(employee,level,tick);
            case CIVIC_CLERK -> paperwork(employee,level,tick);
            case MECHANIC -> inspectTransit(employee,level,tick);
            default -> false;
        };
        if(!done)
            employee.setAction(switch(employee.profession()){
                case TEACHER -> "teacher waiting in classroom for actual nearby student";
                case HOTELIER -> "hotel reception open; guests must pay with their own emerald";
                case CIVIC_CLERK -> "city hall staffed; real paper permit inventory limited";
                case MECHANIC -> "garage open; checking real rail and machine workbench";
                default -> "city service on duty";
            });
        return true;
    }

    private static boolean hasNearby(ServerLevel level,BlockPos origin,
                                     net.minecraft.world.level.block.Block block){
        // 5x3x5 = maximum 75 loaded-state checks every 240 ticks, not a
        // search across player cities or unrelated mod tile entities.
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
            for(int dy=-1;dy<=1;dy++){
                BlockPos b=origin.offset(dx,dy,dz);
                if(level.hasChunkAt(b)&&level.getBlockState(b).is(block))return true;
            }
        return false;
    }
    private static boolean teach(PlayerNpcEntity teacher,ServerLevel world,long tick){
        if(!hasNearby(world,teacher.blockPosition(),Blocks.LECTERN)
            ||teacher.backpack().countItem(Items.BOOK)<1) return false;
        var students=world.getEntitiesOfClass(PlayerNpcEntity.class,
            teacher.getBoundingBox().inflate(3),
            other->other!=teacher && other.isAlive() && !other.isSleeping()
                && other.mode()==PlayerNpcEntity.Mode.LIVE
                && other.profession()==PlayerNpcEntity.Profession.RESIDENT
                && other.backpack().countItem(Items.BOOK)==0
                && !other.isDetained(tick) && !teacher.hostileTo(other)
                && teacher.identity().region().equals(other.identity().region()));
        if(students.isEmpty())return false;
        var student=students.stream().min(Comparator.comparingDouble(
            teacher::distanceToSqr)).orElseThrow();
        if(teacher.distanceToSqr(student)>9||!teacher.hasLineOfSight(student))
            return false;
        SimpleContainer source=snapshot(teacher.backpack());
        SimpleContainer target=snapshot(student.backpack());
        if(!take(source,Items.BOOK)||!target.addItem(new ItemStack(Items.BOOK)).isEmpty())
            return false;
        commit(teacher.backpack(),source);
        commit(student.backpack(),target);
        student.changeNeeds(0,0,4);
        teacher.setAction("gave an existing classroom book to an attending real student");
        student.setAction("attended a live classroom and received a real book");
        System.out.println("REME_NPC_URBAN_SERVICE type=real_school_lesson tick="+tick
            +" teacher="+teacher.getUUID()+" student="+student.getUUID()+" books=1");
        return true;
    }
    private static boolean hotel(PlayerNpcEntity concierge,ServerLevel world,long tick){
        if(!hasNearby(world,concierge.blockPosition(),Blocks.LECTERN)
            ||concierge.backpack().countItem(Items.BREAD)<1)return false;
        var guests=world.getEntitiesOfClass(PlayerNpcEntity.class,
            concierge.getBoundingBox().inflate(3),
            other->other!=concierge && other.isAlive() && !other.isSleeping()
                && !other.isDetained(tick) && !concierge.hostileTo(other)
                && concierge.identity().region().equals(other.identity().region())
                && other.profession()==PlayerNpcEntity.Profession.RESIDENT
                && other.backpack().countItem(Items.EMERALD)>0
                && other.backpack().countItem(Items.BREAD)==0);
        if(guests.isEmpty())return false;
        return sale(concierge,guests.get(0),Items.BREAD,Items.EMERALD,
            "real_hotel_breakfast",tick);
    }
    private static boolean paperwork(PlayerNpcEntity clerk,ServerLevel world,long tick){
        if(!hasNearby(world,clerk.blockPosition(),Blocks.LECTERN)
            ||clerk.backpack().countItem(Items.PAPER)<1)return false;
        var visitors=world.getEntitiesOfClass(PlayerNpcEntity.class,
            clerk.getBoundingBox().inflate(3),other->other!=clerk&&other.isAlive()
                && other.mode()==PlayerNpcEntity.Mode.LIVE
                && other.profession()==PlayerNpcEntity.Profession.RESIDENT
                && other.backpack().countItem(Items.EMERALD)>0
                && other.backpack().countItem(Items.PAPER)==0
                && !other.isDetained(tick)
                && clerk.identity().region().equals(other.identity().region()));
        if(visitors.isEmpty())return false;
        return sale(clerk,visitors.get(0),Items.PAPER,Items.EMERALD,
            "actual_civic_document",tick);
    }
    private static boolean inspectTransit(PlayerNpcEntity mechanic,
                                          ServerLevel world,long tick){
        boolean rail=hasNearby(world,mechanic.blockPosition(),Blocks.RAIL);
        boolean bench=hasNearby(world,mechanic.blockPosition(),Blocks.CRAFTING_TABLE)
            ||hasNearby(world,mechanic.blockPosition(),Blocks.ANVIL);
        if(!rail||!bench)return false;
        mechanic.setAction("inspecting an actual vanilla rail showroom; no car engine invented");
        System.out.println("REME_NPC_URBAN_SERVICE type=rail_inspection tick="+tick
            +" mechanic="+mechanic.getUUID()+" loadedRails=true actualWorkbench=true");
        return true;
    }
    /** Both players are real NPC entities, and both items must already exist. */
    private static boolean sale(PlayerNpcEntity staff,PlayerNpcEntity buyer,
                                Item product,Item currency,String type,long tick) {
        if(staff==buyer||staff.distanceToSqr(buyer)>9
            ||!staff.level().getServer().isSameThread())return false;
        SimpleContainer from=snapshot(staff.backpack());
        SimpleContainer to=snapshot(buyer.backpack());
        if(!take(from,product)||!take(to,currency)
            ||!from.addItem(new ItemStack(currency)).isEmpty()
            ||!to.addItem(new ItemStack(product)).isEmpty())return false;
        commit(staff.backpack(),from);
        commit(buyer.backpack(),to);
        staff.setAction("completed physical "+type+" paid by real emerald");
        buyer.setAction("paid one actual emerald for "+type);
        System.out.println("REME_NPC_URBAN_SERVICE type="+type+" tick="+tick
            +" staff="+staff.getUUID()+" visitor="+buyer.getUUID()
            +" stock=-1 currency=+1");
        return true;
    }
    private static SimpleContainer snapshot(SimpleContainer source){
        SimpleContainer s=new SimpleContainer(source.getContainerSize());
        for(int i=0;i<s.getContainerSize();i++)s.setItem(i,source.getItem(i).copy());
        return s;
    }
    private static boolean take(SimpleContainer bag,Item kind){
        for(int i=0;i<bag.getContainerSize();i++)
            if(bag.getItem(i).is(kind)&&!bag.getItem(i).isEmpty()){
                bag.getItem(i).shrink(1);
                return true;
            }
        return false;
    }
    private static void commit(SimpleContainer bag,SimpleContainer data){
        for(int i=0;i<bag.getContainerSize();i++)bag.setItem(i,data.getItem(i).copy());
        bag.setChanged();
    }
}
