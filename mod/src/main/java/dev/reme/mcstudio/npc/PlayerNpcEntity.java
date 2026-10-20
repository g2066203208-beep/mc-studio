package dev.reme.mcstudio.npc;

import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PlayerNpcEntity extends PathfinderMob {
    public enum Mode { FOLLOW, STAY, WANDER, GUARD }

    private final SimpleContainer backpack = new SimpleContainer(36);
    private Mode mode = Mode.FOLLOW;
    @Nullable private UUID ownerUuid;

    public PlayerNpcEntity(EntityType<? extends PlayerNpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.getNavigation().setCanFloat(true);
    }

    public void setOwner(Player player) { this.ownerUuid = player.getUUID(); }
    public boolean isOwnedBy(Player player) { return ownerUuid != null && ownerUuid.equals(player.getUUID()); }

    @Nullable
    public Player owner() {
        return ownerUuid == null ? null : level().getPlayerByUUID(ownerUuid);
    }

    public Mode mode() { return mode; }
    public void setMode(Mode newMode) {
        mode = newMode;
        this.setTarget(null);
        this.getNavigation().stop();
    }

    public SimpleContainer backpack() { return backpack; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true) {
            @Override public boolean canUse() {
                return mode != Mode.STAY && super.canUse();
            }
        });
        goalSelector.addGoal(2, new FollowPlayerGoal());
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.9) {
            @Override public boolean canUse() {
                return mode == Mode.WANDER && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return mode == Mode.WANDER && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true) {
            @Override public boolean canUse() {
                return (mode == Mode.GUARD || mode == Mode.WANDER) && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return (mode == Mode.GUARD || mode == Mode.WANDER) && super.canContinueToUse();
            }
        });
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!isOwnedBy(player)) {
            player.displayClientMessage(Component.literal("Only this NPC's creator may control it."), true);
            return InteractionResult.CONSUME;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            setMode(mode == Mode.FOLLOW ? Mode.STAY : Mode.FOLLOW);
            player.displayClientMessage(Component.literal("NPC: " + mode.name()), true);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            ItemStack previous = getMainHandItem().copy();
            if (!previous.isEmpty() && !backpack.canAddItem(previous)) {
                player.displayClientMessage(Component.literal("NPC backpack is full."), true);
                return InteractionResult.CONSUME;
            }
            ItemStack equipped = held.copyWithCount(1);
            held.shrink(1);
            setItemSlot(EquipmentSlot.MAINHAND, equipped);
            if (!previous.isEmpty()) backpack.addItem(previous);
            player.displayClientMessage(Component.literal("NPC equipped " + equipped.getHoverName().getString()), true);
            return InteractionResult.CONSUME;
        }

        ItemStack one = held.copyWithCount(1);
        if (!backpack.canAddItem(one)) {
            player.displayClientMessage(Component.literal("NPC backpack is full."), true);
            return InteractionResult.CONSUME;
        }
        backpack.addItem(one);
        held.shrink(1);
        player.displayClientMessage(Component.literal("NPC backpack: item received"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("NpcOwner", ownerUuid);
        tag.putString("NpcMode", mode.name());
        tag.put("NpcBackpack", backpack.createTag(level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerUuid = tag.hasUUID("NpcOwner") ? tag.getUUID("NpcOwner") : null;
        try {
            mode = Mode.valueOf(tag.getString("NpcMode"));
        } catch (IllegalArgumentException ignored) {
            mode = Mode.STAY;
        }
        if (tag.contains("NpcBackpack", Tag.TAG_LIST)) {
            backpack.fromTag(tag.getList("NpcBackpack", Tag.TAG_COMPOUND), level().registryAccess());
        }
    }

    private class FollowPlayerGoal extends Goal {
        private int repathDelay;
        FollowPlayerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() {
            Player player = owner();
            return mode == Mode.FOLLOW && player != null && player.isAlive()
                && distanceToSqr(player) > 9.0;
        }

        @Override public boolean canContinueToUse() {
            Player player = owner();
            return mode == Mode.FOLLOW && player != null && player.isAlive()
                && distanceToSqr(player) > 4.0 && !getNavigation().isDone();
        }

        @Override public void start() { repathDelay = 0; }
        @Override public void stop() { getNavigation().stop(); }

        @Override public void tick() {
            Player player = owner();
            if (player == null) return;
            getLookControl().setLookAt(player, 10.0F, getMaxHeadXRot());
            if (--repathDelay <= 0) {
                repathDelay = 10;
                getNavigation().moveTo(player, 1.2);
            }
        }
    }
}
