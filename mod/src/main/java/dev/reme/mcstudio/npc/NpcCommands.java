package dev.reme.mcstudio.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
            " | backpack " + used + "/36 slots"), false);
        return 1;
    }
}
