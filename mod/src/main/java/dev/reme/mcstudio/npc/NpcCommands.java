package dev.reme.mcstudio.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.SocietyRules;
import dev.reme.mcstudio.npc.life.LifeJourney;
import java.util.Locale;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            .then(Commands.literal("baker").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.BAKER)))
            .then(Commands.literal("resident").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.RESIDENT)))
            .then(Commands.literal("scout").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.SCOUT)))
            .then(Commands.literal("courier").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.COURIER)))
            .then(Commands.literal("guardjob").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.GUARD)))
            .then(Commands.literal("escort").executes(ctx -> escort(ctx.getSource(), true)))
            .then(Commands.literal("unescort").executes(ctx -> escort(ctx.getSource(), false)))
            .then(Commands.literal("setstore").requires(source -> source.hasPermission(2))
                .executes(ctx -> setStore(ctx.getSource())))
            .then(Commands.literal("nation").requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> setIdentity(ctx.getSource(), "nation",
                        StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("faction").requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> setIdentity(ctx.getSource(), "faction",
                        StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("region").requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> setIdentity(ctx.getSource(), "region",
                        StringArgumentType.getString(ctx, "id")))))
            .then(Commands.literal("ambition")
                .then(Commands.argument("id", StringArgumentType.word())
                    .executes(ctx -> ambition(ctx.getSource(),
                        StringArgumentType.getString(ctx, "id")))))
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
            npc.backpack().addItem(new ItemStack(Items.BREAD, 4));
            npc.setPersonality(new LivingPlanner.Personality(
                30 + i * 11, 80 - i * 9, 30 + i * 8));
            npc.setProfession(switch (i) {
                case 0, 1 -> PlayerNpcEntity.Profession.FARMER;
                case 2 -> PlayerNpcEntity.Profession.BAKER;
                case 3 -> PlayerNpcEntity.Profession.GUARD;
                case 4 -> PlayerNpcEntity.Profession.SCOUT;
                default -> PlayerNpcEntity.Profession.COURIER;
            });
            SocietyRules.Faction demoFaction = switch (i) {
                case 2, 5 -> SocietyRules.Faction.MERCHANTS;
                case 3, 4 -> SocietyRules.Faction.RANGERS;
                default -> SocietyRules.Faction.SETTLERS;
            };
            npc.setIdentity(new SocietyRules.Identity(SocietyRules.Nation.COMMONWEALTH,
                demoFaction, "river"));
            npc.journey().setGoal(switch (i) {
                case 0, 1 -> LifeJourney.Goal.FEED_COMMUNITY;
                case 2, 5 -> LifeJourney.Goal.ESTABLISH_TRADE;
                case 3 -> LifeJourney.Goal.DEFEND_SETTLEMENT;
                default -> LifeJourney.Goal.EXPLORE_LANDS;
            });
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
            " autonomous residents (2 farmers, baker, guard, scout, courier). Plant wheat and put a crafting table near the bakery anchor; place mature crops near center to test real harvesting. No terrain was changed."), true);
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


    private static int setStore(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) {
            source.sendFailure(Component.literal("No owned NPC within 16 blocks"));
            return 0;
        }
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult block) ||
            !(player.serverLevel().getBlockEntity(block.getBlockPos()) instanceof ChestBlockEntity)) {
            source.sendFailure(Component.literal("Look at a loaded vanilla chest within 6 blocks"));
            return 0;
        }
        npc.setSupplyPos(block.getBlockPos());
        source.sendSuccess(() -> Component.literal("Assigned chest to " + npc.getName().getString() +
            ": " + block.getBlockPos().toShortString() + " (operator opt-in)"), false);
        return 1;
    }

    private static int escort(CommandSourceStack source, boolean enabled)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) return 0;
        npc.setEscortLeader(enabled ? player.getUUID() : null);
        if (enabled) {
            npc.setProfession(PlayerNpcEntity.Profession.GUARD);
            npc.setMode(PlayerNpcEntity.Mode.LIVE);
        }
        source.sendSuccess(() -> Component.literal(npc.getName().getString()
            + (enabled ? " escorting you" : " escort ended")), false);
        return 1;
    }

    private static int setIdentity(CommandSourceStack source, String kind, String input)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) return 0;
        SocietyRules.Identity before = npc.identity();
        try {
            String name = input.toUpperCase(Locale.ROOT);
            SocietyRules.Identity next = switch (kind) {
                case "nation" -> new SocietyRules.Identity(
                    SocietyRules.Nation.valueOf(name), before.faction(), before.region());
                case "faction" -> new SocietyRules.Identity(
                    before.nation(), SocietyRules.Faction.valueOf(name), before.region());
                case "region" -> new SocietyRules.Identity(
                    before.nation(), before.faction(), input.toLowerCase(Locale.ROOT));
                default -> before;
            };
            npc.setIdentity(next);
            source.sendSuccess(() -> Component.literal(npc.getName().getString() +
                " society: " + next), false);
            return 1;
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal("Invalid affiliation: " + input +
                " (nations COMMONWEALTH/FRONTIER/OUTLANDS; factions SETTLERS/MERCHANTS/RANGERS/RAIDERS)"));
            return 0;
        }
    }

    private static int ambition(CommandSourceStack source, String input)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) return 0;
        try {
            LifeJourney.Goal goal = LifeJourney.Goal.valueOf(input.toUpperCase(Locale.ROOT));
            npc.journey().setGoal(goal);
            source.sendSuccess(() -> Component.literal(npc.getName().getString()
                + " now aspires to " + goal), false);
            return 1;
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal("Invalid life goal. Use FEED_COMMUNITY, ESTABLISH_TRADE, EXPLORE_LANDS, DEFEND_SETTLEMENT, BUILD_A_HOME or LIVE_PEACEFULLY"));
            return 0;
        }
    }

    private static PlayerNpcEntity closestOwnedNpc(ServerPlayer player) {
        AABB range = player.getBoundingBox().inflate(16);
        return player.level().getEntitiesOfClass(PlayerNpcEntity.class, range, npc -> npc.isOwnedBy(player))
            .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static int changeMode(CommandSourceStack source, PlayerNpcEntity.Mode mode) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) {
            source.sendFailure(Component.literal("No NPC owned by you within 16 blocks"));
            return 0;
        }
        npc.setMode(mode);
        source.sendSuccess(() -> Component.literal(npc.getDisplayName().getString() + " -> " + mode.name()), false);
        return 1;
    }

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
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
            + " | trust " + npc.memory().trust(player.getUUID())
            + " | " + npc.identity().nation() + "/" + npc.identity().faction()
            + "@" + npc.identity().region() + " | dream " + npc.journey().goal()
            + " " + npc.journey().progress() + "%"
            + " | supply " + (npc.supplyPos() == null ? "none" : npc.supplyPos().toShortString())
            + " | delivered " + npc.parcelsDelivered()
            + " | backpack " + used + "/36 slots"), false);
        return 1;
    }
}
