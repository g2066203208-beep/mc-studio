package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.SocietyRules;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * OPT-IN real-wall-clock population soak, isolated from the default fast
 * GameTest suite. CI sets reme.npc.soak=true explicitly. This is a real MC
 * server ticking real entities, not a loop that manually calls NPC brains.
 *
 * We measure elapsed System.nanoTime AND server tick history. The normal
 * fast GameTest runner deliberately accelerates world ticks, so "2400 ticks"
 * alone is not proof of two minutes of actual gameplay.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcSoakGameTests {
    private NpcSoakGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 3000000)
    public static void real_wall_clock_loaded_npc_population_soak(GameTestHelper helper) {
        if (!Boolean.getBoolean("reme.npc.soak")) {
            // Ordinary pull requests must not pay for this optional,
            // real-wall-clock soak. The separate CI matrix opts in.
            helper.succeed();
            return;
        }

        int population = Math.clamp(Integer.getInteger("reme.npc.soak.population", 50), 1, 200);
        int minSeconds = Math.clamp(Integer.getInteger("reme.npc.soak.seconds", 300), 30, 900);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos corner = helper.absolutePos(new BlockPos(2, 9, 2));
        for (int x = 0; x < 44; x++) for (int z = 0; z < 44; z++) {
            BlockPos floor = corner.offset(x, -1, z);
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        }

        PlayerNpcEntity[] citizens = new PlayerNpcEntity[population];
        HashSet<UUID> unique = new HashSet<>();
        for (int i = 0; i < population; i++) {
            PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(level);
            if (npc == null) throw new AssertionError("No real 1.21.1 NeoForge resident entity");
            BlockPos home = corner.offset(2 + 2 * (i % 20), 0, 2 + 2 * (i / 20));
            npc.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0, 0);
            npc.setHomePos(home);
            npc.setWorkPos(home);
            npc.setIdentity(SocietyRules.civilian("soak_town"));
            npc.setRoamingRadius(16);
            npc.setMode(PlayerNpcEntity.Mode.LIVE);
            npc.setProfession(i % 8 == 0
                ? PlayerNpcEntity.Profession.GUARD
                : PlayerNpcEntity.Profession.RESIDENT);
            npc.backpack().addItem(new ItemStack(Items.BREAD, 4));
            if (!level.addFreshEntity(npc) || !unique.add(npc.getUUID()))
                throw new AssertionError("Spawn failed or colliding resident UUID");
            citizens[i] = npc;
        }

        final long startTime = System.nanoTime();
        final long startWorldTick = level.getGameTime();
        final long minimumNanoseconds = minSeconds * 1_000_000_000L;
        final long[] samples = new long[8192]; // 64 KiB maximum bounded telemetry
        final long[] recorded = {0L};
        final long[] lastPrint = {startTime};

        System.out.println("REME_NPC_SOAK_BEGIN residents=" + population
            + " minRealWallSeconds=" + minSeconds
            + " initialServerTick=" + server.getTickCount());

        helper.onEachTick(() -> {
            long ageTicks = level.getGameTime() - startWorldTick;
            long now = System.nanoTime();
            // Native server tick history (rather than an arbitrary AI step
            // microbenchmark); sample 1 in 20 ticks for bounded memory.
            if (ageTicks % 20L == 0L) {
                long[] history = server.getTickTimesNanos();
                if (history.length > 0) {
                    long ns = history[Math.floorMod(server.getTickCount() - 1, history.length)];
                    if (ns > 0L) {
                        samples[(int) (recorded[0] % samples.length)] = ns;
                        recorded[0]++;
                    }
                }
            }
            if (now - lastPrint[0] >= 60_000_000_000L) {
                lastPrint[0] = now;
                System.out.println("REME_NPC_SOAK_PROGRESS population=" + population
                    + " realSeconds=" + ((now - startTime) / 1_000_000_000L)
                    + " genuineWorldTicks=" + ageTicks);
            }
            // Require BOTH genuine Minecraft world ticking and elapsed wall
            // clock time. No artificial Thread.sleep on the server thread.
            if (now - startTime < minimumNanoseconds || ageTicks < 12000L) return;

            int alive = 0, progressed = 0;
            HashSet<UUID> liveIds = new HashSet<>();
            for (PlayerNpcEntity npc : citizens) {
                if (npc.isAlive() && !npc.isRemoved()) alive++;
                if (npc.hunger() != 90 || npc.energy() != 85) progressed++;
                if (!liveIds.add(npc.getUUID()))
                    helper.fail("Duplicate UUID in soaked world", corner);
                if (npc.memory().relationshipSnapshot().size() > 128)
                    helper.fail("Resident trust map grew past 128 despite bounded implementation", corner);
            }
            int sampleSize = (int) Math.min(recorded[0], samples.length);
            long[] ordered = Arrays.copyOf(samples, sampleSize);
            Arrays.sort(ordered);
            double p50 = sampleSize == 0 ? -1.0
                : ordered[(sampleSize - 1) / 2] / 1_000_000.0;
            double p95 = sampleSize == 0 ? -1.0
                : ordered[Math.max(0, (int) Math.ceil(sampleSize * 0.95) - 1)] / 1_000_000.0;
            System.out.println(String.format(Locale.ROOT,
                "REME_NPC_SOAK_RESULT population=%d realWallSeconds=%.1f gameTicks=%d"
                    + " survivors=%d needsProgress=%d samples=%d sampledTickP50Ms=%.3f"
                    + " sampledTickP95Ms=%.3f (sampled native server tick-history,"
                    + " no networked players or user's 222-mod pack)",
                population, (now - startTime) / 1e9, ageTicks,
                alive, progressed, sampleSize, p50, p95));
            if (alive != population || progressed != population || sampleSize < 50)
                helper.fail("Soak survivors, life progress or native tick samples failed", corner);
            else helper.succeed();
        });
    }
}
