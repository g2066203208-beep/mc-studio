package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.SocietyRules;
import dev.reme.mcstudio.npc.life.TownEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** A real bell swing and a real note-block event; no synthetic audiovisual props. */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TownEventGameTests {
    private TownEventGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void eyewitness_town_alarm_rings_an_existing_vanilla_bell(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        BlockPos bellPos = helper.absolutePos(new BlockPos(5, 9, 5));
        world.setBlock(bellPos.below(), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(bellPos, Blocks.BELL.defaultBlockState(), 3);
        if (!world.getBlockState(bellPos).is(Blocks.BELL)
            || !(world.getBlockEntity(bellPos) instanceof BellBlockEntity))
            throw new AssertionError("No real bell and native block entity loaded");
        if (!TownEvents.ringAlarm(world, bellPos.east(2)))
            throw new AssertionError("Actual server did not ring existing village bell");

        helper.runAfterDelay(2, () -> {
            if (!(world.getBlockEntity(bellPos) instanceof BellBlockEntity bell)
                || !bell.shaking)
                helper.fail("Native village alarm did not animate a real BellBlockEntity", bellPos);
            else {
                world.setBlock(bellPos, Blocks.AIR.defaultBlockState(), 3);
                if (TownEvents.ringAlarm(world, bellPos.east(2)))
                    helper.fail("Alarm rang after player bell was physically removed", bellPos);
                else helper.succeed();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void weekly_neighborhood_musician_uses_existing_note_block_without_changes(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(5, 9, 5));
        BlockPos note = anchor.east(2);
        world.setBlock(note.below(), Blocks.STONE.defaultBlockState(), 3);
        world.setBlock(note, Blocks.NOTE_BLOCK.defaultBlockState(), 3);
        world.setBlock(note.above(), Blocks.AIR.defaultBlockState(), 3);
        PlayerNpcEntity host = NpcRegistry.PLAYER_NPC.get().create(world);
        PlayerNpcEntity guest = NpcRegistry.PLAYER_NPC.get().create(world);
        if (host == null || guest == null) throw new AssertionError("NPCs not registered");
        host.moveTo(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5, 0, 0);
        guest.moveTo(anchor.getX() + 1.5, anchor.getY(), anchor.getZ() + .5, 0, 0);
        host.setHomePos(anchor);
        host.setWorkPos(anchor);
        host.setProfession(PlayerNpcEntity.Profession.SHOPKEEPER);
        host.setMode(PlayerNpcEntity.Mode.LIVE);
        guest.setHomePos(anchor);
        guest.setMode(PlayerNpcEntity.Mode.LIVE);
        host.setIdentity(SocietyRules.civilian("music_neighborhood"));
        guest.setIdentity(SocietyRules.civilian("music_neighborhood"));
        if (!world.addFreshEntity(host) || !world.addFreshEntity(guest))
            throw new AssertionError("Cannot spawn musicians in real test server");
        var before = world.getBlockState(note);
        if (!TownEvents.playFairNote(guest, host, world)
            || !world.getBlockState(note).equals(before))
            throw new AssertionError("Fair failed real native note-block event or changed player's instrument");
        world.setBlock(note, Blocks.AIR.defaultBlockState(), 3);
        if (TownEvents.playFairNote(guest, host, world))
            throw new AssertionError("An absent note block made phantom music");
        helper.succeed();
    }
}
