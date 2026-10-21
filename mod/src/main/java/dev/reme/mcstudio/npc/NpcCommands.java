package dev.reme.mcstudio.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import dev.reme.mcstudio.npc.life.PlayerMiningActions;
import dev.reme.mcstudio.npc.life.DailyRoaming;
import dev.reme.mcstudio.npc.life.FootTrafficData;
import dev.reme.mcstudio.npc.life.EmergentSettlements;
import dev.reme.mcstudio.npc.life.SettlementLedger;
import dev.reme.mcstudio.npc.life.Commission;
import dev.reme.mcstudio.npc.life.CivicContracts;
import dev.reme.mcstudio.npc.life.CivicMind;
import dev.reme.mcstudio.npc.life.CharacterProfile;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.SocietyRules;
import dev.reme.mcstudio.npc.life.LifeJourney;
import dev.reme.mcstudio.npc.life.SafeBuilder;
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
        // Works with no OP privileges; confirms the MOD actually loaded.
        event.getDispatcher().register(Commands.literal("remecheck")
            .executes(ctx -> {
                boolean ready = ctx.getSource().getServer().getCommands()
                    .getDispatcher().getRoot().getChild("npc") != null;
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "REME NPC v0.5.7-dev active; /npc registered=" + ready
                    + ". If /npc spawn is denied, enable cheats or operator privileges."),
                    false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("npc")
            .executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "REME NPC v0.5.7-dev: /npc info, /npc spawn (cheats required), /npc demo (cheats required)"),
                    false);
                return 1;
            })
            .then(Commands.literal("spawn").requires(source -> source.hasPermission(2))
                .executes(ctx -> spawn(ctx.getSource(), "NPC"))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> spawn(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("demo").requires(source -> source.hasPermission(2))
                .executes(ctx -> demo(ctx.getSource())))
            .then(Commands.literal("radius")
                .then(Commands.argument("blocks", IntegerArgumentType.integer(16, 128))
                    .executes(ctx -> changeRadius(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "blocks"), false))))
            .then(Commands.literal("adminradius").requires(source -> source.hasPermission(2))
                .then(Commands.argument("blocks", IntegerArgumentType.integer(16, 128))
                    .executes(ctx -> changeRadius(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "blocks"), true))))
            .then(Commands.literal("live").executes(ctx -> changeMode(ctx.getSource(), PlayerNpcEntity.Mode.LIVE)))
            .then(Commands.literal("farmer").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.FARMER)))
            .then(Commands.literal("baker").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.BAKER)))
            .then(Commands.literal("resident").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.RESIDENT)))
            .then(Commands.literal("scout").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.SCOUT)))
            .then(Commands.literal("courier").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.COURIER)))
            .then(Commands.literal("guardjob").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.GUARD)))
            .then(Commands.literal("builder").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.BUILDER)))
            .then(Commands.literal("trails")
                .executes(ctx -> pathInfo(ctx.getSource()))
                .then(Commands.literal("on").requires(source -> source.hasPermission(2))
                    .executes(ctx -> setPaths(ctx.getSource(), true)))
                .then(Commands.literal("off").requires(source -> source.hasPermission(2))
                    .executes(ctx -> setPaths(ctx.getSource(), false))))
            .then(Commands.literal("settlement")
                .executes(ctx -> settlementInfo(ctx.getSource())))
            .then(Commands.literal("mine").requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", BlockPosArgument.blockPos())
                    .executes(ctx -> authorizeMining(ctx.getSource(),
                        BlockPosArgument.getLoadedBlockPos(ctx, "target")))))
            .then(Commands.literal("minestop").requires(source -> source.hasPermission(2))
                .executes(ctx -> cancelMining(ctx.getSource())))
            .then(Commands.literal("buildstart").requires(source -> source.hasPermission(2))
                .executes(ctx -> construction(ctx.getSource(), true)))
            .then(Commands.literal("buildstop").requires(source -> source.hasPermission(2))
                .executes(ctx -> construction(ctx.getSource(), false)))
            .then(Commands.literal("escort").executes(ctx -> escort(ctx.getSource(), true)))
            .then(Commands.literal("unescort").executes(ctx -> escort(ctx.getSource(), false)))
            .then(Commands.literal("squad").executes(ctx -> squad(ctx.getSource(), true)))
            .then(Commands.literal("disband").executes(ctx -> squad(ctx.getSource(), false)))
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
            .then(Commands.literal("skin")
                .then(Commands.argument("id", IntegerArgumentType.integer(0, 5))
                    .executes(ctx -> changeSkin(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "id")))))
            .then(Commands.literal("skins").executes(ctx -> listSkins(ctx.getSource())))
            .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
            .then(Commands.literal("law").executes(ctx -> civicInfo(ctx.getSource(), true)))
            .then(Commands.literal("news").executes(ctx -> civicInfo(ctx.getSource(), false)))
            .then(Commands.literal("job").executes(ctx -> jobInfo(ctx.getSource())))
            .then(Commands.literal("accept").executes(ctx -> acceptJob(ctx.getSource())))
            .then(Commands.literal("deliver").executes(ctx -> deliverJob(ctx.getSource())))
            .then(Commands.literal("canceljob").executes(ctx -> cancelJob(ctx.getSource())))
            .then(Commands.literal("commission")
                .then(Commands.argument("goods", StringArgumentType.word())
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                        .then(Commands.argument("reward", IntegerArgumentType.integer(1, 16))
                            .executes(ctx -> commission(ctx.getSource(),
                                StringArgumentType.getString(ctx, "goods"),
                                IntegerArgumentType.getInteger(ctx, "count"),
                                IntegerArgumentType.getInteger(ctx, "reward"))))))));
    }

    private static int pathInfo(CommandSourceStack source) {
        FootTrafficData traffic = FootTrafficData.get(source.getLevel());
        source.sendSuccess(() -> Component.literal(
            "Desire paths: " + traffic.trackedCells() + " recorded foot tiles; "
            + "physical path shaping " + (traffic.physicalPathsEnabled() ? "ON" : "OFF")
            + ". Every real passing resident strengthens familiar routes."
            + " Terrain wear requires OP /npc trails on and mobGriefing=true."), false);
        return 1;
    }

    private static int setPaths(CommandSourceStack source, boolean enabled) {
        FootTrafficData traffic = FootTrafficData.get(source.getLevel());
        traffic.setPhysicalPathsEnabled(enabled);
        source.sendSuccess(() -> Component.literal(
            "NPC physical trail formation " + (enabled ? "ENABLED" : "DISABLED")
            + ". Traffic still learns when off; only exposed natural grass/dirt"
            + " may become dirt_path, after repeated visits, through real player item use."
            + " Other-player buildings/crops are never chosen as trail surfaces."), true);
        return 1;
    }

    private static int settlementInfo(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity reference = closestNpc(player);
        if (reference == null) {
            source.sendFailure(Component.literal(
                "No resident within 16 blocks to census; visit a populated area"));
            return 0;
        }
        var community = EmergentSettlements.survey(player.serverLevel(), reference);
        if (community == null) {
            source.sendSuccess(() -> Component.literal(
                "No organic hamlet yet. At least 3 living allied residents"
                + " with distinct nearby homes are required; an identity label"
                + " alone does not build a village."), false);
            return 0;
        }
        SettlementLedger.get(player.serverLevel()).record(
            community.founder(), community.hub(), community.households(),
            player.serverLevel().getGameTime(), community.culture());
        source.sendSuccess(() -> Component.literal(
            "Organic " + community.tier() + " | " + community.households()
            + " real households | " + community.culture()
            + " | meeting hub " + community.hub().toShortString()
            + " | chronicle settlements: "
            + SettlementLedger.get(player.serverLevel()).knownSettlements()), false);
        return 1;
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
        npc.setSkinId(player.getRandom().nextInt(6));
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
            npc.setSkinId(i);
            npc.setCustomNameVisible(true);
            npc.setMode(PlayerNpcEntity.Mode.LIVE);
            npc.backpack().addItem(new ItemStack(Items.BREAD, 4));
            npc.setCharacter(CharacterProfile.fromSeed(i + 1));
            npc.setPersonality(new LivingPlanner.Personality(
                30 + i * 11, 80 - i * 9, 30 + i * 8));
            npc.setProfession(switch (i) {
                case 0, 1 -> PlayerNpcEntity.Profession.FARMER;
                case 2 -> PlayerNpcEntity.Profession.BAKER;
                case 3 -> PlayerNpcEntity.Profession.GUARD;
                case 4 -> PlayerNpcEntity.Profession.SCOUT;
                default -> PlayerNpcEntity.Profession.COURIER;
            });
            // Make the demonstration town emerge from THREE actual nearby
            // settler homes (including the baker), not from a hardcoded
            // village flag. Other residents retain separate affiliations.
            SocietyRules.Faction demoFaction = switch (i) {
                case 5 -> SocietyRules.Faction.MERCHANTS;
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


    /** An explicit operator-approved *one block* mining order, never an
     * unrestricted terrain-razing command for autonomous mobs.
     */
    private static int authorizeMining(CommandSourceStack source, BlockPos target)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) {
            source.sendFailure(Component.literal("No owned NPC within 16 blocks"));
            return 0;
        }
        if (!player.serverLevel().hasChunkAt(target) ||
            player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 256 ||
            !DailyRoaming.inRange(npc, target) ||
            !PlayerMiningActions.allowed(player.serverLevel().getBlockState(target))) {
            source.sendFailure(Component.literal(
                "Mining order rejected: loaded target must be an allowed natural stone/ore within 16 blocks and home territory"));
            return 0;
        }
        var tool = npc.getMainHandItem();
        var state = player.serverLevel().getBlockState(target);
        if (tool.isEmpty() ||
            (state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state))) {
            source.sendFailure(Component.literal(
                "Give NPC a proper pickaxe: sneak-right-click the NPC with your pickaxe"));
            return 0;
        }
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        npc.setMiningTarget(target);
        source.sendSuccess(() -> Component.literal(
            "Authorized ONE real player-style mining task for " + npc.getName().getString()
                + ": " + target.toShortString() + " (no instant destroy)"), false);
        return 1;
    }

    private static int cancelMining(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) return 0;
        if (npc.miningTarget() != null)
            source.getLevel().destroyBlockProgress(npc.getId(), npc.miningTarget(), -1);
        npc.clearMiningTarget();
        npc.setAction("mining order cancelled by owner");
        source.sendSuccess(() -> Component.literal("Stopped NPC mining"), false);
        return 1;
    }

    private static int construction(CommandSourceStack source, boolean start)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) return 0;
        if (!start) {
            npc.setBuildEnabled(false);
            source.sendSuccess(() -> Component.literal("Construction paused"), false);
            return 1;
        }
        if (!SafeBuilder.validateSite(player.serverLevel(), npc.workPos())) {
            source.sendFailure(Component.literal("Build site must be loaded and empty: 5x5 x 3, no entities inside"));
            return 0;
        }
        npc.setProfession(PlayerNpcEntity.Profession.BUILDER);
        npc.resetBuildCursor();
        npc.setBuildEnabled(true);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        source.sendSuccess(() -> Component.literal("Started consented 5x5 shelter build at " +
            npc.workPos().toShortString() + "; uses actual cobblestone/planks, cannot clear blocks"), true);
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

    private static int squad(CommandSourceStack source, boolean assemble)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var nearby = player.level().getEntitiesOfClass(PlayerNpcEntity.class,
            player.getBoundingBox().inflate(16), npc -> npc.isOwnedBy(player) &&
            (npc.profession() == PlayerNpcEntity.Profession.GUARD ||
             npc.profession() == PlayerNpcEntity.Profession.SCOUT));
        nearby.sort(Comparator.comparingDouble(player::distanceToSqr));
        int number = 0;
        for (PlayerNpcEntity npc : nearby) {
            if (number >= 9) break;
            npc.setEscortLeader(assemble ? player.getUUID() : null);
            npc.setFormationSlot(number);
            if (assemble) npc.setMode(PlayerNpcEntity.Mode.LIVE);
            number++;
        }
        int total = number;
        source.sendSuccess(() -> Component.literal((assemble ?
            "Squad assembled: " : "Squad disbanded: ") + total +
            " guards/scouts in 16-block radius"), false);
        return number;
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

    private static int changeRadius(CommandSourceStack source, int radius, boolean admin)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = admin ? closestNpc(player) : closestOwnedNpc(player);
        if (npc == null) return 0;
        npc.setRoamingRadius(radius);
        source.sendSuccess(() -> Component.literal(npc.getName().getString()
            + " home range -> " + npc.roamingRadius() + " blocks"), false);
        return 1;
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
            source.sendFailure(Component.literal("No resident within 16 blocks"));
            return 0;
        }
        npc.setMode(mode);
        source.sendSuccess(() -> Component.literal(npc.getDisplayName().getString() + " -> " + mode.name()), false);
        return 1;
    }


    private static PlayerNpcEntity closestNpc(ServerPlayer player) {
        return player.level().getEntitiesOfClass(PlayerNpcEntity.class,
            player.getBoundingBox().inflate(16), PlayerNpcEntity::isAlive).stream()
            .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }

    private static int civicInfo(CommandSourceStack source, boolean law)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestNpc(player);
        if (npc == null) {
            source.sendFailure(Component.literal("No resident in range"));
            return 0;
        }
        if (law) {
            source.sendSuccess(() -> Component.literal(npc.getName().getString()
                + ": personal reputation of you " + npc.civic().reputation(player.getUUID())
                + " (" + npc.civic().standing(player.getUUID(), npc.character().justice()) + ")"
                + " | honesty " + npc.character().honesty()
                + " justice " + npc.character().justice()
                + " empathy " + npc.character().empathy()
                + " curiosity " + npc.character().curiosity()), false);
        } else {
            int direct = 0, rumor = 0;
            for (CivicMind.Report report : npc.civic().reports()) {
                if (report.direct()) direct++; else rumor++;
            }
            final int a = direct, b = rumor;
            var reports = npc.civic().reports();
            String latest = reports.isEmpty() ? "none"
                : reports.get(reports.size() - 1).kind()
                    + " concerning " + reports.get(reports.size() - 1).actor()
                    + (reports.get(reports.size() - 1).direct()
                        ? " (eyewitness)" : " (hearsay, unverified)");
            source.sendSuccess(() -> Component.literal(npc.getName().getString()
                + " knows " + a + " witnessed events and " + b
                + " second-hand reports; latest: " + latest), false);
        }
        return 1;
    }

    private static int commission(CommandSourceStack source, String goods,
                                  int count, int reward)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) {
            source.sendFailure(Component.literal("No owned NPC in range"));
            return 0;
        }
        try {
            Commission.Goods type = Commission.Goods.valueOf(goods.toUpperCase(Locale.ROOT));
            if (!CivicContracts.post(npc, type, count, reward)) {
                source.sendFailure(Component.literal("Active job exists or NPC lacks real emerald rewards"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(npc.getName().getString()
                + " posted funded contract: deliver " + count + " " + type
                + " for " + reward + " reserved emeralds"), false);
            return 1;
        } catch (IllegalArgumentException bad) {
            source.sendFailure(Component.literal("Supported goods: WHEAT, BREAD, CARROT, POTATO"));
            return 0;
        }
    }

    private static int jobInfo(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestNpc(source.getPlayerOrException());
        if (npc == null) {
            source.sendFailure(Component.literal("No resident in range"));
            return 0;
        }
        Commission job = npc.commission();
        source.sendSuccess(() -> Component.literal(npc.getName().getString()
            + (job == null ? " offers no commission"
            : " job " + job.state() + ": " + job.quantity() + " " + job.goods()
                + " for " + job.emeraldReward() + " reserved emeralds")), false);
        return 1;
    }

    private static int acceptJob(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestNpc(player);
        if (npc == null || !CivicContracts.accept(npc, player)) {
            source.sendFailure(Component.literal("No open commission here (or your reputation is too poor)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Accepted job from " + npc.getName().getString()
            + ". Use /npc deliver near this NPC with the correct goods in your main hand."), false);
        return 1;
    }

    private static int deliverJob(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestNpc(player);
        if (npc == null || !CivicContracts.deliver(npc, player)) {
            source.sendFailure(Component.literal("Delivery failed: wrong owner/items, NPC bag full or no free inventory slot"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Delivered real goods and received reserved emerald reward"), false);
        return 1;
    }

    private static int cancelJob(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null || !CivicContracts.cancel(npc)) {
            source.sendFailure(Component.literal("Unable to cancel; not yours, no active job or no backpack room for refund"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Job cancelled; escrow returned to NPC backpack"), false);
        return 1;
    }


    private static int changeSkin(CommandSourceStack source, int id)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) {
            source.sendFailure(Component.literal("No NPC owned by you within 16 blocks"));
            return 0;
        }
        npc.setSkinId(id);
        source.sendSuccess(() -> Component.literal(
            npc.getName().getString() + ": switched to skin " + id +
            ". Nearby clients see the update immediately."), false);
        return 1;
    }

    private static int listSkins(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "NPC skins (0-5): 0 Green Farmer, 1 Field Explorer, 2 Formal Trader, " +
            "3 Blue Farmer, 4 Teal Explorer, 5 Brown Trader. Use /npc skin <id>"), false);
        return 1;
    }

    private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // Anyone may inspect the closest live resident; ownership is needed
        // only to issue commands that actually mutate that resident.
        PlayerNpcEntity npc = closestNpc(player);
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
            + " | skin " + npc.skinId()
            + " | roam " + npc.roamingRadius()
            + " | natural " + npc.naturalResident()
            + " | civics: " + npc.civic().reports().size() + " reports"
            + " | personal reputation " + npc.civic().reputation(player.getUUID())
            + " | commission " + (npc.commission() == null ? "none" : npc.commission().state())
            + " | backpack " + used + "/36 slots"), false);
        return 1;
    }
}
