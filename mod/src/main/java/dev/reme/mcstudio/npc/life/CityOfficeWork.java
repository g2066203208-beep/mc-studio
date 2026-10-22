package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Explicit 41-block creative-city DEMO office route only. This feature does
 * not assign office jobs to arbitrary player-owned NPCs in older worlds.
 * Its real lectern is a bounded, loaded vanilla block and no GUI is faked.
 */
public final class CityOfficeWork {
    private CityOfficeWork() {}

    public static boolean tick(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if (!npc.getTags().contains("reme_city_office_worker")
            || npc.mode() != PlayerNpcEntity.Mode.LIVE
            || npc.profession() != PlayerNpcEntity.Profession.RESIDENT
            || npc.isDetained(tick) || npc.isAfraid(tick)
            || npc.isSleeping() || npc.energy() <= 26
            || npc.hunger() <= 32
            || npc.intent() == LivingPlanner.Intent.SLEEP
            || npc.intent() == LivingPlanner.Intent.ESCAPE
            || !level.hasChunkAt(npc.workPos())
            || !DailyRoaming.inRange(npc,npc.workPos())) return false;
        long clock=Math.floorMod(level.getDayTime()+npc.dailyShiftTicks(),24000L);
        BlockPos desk=npc.workPos();
        if (clock>=1800 && clock<9000) {
            // The city demo's "office" is a real lectern and walkable desk.
            if (!level.getBlockState(desk.west()).is(Blocks.LECTERN)) return false;
            if (npc.blockPosition().distSqr(desk)>5.0) {
                if (MovementPolicy.due(tick,npc.getId(),40))
                    RoadNavigator.moveToward(npc,level,desk,0.9);
                npc.setAction("on the morning commute to physical office desk");
            } else {
                npc.getNavigation().stop();
                npc.setAction("working actual morning office shift at local lectern");
            }
            return true;
        }
        if (clock>=9000 && clock<11200
            && npc.blockPosition().distSqr(npc.homePos())>9
            && level.hasChunkAt(npc.homePos())) {
            if(MovementPolicy.due(tick,npc.getId(),40))
                RoadNavigator.moveToward(npc,level,npc.homePos(),0.94);
            npc.setAction("office work finished; walking home across city streets");
            return true;
        }
        return false;
    }
}
