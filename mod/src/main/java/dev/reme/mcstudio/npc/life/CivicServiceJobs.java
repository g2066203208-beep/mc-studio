package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Real loaded-world civic work, not idle animation:
 * medic consumes ONE physical golden apple to heal a genuinely wounded
 * nearby NPC; librarian physically exchanges one existing book for one
 * customer's emerald. No magical stock/payroll and no player inventories.
 */
public final class CivicServiceJobs {
    private CivicServiceJobs() {}

    public static boolean tick(PlayerNpcEntity staff, ServerLevel world, long tick) {
        if (staff.mode() != PlayerNpcEntity.Mode.LIVE
            || !staff.isAlive() || staff.isSleeping() || staff.isDetained(tick)
            || staff.isAfraid(tick)
            || staff.intent() != LivingPlanner.Intent.WORK
            || (staff.profession() != PlayerNpcEntity.Profession.MEDIC
                && staff.profession() != PlayerNpcEntity.Profession.LIBRARIAN))
            return false;
        if (!world.hasChunkAt(staff.workPos())
            || !DailyRoaming.inRange(staff, staff.workPos())) return false;
        if (staff.blockPosition().distSqr(staff.workPos()) > 9) {
            if (MovementPolicy.due(tick, staff.getId(), 40)) {
                // Civic jobs are indoors behind REAL facades. Greedily
                // navigating toward the desk through its solid wall stranded
                // the librarian outside for the entire work shift. Prefer a
                // fully reachable native route, then stage a genuine open
                // building door. Never count a remote job as attendance.
                var route = staff.getNavigation().createPath(staff.workPos(), 0);
                if (route != null && route.canReach()
                    && staff.getNavigation().moveTo(route, 0.92))
                    staff.setAction("walking native reachable route to real civic desk");
                else if (HomeEntranceRoute.moveTowardEntrance(
                    staff, world, staff.workPos(), 0.92))
                    staff.setAction("entering real hospital/library through open door");
                else if (RoadNavigator.moveToward(staff, world, staff.workPos(), 0.92))
                    staff.setAction("commuting on foot to hospital or public library duty");
                else
                    staff.setAction("civic desk unreachable: waiting without fake attendance");
            }
            return true;
        }
        if (!MovementPolicy.due(tick, staff.getId(), 160)) return true;
        if (staff.profession() == PlayerNpcEntity.Profession.MEDIC)
            treatInjured(staff, world, tick);
        else
            loanBook(staff, world, tick);
        return true;
    }

    public static boolean treatInjured(PlayerNpcEntity medic, ServerLevel world, long tick) {
        if (medic.profession()!=PlayerNpcEntity.Profession.MEDIC
            || medic.mode()!=PlayerNpcEntity.Mode.LIVE
            || !world.getServer().isSameThread()
            || medic.isSleeping() || !medic.isAlive()
            || !ShopCounter.scheduledAt(world.getDayTime(),medic.dailyShiftTicks())
            || medic.backpack().countItem(Items.GOLDEN_APPLE)<1)
            return false;
        var patients=world.getEntitiesOfClass(PlayerNpcEntity.class,
            medic.getBoundingBox().inflate(3),
            other->other!=medic && other.isAlive() && !other.isDetained(tick)
                && medic.identity().region().equals(other.identity().region())
                && !medic.hostileTo(other)
                && other.getHealth() <= other.getMaxHealth()-4
                && world.hasChunkAt(other.blockPosition()));
        if (patients.isEmpty()) return false;
        var patient=patients.stream()
            .min(Comparator.comparingDouble(medic::distanceToSqr)).orElseThrow();
        if (medic.distanceToSqr(patient)>9 || !medic.hasLineOfSight(patient))
            return false;
        SimpleContainer stocks=snapshot(medic.backpack());
        if (!take(stocks,Items.GOLDEN_APPLE)) return false;
        commit(medic.backpack(),stocks);
        float before=patient.getHealth();
        patient.heal(8);
        medic.setAction("consumed own REAL golden apple caring for a wounded local resident");
        patient.setAction("received real clinic care from nearby medic");
        System.out.println("REME_NPC_CITY_DAY stage=medical_treatment tick="+tick
            +" patient="+patient.getUUID()+" before="+before
            +" after="+patient.getHealth()+" paidGoldenApple=1");
        return true;
    }

    public static boolean loanBook(PlayerNpcEntity librarian, ServerLevel world,long tick) {
        if(librarian.profession()!=PlayerNpcEntity.Profession.LIBRARIAN
            || librarian.mode()!=PlayerNpcEntity.Mode.LIVE
            || !librarian.isAlive() || librarian.isSleeping()
            || !world.getServer().isSameThread()
            || !ShopCounter.scheduledAt(world.getDayTime(),librarian.dailyShiftTicks())
            || librarian.backpack().countItem(Items.BOOK)<1) return false;
        var readers=world.getEntitiesOfClass(PlayerNpcEntity.class,
            librarian.getBoundingBox().inflate(3),
            other->other!=librarian && other.isAlive()
                && other.mode()==PlayerNpcEntity.Mode.LIVE
                && other.profession()==PlayerNpcEntity.Profession.RESIDENT
                && !other.isDetained(tick) && !other.isSleeping()
                && librarian.identity().region().equals(other.identity().region())
                && !librarian.hostileTo(other)
                && other.backpack().countItem(Items.EMERALD)>0);
        if(readers.isEmpty())return false;
        var customer=readers.stream()
            .min(Comparator.comparingDouble(librarian::distanceToSqr)).orElseThrow();
        if(librarian.distanceToSqr(customer)>9 || !librarian.hasLineOfSight(customer))
            return false;
        SimpleContainer source=snapshot(librarian.backpack());
        SimpleContainer buyer=snapshot(customer.backpack());
        if (!take(source,Items.BOOK) || !take(buyer,Items.EMERALD)
            || !source.addItem(new ItemStack(Items.EMERALD)).isEmpty()
            || !buyer.addItem(new ItemStack(Items.BOOK)).isEmpty())
            return false;
        commit(librarian.backpack(),source);
        commit(customer.backpack(),buyer);
        librarian.setAction("librarian sold one real book for one actual citizen emerald");
        customer.setAction("reading a real book purchased at neighborhood library");
        System.out.println("REME_NPC_CITY_DAY stage=real_library_sale tick="+tick
            +" reader="+customer.getUUID()+" books=1 emeralds=1");
        return true;
    }

    private static SimpleContainer snapshot(SimpleContainer from){
        SimpleContainer copy=new SimpleContainer(from.getContainerSize());
        for(int i=0;i<from.getContainerSize();i++)
            copy.setItem(i,from.getItem(i).copy());
        return copy;
    }
    private static boolean take(SimpleContainer inventory,net.minecraft.world.item.Item item){
        for(int i=0;i<inventory.getContainerSize();i++)
            if(inventory.getItem(i).is(item)){
                inventory.getItem(i).shrink(1);
                return true;
            }
        return false;
    }
    private static void commit(SimpleContainer inventory,SimpleContainer snapshot){
        for(int i=0;i<inventory.getContainerSize();i++)
            inventory.setItem(i,snapshot.getItem(i).copy());
        inventory.setChanged();
    }
}
