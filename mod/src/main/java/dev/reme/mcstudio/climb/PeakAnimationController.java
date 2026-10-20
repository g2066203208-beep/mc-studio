package dev.reme.mcstudio.climb;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.reme.mcstudio.StudioMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Player Animator API integration. Every observed player gets a separate
 * ModifierLayer so Steve/Alex arms, body and legs animate in both perspectives.
 *
 * Unlike our old PlayerModel Mixin, the animation library owns composition,
 * skin sleeve motion and the third-person-to-first-person arm render pass.
 */
@EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT)
public final class PeakAnimationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PeakAnimationController.class);
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(StudioMod.MOD_ID, "peak_climb_layer");
    private static final Map<AbstractClientPlayer, Playback> PLAYBACKS = new WeakHashMap<>();
    private static final int NONE = -1;
    private static final int HANG = 0;
    private static final int ASCEND = 1;
    private static final int TRAVERSE = 2;
    private static final int MANTLE = 3;
    private static final int RELEASE = 7;
    private static final int CEILING_HANG = 4;
    private static final int CEILING_CRAWL = 5;
    private static final int CEILING_TRAVERSE = 6;
    private static boolean warnedMissing;

    private static final class Playback {
        int mode = NONE;
        int releaseUntil = -1;
    }

    private PeakAnimationController() {}

    @EventBusSubscriber(modid = StudioMod.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() ->
                    PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                            LAYER_ID, 880, player -> new ModifierLayer<IAnimation>()));
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PLAYBACKS.clear();
            return;
        }
        for (var player : mc.level.players()) {
            if (!(player instanceof AbstractClientPlayer client)) continue;
            var stored = PlayerAnimationAccess.getPlayerAssociatedData(client).get(LAYER_ID);
            if (!(stored instanceof ModifierLayer<?> raw)) continue;
            @SuppressWarnings("unchecked")
            ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
            Playback playback = PLAYBACKS.computeIfAbsent(client, ignored -> new Playback());
            PeakVisuals.Pose state = PeakVisuals.pose(client);
            int desired = state == null ? NONE : Math.min(CEILING_TRAVERSE, Math.max(HANG, state.action()));

            if (desired == NONE && playback.mode >= HANG && playback.mode <= CEILING_TRAVERSE) {
                swap(layer, RELEASE);
                playback.mode = RELEASE;
                playback.releaseUntil = client.tickCount + 9;
            } else if (desired >= HANG && desired != playback.mode) {
                swap(layer, desired);
                playback.mode = desired;
                playback.releaseUntil = -1;
            } else if (desired == NONE && playback.mode == RELEASE
                    && client.tickCount >= playback.releaseUntil) {
                layer.replaceAnimationWithFade(
                        AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), null);
                playback.mode = NONE;
            }
        }
    }

    private static void swap(ModifierLayer<IAnimation> layer, int action) {
        String name = switch (action) {
            case ASCEND -> "peak_ascend";
            case TRAVERSE -> "peak_traverse";
            case MANTLE -> "peak_mantle";
            case CEILING_HANG -> "peak_ceiling_hang";
            case CEILING_CRAWL -> "peak_ceiling_crawl";
            case CEILING_TRAVERSE -> "peak_ceiling_traverse";
            case RELEASE -> "peak_release";
            default -> "peak_hang";
        };
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(StudioMod.MOD_ID, name);
        IPlayable resource = PlayerAnimationRegistry.getAnimation(key);
        if (resource == null) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOGGER.error("Missing Player Animator keyframe resource {} - check assets/reme_studio/player_animations", key);
            }
            return;
        }
        IAnimation animation = resource.playAnimation();
        if (animation instanceof KeyframeAnimationPlayer keyframes) {
            // Render actual vanilla player arms via Player Animator's supported
            // first-person model route instead of fixed-pose ItemInHandRenderer edits.
            keyframes.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            // Player Animator has BOTH arm visibility flags false by default.
            keyframes.setFirstPersonConfiguration(new FirstPersonConfiguration()
                    .setShowLeftArm(true).setShowRightArm(true)
                    .setShowLeftItem(true).setShowRightItem(true));
        }
        layer.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE), animation, true);
    }
}
