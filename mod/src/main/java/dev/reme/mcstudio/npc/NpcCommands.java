package dev.reme.mcstudio.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import dev.reme.mcstudio.npc.life.PlayerMiningActions;
import dev.reme.mcstudio.npc.life.DailyRoaming;
import dev.reme.mcstudio.npc.life.PublicSafety;
import dev.reme.mcstudio.npc.life.CivicWorkplaces;
import dev.reme.mcstudio.npc.life.ShopCounter;
import dev.reme.mcstudio.npc.life.ResidentBedLife;
import dev.reme.mcstudio.npc.life.NeighborhoodMarket;
import dev.reme.mcstudio.npc.life.ConvenienceStoreCrime;
import dev.reme.mcstudio.npc.life.CityDistrictDemo;
import dev.reme.mcstudio.npc.life.CityDistrictExpansion;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
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
                    "REME NPC " + dev.reme.mcstudio.StudioMod.VERSION + " 已加载; /npc registered=" + ready
                    + ". If /npc spawn is denied, enable cheats or operator privileges."),
                    false);
                return 1;
            }));
        event.getDispatcher().register(Commands.literal("npc")
            .executes(ctx -> {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "REME NPC " + dev.reme.mcstudio.StudioMod.VERSION
                        + "：/npc market 查看真实市集、/npc info 查看生活、/npc bed 查看床位。/npc spawn 和 /npc demo 需要作弊权限；也可使用生成蛋。"),
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
            .then(Commands.literal("shopkeeper").executes(ctx -> profession(ctx.getSource(), PlayerNpcEntity.Profession.SHOPKEEPER)))
            .then(Commands.literal("workplace").executes(ctx -> describeWorkplace(ctx.getSource())))
            .then(Commands.literal("bed")
                .executes(ctx -> bedStatus(ctx.getSource()))
                .then(Commands.literal("assign")
                    .executes(ctx -> assignBed(ctx.getSource())))
                .then(Commands.literal("clear")
                    .executes(ctx -> clearBed(ctx.getSource()))))
            .then(Commands.literal("shopstock")
                .executes(ctx -> shopStockStatus(ctx.getSource()))
                .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                    .executes(ctx -> assignShopStock(ctx.getSource())))
                .then(Commands.literal("clear").requires(source -> source.hasPermission(2))
                    .executes(ctx -> clearShopStock(ctx.getSource()))))
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
            .then(Commands.literal("market")
                .executes(ctx -> marketStatus(ctx.getSource())))
            .then(Commands.literal("camera")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> npcCameraStatus(ctx.getSource()))
                .then(Commands.literal("stop").executes(ctx -> npcCameraStop(ctx.getSource())))
                .then(Commands.literal("pov")
                    .then(Commands.argument("role", StringArgumentType.word())
                        .executes(ctx -> npcCameraStart(ctx.getSource(),
                            StringArgumentType.getString(ctx,"role"), NpcCameraController.Mode.POV))))
                .then(Commands.literal("chase")
                    .then(Commands.argument("role", StringArgumentType.word())
                        .executes(ctx -> npcCameraStart(ctx.getSource(),
                            StringArgumentType.getString(ctx,"role"), NpcCameraController.Mode.CHASE)))))
            .then(Commands.literal("city")
                .executes(ctx -> cityHelp(ctx.getSource()))
                .then(Commands.literal("survey")
                    .executes(ctx -> citySurvey(ctx.getSource())))
                .then(Commands.literal("census")
                    .executes(ctx -> cityCensus(ctx.getSource())))
                .then(Commands.literal("audit")
                    .executes(ctx -> cityLiveAudit(ctx.getSource())))
                .then(Commands.literal("demo")
                    .requires(source -> source.hasPermission(2))
                    .executes(ctx -> cityDemo(ctx.getSource())))
                .then(Commands.literal("grow")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("east")
                        .executes(ctx -> cityGrow(ctx.getSource(), net.minecraft.core.Direction.EAST)))
                    .then(Commands.literal("west")
                        .executes(ctx -> cityGrow(ctx.getSource(), net.minecraft.core.Direction.WEST)))
                    .then(Commands.literal("north")
                        .executes(ctx -> cityGrow(ctx.getSource(), net.minecraft.core.Direction.NORTH)))
                    .then(Commands.literal("south")
                        .executes(ctx -> cityGrow(ctx.getSource(), net.minecraft.core.Direction.SOUTH)))))
            .then(Commands.literal("mine").requires(source -> source.hasPermission(2))
                .then(Commands.argument("target", BlockPosArgument.blockPos())
                    .executes(ctx -> authorizeMining(ctx.getSource(),
                        BlockPosArgument.getLoadedBlockPos(ctx, "target")))))
            .then(Commands.literal("minestop").requires(source -> source.hasPermission(2))
                .executes(ctx -> cancelMining(ctx.getSource())))
            .then(Commands.literal("buildplan").requires(source -> source.hasPermission(2))
                .then(Commands.argument("type", StringArgumentType.word())
                    .executes(ctx -> setBlueprint(ctx.getSource(),
                        StringArgumentType.getString(ctx, "type")))))
            .then(Commands.literal("buildstart").requires(source -> source.hasPermission(2))
                .executes(ctx -> construction(ctx.getSource(), true)))
            .then(Commands.literal("buildstop").requires(source -> source.hasPermission(2))
                .executes(ctx -> construction(ctx.getSource(), false)))
            .then(Commands.literal("escort").executes(ctx -> escort(ctx.getSource(), true)))
            .then(Commands.literal("unescort").executes(ctx -> escort(ctx.getSource(), false)))
            .then(Commands.literal("squad").executes(ctx -> squad(ctx.getSource(), true)))
            .then(Commands.literal("disband").executes(ctx -> squad(ctx.getSource(), false)))
            .then(Commands.literal("jail")
                .executes(ctx -> jailStatus(ctx.getSource()))
                .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                    .executes(ctx -> assignJail(ctx.getSource())))
                .then(Commands.literal("clear").requires(source -> source.hasPermission(2))
                    .executes(ctx -> clearJail(ctx.getSource()))))
            .then(Commands.literal("setstore").requires(source -> source.hasPermission(2))
                .executes(ctx -> setStore(ctx.getSource())))
            .then(Commands.literal("route")
                .executes(ctx -> routeStatus(ctx.getSource()))
                .then(Commands.literal("pickup").requires(source -> source.hasPermission(2))
                    .executes(ctx -> setRouteDepot(ctx.getSource(), true)))
                .then(Commands.literal("dropoff").requires(source -> source.hasPermission(2))
                    .executes(ctx -> setRouteDepot(ctx.getSource(), false)))
                .then(Commands.literal("clear").requires(source -> source.hasPermission(2))
                    .executes(ctx -> clearRoute(ctx.getSource()))))
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
        // This log is emitted by the actual dispatcher registration event,
        // never merely because the mod constructor happened to execute.
        boolean npcPresent = event.getDispatcher().getRoot().getChild("npc") != null;
        boolean checkPresent = event.getDispatcher().getRoot().getChild("remecheck") != null;
        if (!npcPresent || !checkPresent)
            throw new IllegalStateException("REME NPC command registration missing at server dispatcher");
        com.mojang.logging.LogUtils.getLogger().info(
            "REME NPC {} COMMANDS REGISTERED: /npc={}, /remecheck={},"
                + " dispatcherIdentity={}, NeoForge requires runtime 21.1.262+",
            dev.reme.mcstudio.StudioMod.VERSION, npcPresent, checkPresent,
            System.identityHashCode(event.getDispatcher()));
    }

    private static int marketStatus(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var locals = player.serverLevel().getEntitiesOfClass(
            PlayerNpcEntity.class, player.getBoundingBox().inflate(20),
            resident -> resident.isAlive() && resident.mode() == PlayerNpcEntity.Mode.LIVE);
        int stores = 0, operating = 0, food = 0, patronsWithMoney = 0, localFarmers = 0;
        for (PlayerNpcEntity resident : locals) {
            if (resident.profession() == PlayerNpcEntity.Profession.SHOPKEEPER) {
                stores++;
                food += ShopCounter.stockedFood(resident.backpack());
                if (ShopCounter.openForResidentSales(resident))
                    operating++;
            }
            if (resident.profession() == PlayerNpcEntity.Profession.FARMER)
                localFarmers++;
            if (resident.hunger() <= 65
                && resident.backpack().countItem(Items.EMERALD) > 0)
                patronsWithMoney++;
        }
        final int a = stores, b = operating, c = food, d = patronsWithMoney, e = localFarmers;
        boolean fair = NeighborhoodMarket.fairWindow(player.serverLevel().getDayTime(), 0);
        source.sendSuccess(() -> Component.literal(
            "附近20格 NPC 市集：店铺 " + a + "，正在柜台营业 " + b
            + "，真实食品库存 " + c + "，有绿宝石且有消费需求的居民 " + d
            + "，农夫 " + e + "，每七天市集时段 " + (fair ? "进行中" : "未开始")
            + "。NPC 买卖只转移实际背包里的商品和绿宝石，不凭空创造物品。"), false);
        return 1;
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
        // Census the actual neighborhood rather than choosing only the
        // closest individual. An unrelated NPC spawned by /npc spawn may be
        // nearer to the player than three allied real households.
        var neighbors = player.serverLevel().getEntitiesOfClass(
            PlayerNpcEntity.class, player.getBoundingBox().inflate(24),
            resident -> resident.isAlive()
                && resident.mode() == PlayerNpcEntity.Mode.LIVE);
        if (neighbors.isEmpty()) {
            source.sendFailure(Component.literal(
                "No live resident within 24 blocks to census; visit a populated area"));
            return 0;
        }
        var community = neighbors.stream()
            .map(npc -> EmergentSettlements.survey(player.serverLevel(), npc))
            .filter(java.util.Objects::nonNull)
            .max(Comparator.comparingInt(EmergentSettlements.Settlement::households))
            .orElse(null);
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


    private static int npcCameraStatus(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        source.sendSuccess(() -> Component.literal(NpcCameraController.status(player)
            +"。先 /gamemode spectator；/npc camera pov shoplifter 或"
            +" /npc camera chase guard；/npc camera stop。"),false);
        return 1;
    }
    private static int npcCameraStart(CommandSourceStack source, String role,
                                      NpcCameraController.Mode mode)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player=source.getPlayerOrException();
        PlayerNpcEntity npc=NpcCameraController.find(player,role);
        if (npc==null || !NpcCameraController.start(player,npc,mode)) {
            source.sendFailure(Component.literal("必须先切到旁观模式，目标需在同维度加载范围64格内；"
                +"角色仅限 shoplifter/clerk/guard/park/nearest"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("已连续跟踪真实NPC "+npc.getUUID()+"："
            +mode+"；镜头只移动玩家，不移动NPC。停止 /npc camera stop"),false);
        return 1;
    }
    private static int npcCameraStop(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NpcCameraController.stop(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("NPC镜头已解除附身/追踪"),false);
        return 1;
    }

    private static int cityHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "NPC 城市工作室：/npc city survey 检查当前位置的41×41平坦空地；"
            + "/npc city demo 仅OP允许，在全新平坦测试世界真正放置城市方块和13个居民。"
            + "/npc city audit 实时查看店铺、顾客、逃单、报警与追捕。" 
            + "演示不会自动填平、破坏现有建筑；NPC自主生存建设仍属另一系统。"), false);
        return 1;
    }

    private static int citySurvey(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String result = CityDistrictDemo.survey(player.serverLevel(), player.blockPosition());
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int cityCensus(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var citizens = player.serverLevel().getEntitiesOfClass(
            PlayerNpcEntity.class, player.getBoundingBox().inflate(42),
            npc -> npc.isAlive());
        int shops=0, farmers=0, officers=0, clients=0;
        for (PlayerNpcEntity npc : citizens) {
            switch (npc.profession()) {
                case SHOPKEEPER -> shops++;
                case FARMER -> farmers++;
                case GUARD -> officers++;
                case RESIDENT -> clients++;
                default -> {}
            }
        }
        final int count=citizens.size(), s=shops, f=farmers, g=officers, c=clients;
        source.sendSuccess(() -> Component.literal(
            "城市实体普查：周围42格内真正存活 NPC " + count
            + "；店员 " + s + "；农夫 " + f + "；警卫 " + g
            + "；普通居民 " + c + "。这是实时加载的实体统计，"
            + "不是 /npc city demo 的预期创建数量。"), false);
        return 1;
    }

    /** Real-time on-location audit; numbers come from live entity state,
     * never inferred from the script or generated city template.
     */
    private static int cityLiveAudit(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        long now = p.serverLevel().getGameTime();
        var residents = p.serverLevel().getEntitiesOfClass(
            PlayerNpcEntity.class, p.getBoundingBox().inflate(42),
            other -> other.isAlive() && other.mode() == PlayerNpcEntity.Mode.LIVE);
        int open = 0, closed = 0, food = 0, customers = 0, unpaid = 0,
            reported = 0, officers = 0, pursuing = 0, detained = 0, guardsAtCounter = 0;
        for (PlayerNpcEntity npc : residents) {
            if (npc.profession() == PlayerNpcEntity.Profession.SHOPKEEPER) {
                if (ShopCounter.openForResidentSales(npc)) {
                    open++;
                    guardsAtCounter += ConvenienceStoreCrime.nearbySecurity(
                        npc, p.serverLevel(), now);
                } else closed++;
                food += ShopCounter.stockedFood(npc.backpack());
            }
            if (npc.profession() == PlayerNpcEntity.Profession.RESIDENT
                && npc.backpack().countItem(Items.EMERALD) > 0)
                customers++;
            if (npc.hasUnpaidShopItem(now)) {
                unpaid++;
                if (npc.shopCrimeReported()) reported++;
            }
            if (npc.profession() == PlayerNpcEntity.Profession.GUARD) {
                officers++;
                if (npc.policeSuspect() != null && npc.policeAlertUntil() > now)
                    pursuing++;
            }
            if (npc.isDetained(now)) detained++;
        }
        final int a=open,b=closed,c=food,d=customers,e=unpaid,g=reported,
            h=officers,i=pursuing,j=detained,k=guardsAtCounter;
        source.sendSuccess(() -> Component.literal(
            "城市治安直播统计 | tick=" + now
            + " | 营业=" + a + " 休业=" + b + " 真实库存=" + c
            + " 有钱顾客=" + d + " 逃单嫌疑人=" + e
            + " 已报警=" + g + " 警察=" + h + " 追捕任务=" + i
            + " 在押=" + j + " 店铺周边警力=" + k
            + "。仅统计周围42格真实加载实体，不伪造抓捕。"), false);
        return 1;
    }

    private static int cityGrow(CommandSourceStack source, net.minecraft.core.Direction way)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer user=source.getPlayerOrException();
        BlockPos center=user.blockPosition().below();
        CityDistrictDemo.Result outcome=CityDistrictExpansion.grow(
            user.serverLevel(),center,way,user);
        if (!outcome.success()) {
            source.sendFailure(Component.literal("新城区未生成：" + outcome.reason()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(outcome.reason()
            + "；本次增加方块="+outcome.blocks()
            + "；本次新增加载NPC="+outcome.citizens()),true);
        return 1;
    }

    private static int cityDemo(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CityDistrictDemo.Result result = CityDistrictDemo.build(
            player.serverLevel(), player.blockPosition(), player);
        if (!result.success()) {
            source.sendFailure(Component.literal("城市演示拒绝施工：" + result.reason()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            result.reason() + "；真实方块：" + result.blocks()
            + "；实际生活NPC：" + result.citizens()
            + "。可用 /npc market、/npc city 查看经济和城镇系统。"), true);
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

    private static int bedStatus(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestNpc(source.getPlayerOrException());
        if (npc == null) {
            source.sendFailure(Component.literal("附近 16 格没有 NPC"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            npc.getName().getString() + " 床位: "
            + (npc.assignedBedPos() == null ? "未分配" : npc.assignedBedPos().toShortString())
            + " | 原生睡眠状态: " + npc.isSleeping()
            + " | 住处: " + npc.homePos().toShortString()), false);
        return 1;
    }

    private static int assignBed(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) {
            source.sendFailure(Component.literal("需要附近 16 格内自己拥有的 NPC"));
            return 0;
        }
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult block)
            || !player.serverLevel().hasChunkAt(block.getBlockPos())) {
            source.sendFailure(Component.literal("请看向 6 格内已加载的原版床"));
            return 0;
        }
        BlockPos target = block.getBlockPos();
        var state = player.serverLevel().getBlockState(target);
        if (!(state.getBlock() instanceof BedBlock)) {
            source.sendFailure(Component.literal("目标不是原版床方块"));
            return 0;
        }
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD
            ? target : target.relative(state.getValue(BedBlock.FACING));
        if (!ResidentBedLife.claimSpecificBed(npc, player.serverLevel(), head)) {
            source.sendFailure(Component.literal(
                "床位不可分配：必须是住处附近可走到的空床，且没有其他 NPC 预订"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            "已为 " + npc.getName().getString() + " 认领床头 " + head.toShortString()
            + "，入夜会真实走到床旁并睡觉"), false);
        return 1;
    }

    private static int clearBed(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) {
            source.sendFailure(Component.literal("需要附近 16 格内自己拥有的 NPC"));
            return 0;
        }
        ResidentBedLife.forgetBed(npc);
        source.sendSuccess(() -> Component.literal(
            "已解除 " + npc.getName().getString() + " 的床位；如果睡着会安全起床"), false);
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

    private static int setBlueprint(CommandSourceStack source, String selected)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) return 0;
        SafeBuilder.Template kind;
        try { kind = SafeBuilder.Template.valueOf(selected.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) {
            source.sendFailure(Component.literal(
                "Unknown building type. Allowed: shelter, shop, bakery."));
            return 0;
        }
        if (npc.buildEnabled()) {
            source.sendFailure(Component.literal(
                "Pause the active construction with /npc buildstop before changing templates."));
            return 0;
        }
        npc.setBuildTemplate(kind);
        source.sendSuccess(() -> Component.literal(
            "Building blueprint: " + kind + " | "
            + SafeBuilder.steps(kind) + " real block placements. Prepare actual"
            + " inventory materials. /npc setwork at build site; /npc buildstart"
            + " checks empty loaded cells. No buildings spawn for free."), false);
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
        if (!SafeBuilder.validateSite(player.serverLevel(), npc.workPos(), npc.buildTemplate())) {
            source.sendFailure(Component.literal("Build site must be loaded and empty: 5x5 x 3, no entities inside"));
            return 0;
        }
        npc.setProfession(PlayerNpcEntity.Profession.BUILDER);
        npc.resetBuildCursor();
        npc.setBuildEnabled(true);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        source.sendSuccess(() -> Component.literal("Started consented 5x5 " + npc.buildTemplate() + " build at " +
            npc.workPos().toShortString() + "; uses actual cobblestone/planks, cannot clear blocks"), true);
        return 1;
    }

    private static int routeStatus(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) return 0;
        source.sendSuccess(() -> Component.literal(
            npc.getName().getString() + " | caravan pickup=" + npc.caravanPickup()
            + " | dropoff=" + npc.caravanDropoff()
            + " | cargo wheat=" + npc.backpack().countItem(Items.WHEAT)
            + " | deliveries=" + npc.parcelsDelivered()), false);
        return 1;
    }

    private static int setRouteDepot(CommandSourceStack source, boolean pickup)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null) return 0;
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult block)
            || !(player.serverLevel().getBlockEntity(block.getBlockPos())
                instanceof ChestBlockEntity)) {
            source.sendFailure(Component.literal(
                "Look directly at a loaded vanilla CHEST within six blocks"));
            return 0;
        }
        BlockPos chosen = block.getBlockPos();
        if (!DailyRoaming.inRange(npc, chosen)) {
            source.sendFailure(Component.literal(
                "Depot outside resident's mobility radius; assign a local site"));
            return 0;
        }
        if (pickup) npc.setCaravanPickup(chosen);
        else npc.setCaravanDropoff(chosen);
        npc.setProfession(PlayerNpcEntity.Profession.COURIER);
        npc.setMode(PlayerNpcEntity.Mode.LIVE);
        source.sendSuccess(() -> Component.literal(
            "Caravan " + (pickup ? "SOURCE" : "DESTINATION") + " authorized at "
            + chosen.toShortString()
            + ". Point at the other chest and assign it; only actual wheat moves."), false);
        return 1;
    }

    private static int clearRoute(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) return 0;
        npc.setCaravanPickup(null);
        npc.setCaravanDropoff(null);
        source.sendSuccess(() -> Component.literal(
            "Caravan route canceled. Existing goods remain in actual NPC backpack."), false);
        return 1;
    }

    private static int jailStatus(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity guard = closestOwnedNpc(source.getPlayerOrException());
        if (guard == null || guard.profession() != PlayerNpcEntity.Profession.GUARD) {
            source.sendFailure(Component.literal("Find an owned GUARD NPC within 16 blocks"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
            "Guard: " + guard.getName().getString()
            + " | designated holding spot=" + guard.policeJail()
            + " | current crime report=" + guard.policeSuspect()
            + " | physical iron bars required; no artificial prison generated"), false);
        return 1;
    }

    private static int assignJail(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity guard = closestOwnedNpc(player);
        if (guard == null || guard.profession() != PlayerNpcEntity.Profession.GUARD) {
            source.sendFailure(Component.literal("Owned GUARD NPC must be within 16 blocks"));
            return 0;
        }
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult block)
            || !player.serverLevel().getBlockState(block.getBlockPos())
                .is(net.minecraft.world.level.block.Blocks.IRON_BARS)) {
            source.sendFailure(Component.literal("Look directly at existing iron bars within six blocks"));
            return 0;
        }
        BlockPos spot = PublicSafety.nearestHoldingSpot(
            player.serverLevel(), block.getBlockPos(), guard);
        if (spot == null) {
            source.sendFailure(Component.literal(
                "No loaded walkable cell adjacent to bars within guard territory."
                + " Provide a solid floor with two air blocks above it."));
            return 0;
        }
        guard.setPoliceJail(spot);
        source.sendSuccess(() -> Component.literal(
            "Authorized nonlethal holding location " + spot.toShortString()
            + ". Only caught NPC raiders with real eyewitness robbery complaints"
            + " can be detained; no human players are arrested."
            + " Add actual walls/locked access to make a real secure prison."), true);
        return 1;
    }

    private static int clearJail(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity guard = closestOwnedNpc(source.getPlayerOrException());
        if (guard == null || guard.profession() != PlayerNpcEntity.Profession.GUARD) return 0;
        guard.setPoliceJail(null);
        guard.clearPoliceCase();
        source.sendSuccess(() -> Component.literal(
            "Guard holding location unset. Existing individual detainees"
            + " retain their finite sentence and automatic release."), true);
        return 1;
    }

    private static int describeWorkplace(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) return 0;
        var survey = CivicWorkplaces.inspect(source.getLevel(), npc.workPos());
        source.sendSuccess(() -> Component.literal(
            npc.getName().getString() + " workplace: "
            + survey.type() + " | physical stations=" + survey.requiredBlocks()
            + " | roof=" + survey.roof() + " | door=" + survey.door()
            + " | finished building=" + survey.indoorBuilding()
            + " | actual role compatible="
            + CivicWorkplaces.matchesProfession(survey.type(), npc.profession())
            + ". Worksite detection does not automatically build a structure or create supplies."), false);
        return 1;
    }

    private static int shopStockStatus(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) return 0;
        source.sendSuccess(() -> Component.literal(
            "Shopkeeper=" + (npc.profession() == PlayerNpcEntity.Profession.SHOPKEEPER)
            + " | assigned stock chest=" + npc.shopStockChest()
            + " | actual food=" + ShopCounter.stockedFood(npc.backpack())
            + " | emerald balance=" + npc.backpack().countItem(Items.EMERALD)), false);
        return 1;
    }

    private static int assignShopStock(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PlayerNpcEntity npc = closestOwnedNpc(player);
        if (npc == null || npc.profession() != PlayerNpcEntity.Profession.SHOPKEEPER) {
            source.sendFailure(Component.literal(
                "Own a SHOPKEEPER NPC within sixteen blocks before assigning stocks"));
            return 0;
        }
        HitResult hit = player.pick(6.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult target)
            || !player.serverLevel().hasChunkAt(target.getBlockPos())
            || !(player.serverLevel().getBlockEntity(target.getBlockPos())
                instanceof ChestBlockEntity)
            || !DailyRoaming.inRange(npc, target.getBlockPos())) {
            source.sendFailure(Component.literal(
                "Look at an actual loaded vanilla chest within six blocks"
                + " and the shopkeeper's home territory. No remote inventories."));
            return 0;
        }
        BlockPos pos = target.getBlockPos();
        npc.setShopStockChest(pos);
        source.sendSuccess(() -> Component.literal(
            "Shop supply chest authorized at " + pos.toShortString()
            + ". Clerk physically retrieves up to eight existing food items"
            + " when local carried stock runs low. No goods are generated."), true);
        return 1;
    }

    private static int clearShopStock(CommandSourceStack source)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerNpcEntity npc = closestOwnedNpc(source.getPlayerOrException());
        if (npc == null) return 0;
        npc.setShopStockChest(null);
        source.sendSuccess(() -> Component.literal(
            "Automatic shop restock canceled; all existing items remain intact."), false);
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
            + " | bed " + (npc.assignedBedPos() == null ? "unassigned"
                : npc.assignedBedPos().toShortString())
            + " | sleeping " + npc.isSleeping()
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
