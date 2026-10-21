package dev.reme.mcstudio.worldstack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/** Admin control for an opt-in three-layer vertical world stack. */
public final class WorldStackCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private WorldStackCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("reme")
                .then(Commands.literal("worldstack")
                    .executes(WorldStackCommands::status)
                    .then(Commands.literal("status").executes(WorldStackCommands::status))
                    .then(Commands.literal("setup")
                        .requires(source -> source.hasPermission(2))
                        .executes(WorldStackCommands::setup)))
        );
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            WorldStackBridge.Status state = WorldStackBridge.status(source.getServer());
            if (!state.dependencyPresent()) {
                source.sendFailure(Component.literal(
                    "世界堆叠不可用：请安装适配 NeoForge 1.21.1 的 Immersive Portals 与依赖。"));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(
                "世界堆叠（末地/主世界/下界）: "
                    + (state.installed() ? "已建立" : "尚未建立或为其他拓扑")
                    + " | 垂直连接数: "
                    + state.endFloor() + "/" + state.overworldConnections()
                    + "/" + state.netherCeiling()), false);
            return 1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return reportError(source, "查询", e);
        }
    }

    private static int setup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!WorldStackBridge.dependencyPresent()) {
            source.sendFailure(Component.literal(
                "缺少 Immersive Portals；没有进行世界修改。"));
            return 0;
        }
        try {
            WorldStackBridge.InstallResult result = WorldStackBridge.install(source.getServer());
            switch (result) {
                case INSTALLED -> {
                    source.sendSuccess(() -> Component.literal(
                        "已建立：末地底部 ↔ 主世界顶部 ↔ 主世界底部 ↔ 下界顶部。"
                            + " 已有区块的基岩不会自动修改；请在测试存档验证穿越。"), true);
                    return 1;
                }
                case ALREADY_PRESENT ->
                    source.sendFailure(Component.literal("已有维度堆叠，未覆盖原有连接。"));
                case FOREIGN_GLOBAL_PORTALS ->
                    source.sendFailure(Component.literal("检测到已有全局传送门，为保护现有世界已取消安装。"));
                case MISSING_DIMENSION ->
                    source.sendFailure(Component.literal("末地、主世界或下界未加载，未进行修改。"));
                case UNEXPECTED_PORTAL_COUNT ->
                    source.sendFailure(Component.literal(
                        "调用上游接口后连接数不符合预期。请备份存档并检查日志；不要重复执行。"));
            }
            return 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return reportError(source, "安装", e);
        }
    }

    private static int reportError(CommandSourceStack source, String operation, Exception e) {
        LOGGER.error("REME worldstack {} failed; inspect portal storage before retrying", operation, e);
        source.sendFailure(Component.literal(
            "世界堆叠" + operation + "失败。请检查 latest.log，勿在存档上反复尝试。"));
        return 0;
    }
}
