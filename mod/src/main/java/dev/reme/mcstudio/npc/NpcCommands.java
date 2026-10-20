package dev.reme.mcstudio.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Comparator;

public final class NpcCommands {
    private NpcCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("npc")
            .then(Commands.literal("spawn").requires(source -> source.hasPermission(2))
                .executes(ctx -> spawn(ctx.getSource(), "NPC"))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> spawn(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("demo").requires(source -> source.hasPermission(2))
                .executes(ctx -> demo(ctx.getSource())))
            .then(Commands.literal("live").executes(ctx -> changeMode(ctx.getSource(), PlayerNpcEntity.Mode.LIVE)))
            .then(Commands.literal("farmer").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.FARMER)))
            .then(Commands.literal("resident").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.RESIDENT)))
            .then(Commands.literal("sethome").executes(ctx -> anchor(ctx.getSource(), true)))
            .then(Commands.literal("setwork").executes(ctx -> anchor(ctx.getSource(), false)))
            .then(Commands.literal("follow").executes(ctx -> changeMode(ctx.getSource(), PlayerNpcEntity.Mode.FOLLOW)))
            .then(Commands.literal("stay").executes(ctx -> changeMode(ctx.getSource(), PlayerNpcEntity.Mode.STAY)))
            .then(Commands.literal("wander").executes(ctx -> changeMode(ctx.getSource(), PlayerNpcEntity.Mode.WANDER)))
            .then(Commands.literal("guard").executes(ctx -> changeMode(ctx.getSource(), PlayerNpcEntity.Mode.GUARD)))
            .then(Commands.literal("info").executes(ctx -> info(ctx.getSource()))));
    }

    private static int spawn(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(player.serverLevel());
        if (npc == null) {
            source.sendFailure(Component.literal("NPC spawn failed"));
            return 0;
        }
        npc.setOwner(player);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setHomePos(player.blockPosition().offset(1, 0, 1));
        npc.setWorkPos(player.blockPosition());
        npc.setCustomName(Component.literal(name.substring(0, Math.min(48, name.length()))));
        npc.setCustomNameVisible(true);
        npc.moveTo(player.getX() + 1.5, player.getY(), player.getZ() + 1.5, player.getYRot(), 0);
        if (!player.serverLevel().addFreshEntity(npc)) {
            source.sendFailure(Component.literal("NPC could not enter the world"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Created player NPC: " + name), true);
        return 1;
    }


    private static int demo(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String[] names = {"阿禾", "秋田", "南枝", "小溪", "谷雨", "阿岚"};
        int created = 0;
        for (int i = 0; i < names.length; i++) {
            PlayerNpcEntity npc = NpcRegistry.PLAYER_NPC.get().create(player.serverLevel());
            if (npc == null) continue;
            npc.setOwner(player);
            npc.setCustomName(Component.literal(names[i]));
            npc.setCustomNameVisible(true);
            npc.setMode(PlayerNpcEntity.Mode.LIVE);
            npc.setProfession(i < 2
                ? PlayerNpcEntity.Profession.FARMER
                : PlayerNpcEntity.Profession.RESIDENT);
            int dx = (i % 3) * 3 - 3;
            int dz = (i / 3) * 3 + 3;
            BlockPos spawn = player.blockPosition().offset(dx, 0, dz);
            npc.moveTo(spawn.getX() + 0.5, spawn.getY(),
                spawn.getZ() + 0.5, 0, 0);
            npc.setHomePos(spawn);
            npc.setWorkPos(player.blockPosition());
            if (player.serverLevel().addFreshEntity(npc)) created++;
        }
        final int count = created;
        source.sendSuccess(() -> Component.literal("Created " + count +
            " autonomous residents (2 farmers). Place mature crops near center to test real harvesting. No terrain was changed."), true);
        return created;
    }

    private static int profession(CommandSourceStack source, PlayerNpcEntity.Profession job)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) {
            source.sendFailure(Component.literal("No owned resident within 16 blocks"));
            return 0;
        }
        npc.setProfession(job);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        source.sendSuccess(() -> Component.literal(npc.getName().getString()
            + " profession: " + job), false);
        return 1;
    }

    private static int anchor(CommandSourceStack source, boolean home)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) {
            source.sendFailure(Component.literal("No owned resident within 16 blocks"));
            return 0;
        }
        if (home) npc.setHomePos(player.blockPosition());
        else npc.setWorkPos(player.blockPosition());
        source.sendSuccess(() -> Component.literal(npc.getName().getString()
            + (home ? " home" : " workplace")
            + " = " + player.blockPosition().toShortString()), false);
        return 1;
    }

    private static PlayerNpcEntity closestOwnedNpc(ServerPlayer player) {
        AABB range = player.getBoundingBox().inflate(16);
        return player.level().getEntitiesOfClass(PlayerNpcEntity.class, range, npc -> npc.isOwnedBy(player))
            .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static int changeMode(CommandSourceStack source, PlayerNpcEntity.Mode mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) {
            source.sendFailure(Component.literal("No NPC owned by you within 16 blocks"));
            return 0;
        }
        npc.setMode(mode);
        source.sendSuccess(() -> Component.literal(npc.getDisplayName().getString() + " -> " + mode.name()), false);
        return 1;
    }

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) {
            source.sendFailure(Component.literal("No NPC owned by you within 16 blocks"));
            return 0;
        }
        int occupied = 0;
        for (int i = 0; i < npc.backpack().getContainerSize(); i++) {
            if (!npc.backpack().getItem(i).isEmpty()) occupied++;
        }
        final int used = occupied;
        source.sendSuccess(() -> Component.literal(npc.getDisplayName().getString() +
            " | " + npc.mode().name() + " | HP " + Math.round(npc.getHealth()) +
            " | hunger " + npc.hunger() + " energy " + npc.energy()
            + " social " + npc.socialNeed() + " | " + npc.profession()
            + " | intent " + npc.intent() + " (" + npc.intentReason() + ")"
            + " | action " + npc.lastAction()
            + " | trust " + npc.memory().trust(source.getPlayerOrException().getUUID())
            + " | backpack " + used + "/36 slots"), false);
        return 1;
    }
}
