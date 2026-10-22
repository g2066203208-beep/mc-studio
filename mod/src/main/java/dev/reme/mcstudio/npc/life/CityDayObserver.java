package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;

/**
 * Inspect REAL 24,000 native Minecraft ticks of a physical city.
 * Per-citizen samples, deliberately only 20 per day (every 1200 ticks):
 * no world/global scans, no background polling, no TPS-intensive HUD packets.
 * Persistent NBT tags anchor time to the instant the live 3D city was built.
 */
public final class CityDayObserver {
    private CityDayObserver() {}
    public static final String CITY_TAG = "reme_city_citizen";
    private static final String START = "RemeRealCityStartTick";
    private static final String FINISHED = "RemeRealCityDayCompleted";

    public static void markSpawn(PlayerNpcEntity npc, long tick) {
        npc.addTag(CITY_TAG);
        npc.getPersistentData().putLong(START, tick);
        npc.getPersistentData().putBoolean(FINISHED, false);
    }

    public static void observe(PlayerNpcEntity npc, ServerLevel world, long tick) {
        if (!npc.getTags().contains(CITY_TAG)) return;
        long start = npc.getPersistentData().getLong(START);
        long age = tick - start;
        if (age < 0) return;
        // Per-entity, sparse O(1) instrumentation with real server tick time.
        if (age < 24000L && age % 1200L == 0L) {
            var p = npc.blockPosition();
            String activity = npc.lastAction().replaceAll("[\\r\\n]", " ");
            if (activity.length() > 96) activity = activity.substring(0,96);
            System.out.println("REME_NPC_CITY_DAY stage=sample"
                + " age=" + age + " tick=" + tick
                + " uuid=" + npc.getUUID()
                + " name=" + npc.getName().getString()
                + " role=" + npc.profession()
                + " intent=" + npc.intent()
                + " action=" + activity
                + " x=" + p.getX() + " y=" + p.getY() + " z=" + p.getZ()
                + " homeSq=" + p.distSqr(npc.homePos())
                + " workSq=" + p.distSqr(npc.workPos())
                + " sleeping=" + npc.isSleeping()
                + " hunger=" + npc.hunger()
                + " energy=" + npc.energy()
                + " emerald=" + npc.backpack().countItem(Items.EMERALD)
                + " bread=" + npc.backpack().countItem(Items.BREAD)
                + " detained=" + npc.isDetained(tick));
        }
        if (age >= 24000L && !npc.getPersistentData().getBoolean(FINISHED)) {
            npc.getPersistentData().putBoolean(FINISHED, true);
            System.out.println("REME_NPC_CITY_DAY stage=full_day_complete"
                + " uuid=" + npc.getUUID()
                + " role=" + npc.profession()
                + " age=" + age
                + " alive=" + npc.isAlive()
                + " x=" + npc.blockPosition().getX()
                + " z=" + npc.blockPosition().getZ());
        }
    }
}
