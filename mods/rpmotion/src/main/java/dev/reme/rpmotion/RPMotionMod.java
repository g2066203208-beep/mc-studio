package dev.reme.rpmotion;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Software-only diagnostic/control host; NEVER duplicates Create Aeronautics blocks.
 * Actual bearing power is supplied by the player's installed Create shaft network.
 */
@Mod(RPMotionMod.ID)
public final class RPMotionMod {
    public static final String ID="rpmotion";
    public RPMotionMod(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }
    private void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher=event.getDispatcher();
        dispatcher.register(Commands.literal("rpmotion")
            .then(Commands.literal("probe")
                .then(Commands.argument("pos",BlockPosArgument.blockPos())
                    .executes(context->{
                        CommandSourceStack source=context.getSource();
                        ServerLevel level=source.getLevel();
                        BlockPos pos=BlockPosArgument.getLoadedBlockPos(context,"pos");
                        var status=NativeBearingProbe.read(level,pos);
                        if(!status.installed()) {
                            source.sendFailure(Component.literal("No ORIGINAL simulated:swivel_bearing at "+pos.toShortString()));
                            return 0;
                        }
                        String text="Original Aeronautics swivel bearing @ "+pos.toShortString()
                            +" | assembled="+status.assembled()
                            +" | Sable UUID="+status.subLevelId()
                            +" | native plate="+status.platePos()
                            +" | native target="+String.format(java.util.Locale.ROOT,"%.2f",status.targetDeg())+" degrees";
                        source.sendSuccess(()->Component.literal(text),false);
                        return status.attached()?2:1;
                    }))));
    }
}
