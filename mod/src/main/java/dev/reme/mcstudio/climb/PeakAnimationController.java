package dev.reme.mcstudio.climb;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
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
        Vec3 contact = Vec3.ZERO;
        boolean contactReady;
        SpeedModifier speed;
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
                            LAYER_ID, 880, PeakAnimationController::createLayer));
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
            if (state != null && state.normal().lengthSqr() > .5) {
                if (!playback.contactReady) {
                    playback.contact = state.contactPoint();
                    playback.contactReady = true;
                } else if (playback.contact.distanceToSqr(state.contactPoint()) > 4) {
                    // Teleport or huge moving-structure correction: snap safely
                    // rather than interpolating an arm across the whole world.
                    playback.contact = state.contactPoint();
                } else {
                    playback.contact = playback.contact.lerp(state.contactPoint(), 0.28);
                }
            } else playback.contactReady = false;
            int desired = state == null ? NONE : Math.min(CEILING_TRAVERSE, Math.max(HANG, state.action()));
            if (playback.speed != null && state != null && state.normal().lengthSqr() > .5) {
                // Link animation cadence to ACTUAL contact-plane movement, not camera.
                double travel = ClimbSurfaceMath.tangent(client.getDeltaMovement(),
                        state.normal().normalize()).length();
                float wantedSpeed = (float)Math.max(.72, Math.min(2.2, travel/.16));
                if (desired == HANG || desired == CEILING_HANG) wantedSpeed = .84f;
                if (desired == MANTLE) wantedSpeed = 1f;
                playback.speed.speed += (wantedSpeed - playback.speed.speed)*.22f;
            }

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

    private static ModifierLayer<IAnimation> createLayer(AbstractClientPlayer client) {
        ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        Playback playback = PLAYBACKS.computeIfAbsent(client, ignored -> new Playback());
        // Player Animator's supported adjustment API: small arm corrections are
        // derived from the CURRENT WORLD CONTACT, not player look pitch.
        // This is a bounded contact correction atop authored keyframes, not a
        // claim of an exact two-bone wrist IK solver.
        layer.addModifierBefore(new AdjustmentModifier(part ->
                contactCorrection(client, playback, part)));
        playback.speed = new SpeedModifier(1f);
        layer.addModifierLast(playback.speed);
        return layer;
    }

    private static Optional<AdjustmentModifier.PartModifier> contactCorrection(
            AbstractClientPlayer client, Playback playback, String part) {
        boolean isLeft = part.equals("leftArm") || part.equals("left_arm");
        boolean isRight = part.equals("rightArm") || part.equals("right_arm");
        if ((!isLeft && !isRight) || !playback.contactReady) return Optional.empty();
        PeakVisuals.Pose pose = PeakVisuals.pose(client);
        if (pose == null || !pose.climbing() || pose.normal().lengthSqr() < .5)
            return Optional.empty();
        double yaw = Math.toRadians(pose.bodyYaw());
        Vec3 sideways = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 toward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        double armSide = isLeft ? -1 : 1;
        Vec3 shoulder = client.position().add(0, 1.43, 0).add(sideways.scale(armSide*.25));
        Vec3 target = playback.contact.add(sideways.scale(armSide*.27));
        Vec3 delta = target.subtract(shoulder);
        // Maximum ~1 vanilla model pixel / 0.13 radians per component.
        float lateral = (float)Math.max(-1, Math.min(1, delta.dot(sideways)*1.4));
        float vertical = (float)Math.max(-1, Math.min(1, delta.y * .65));
        float forward = (float)Math.max(-1, Math.min(1, delta.dot(toward)*.65));
        boolean roof = ClimbSurfaceMath.ceiling(pose.normal());
        // Vanilla Steve/Alex left arm is model +X; right arm is -X.
        // Widen ONLY the local first-person roof presentation so both hands
        // sit near screen sides and never mask the aiming reticle.
        boolean firstPersonRoof = roof && client == Minecraft.getInstance().player
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        float modelSide = isLeft ? 1f : -1f;
        float wide = firstPersonRoof ? 3.4f : roof ? .25f : 0f;
        Vec3f rotation = new Vec3f(-.085f*vertical, .10f*lateral,
                -.065f*(float)armSide*lateral);
        Vec3f offset = new Vec3f(.67f*lateral + modelSide*wide,
                .44f*vertical, .46f*forward);
        return Optional.of(new AdjustmentModifier.PartModifier(rotation, offset));
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
