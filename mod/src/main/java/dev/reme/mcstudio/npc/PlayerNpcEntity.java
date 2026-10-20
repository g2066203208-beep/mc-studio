package dev.reme.mcstudio.npc;

import java.util.EnumSet;
import java.util.Map;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.LivingWorldActions;
import dev.reme.mcstudio.npc.life.ResidentMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import java.util.UUID;
import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.Containers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class PlayerNpcEntity extends PathfinderMob {
    public enum Mode { LIVE, FOLLOW, STAY, WANDER, GUARD }
    public enum Profession { FARMER, BAKER, RESIDENT, GUARD }

    private final SimpleContainer backpack = new SimpleContainer(36) {
        @Override public boolean stillValid(Player player) {
            return PlayerNpcEntity.this.isAlive()
                && PlayerNpcEntity.this.distanceToSqr(player) < 64.0
                && PlayerNpcEntity.this.isOwnedBy(player);
        }
    };
    private Mode mode = Mode.LIVE;
    private Profession profession = Profession.RESIDENT;
    private BlockPos homePos = BlockPos.ZERO;
    private BlockPos workPos = BlockPos.ZERO;
    private int hunger = 90, energy = 85, socialNeed = 65;
    private final ResidentMemory memory = new ResidentMemory();
    private LivingPlanner.Personality personality = LivingPlanner.Personality.average();
    private LivingPlanner planner = new LivingPlanner(personality);
    private UUID recentAggressor = null;
    private long afraidUntil = 0;
    private LivingPlanner.Decision decision =
        new LivingPlanner.Decision(LivingPlanner.Intent.IDLE, 0, "starting");
    private String lastAction = "just arrived";
    private long nextSocialTick = 0;
    @Nullable private UUID ownerUuid;

    public PlayerNpcEntity(EntityType<? extends PlayerNpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.getNavigation().setCanFloat(true);
        this.homePos = blockPosition();
        this.workPos = blockPosition();
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
    public Profession profession() { return profession; }
    public void setProfession(Profession job) { profession = job; }
    public void setHomePos(BlockPos pos) { homePos = pos.immutable(); }
    public void setWorkPos(BlockPos pos) { workPos = pos.immutable(); }
    public BlockPos homePos() { return homePos; }
    public BlockPos workPos() { return workPos; }
    public LivingPlanner planner() { return planner; }
    public LivingPlanner.Personality personality() { return personality; }
    public void setPersonality(LivingPlanner.Personality traits) {
        personality = traits;
        planner = new LivingPlanner(traits);
    }
    public UUID recentAggressor() { return recentAggressor; }
    public boolean isAfraid(long tick) { return recentAggressor != null && tick < afraidUntil; }
    public ResidentMemory memory() { return memory; }
    public int hunger() { return hunger; }
    public int energy() { return energy; }
    public int socialNeed() { return socialNeed; }
    public long nextSocialTick() { return nextSocialTick; }
    public void setNextSocialTick(long time) { nextSocialTick = time; }
    public LivingPlanner.Intent intent() { return decision.intent(); }
    public String intentReason() { return decision.reason(); }
    public String lastAction() { return lastAction; }
    public void setDecision(LivingPlanner.Decision next) { decision = next; }
    public void setAction(String action) { lastAction = action; }
    public void changeNeeds(int food, int rest, int social) {
        hunger = Math.clamp(hunger + food, 0, 100);
        energy = Math.clamp(energy + rest, 0, 100);
        socialNeed = Math.clamp(socialNeed + social, 0, 100);
    }
    public void rememberGreeting(PlayerNpcEntity other, long tick) {
        memory.witness(tick, other.getUUID(), ResidentMemory.Kind.GREETING,
            blockPosition().toShortString());
    }
    @Override public boolean hurt(DamageSource source, float amount) {
        boolean damaged = super.hurt(source, amount);
        if (damaged && !level().isClientSide && source.getEntity() instanceof Player player) {
            recentAggressor = player.getUUID();
            afraidUntil = level().getGameTime() + 600;
            memory.witness(level().getGameTime(), player.getUUID(),
                ResidentMemory.Kind.ATTACK_WITNESSED, blockPosition().toShortString());
            lastAction = "injured by " + player.getName().getString();
        }
        return damaged;
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel server) {
            LivingWorldActions.tick(this, server);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true) {
            @Override public boolean canUse() {
                return mode != Mode.STAY && mode != Mode.LIVE && super.canUse();
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
        ItemStack held = player.getItemInHand(hand);
        if (!isOwnedBy(player)) {
            if (held.isEmpty()) {
                memory.witness(level().getGameTime(), player.getUUID(),
                    ResidentMemory.Kind.GREETING, blockPosition().toShortString());
                player.displayClientMessage(Component.literal(getName().getString()
                    + ": " + lastAction + " | trust " + memory.trust(player.getUUID())), false);
            } else if (held.is(Items.BREAD) || held.is(Items.CARROT) ||
                    held.is(Items.APPLE) || held.is(Items.BAKED_POTATO)) {
                ItemStack gift = held.copyWithCount(1);
                if (backpack.canAddItem(gift)) {
                    backpack.addItem(gift);
                    held.shrink(1);
                    memory.witness(level().getGameTime(), player.getUUID(),
                        ResidentMemory.Kind.GIFT, blockPosition().toShortString());
                    player.displayClientMessage(Component.literal(getName().getString()
                        + " accepted your food gift."), false);
                }
            } else {
                player.displayClientMessage(Component.literal(getName().getString()
                    + " is busy: " + lastAction), false);
            }
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty() && player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                (containerId, inventory, user) ->
                    new ChestMenu(MenuType.GENERIC_9x4, containerId, inventory, backpack, 4),
                Component.literal(getName().getString() + " Backpack")));
            return InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            setMode(mode == Mode.LIVE ? Mode.STAY : Mode.LIVE);
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
        tag.putString("NpcProfession", profession.name());
        tag.putInt("NpcDiligence", personality.diligence());
        tag.putInt("NpcSociability", personality.sociability());
        tag.putInt("NpcCaution", personality.caution());
        tag.putLong("NpcHomePos", homePos.asLong());
        tag.putLong("NpcWorkPos", workPos.asLong());
        tag.putInt("NpcHunger", hunger);
        tag.putInt("NpcEnergy", energy);
        tag.putInt("NpcSocial", socialNeed);
        tag.putString("NpcAction", lastAction.length() > 256 ? lastAction.substring(0, 256) : lastAction);
        ListTag events = new ListTag();
        for (ResidentMemory.Event event : memory.recentEvents()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Tick", event.worldTick());
            entry.putUUID("Actor", event.actor());
            entry.putString("Kind", event.kind().name());
            entry.putString("Place", event.where());
            events.add(entry);
        }
        tag.put("NpcMemories", events);
        ListTag relations = new ListTag();
        int count = 0;
        for (Map.Entry<UUID, Integer> relation : memory.relationshipSnapshot().entrySet()) {
            if (count++ >= 128) break;
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Actor", relation.getKey());
            entry.putInt("Trust", relation.getValue());
            relations.add(entry);
        }
        tag.put("NpcRelationships", relations);
        tag.put("NpcBackpack", backpack.createTag(level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerUuid = tag.hasUUID("NpcOwner") ? tag.getUUID("NpcOwner") : null;
        try {
            mode = Mode.valueOf(tag.getString("NpcMode"));
        } catch (IllegalArgumentException ignored) {
            mode = Mode.LIVE;
        }
        try { profession = Profession.valueOf(tag.getString("NpcProfession")); }
        catch (IllegalArgumentException ignored) { profession = Profession.RESIDENT; }
        setPersonality(new LivingPlanner.Personality(
            Math.clamp(tag.contains("NpcDiligence") ? tag.getInt("NpcDiligence") : 50, 0, 100),
            Math.clamp(tag.contains("NpcSociability") ? tag.getInt("NpcSociability") : 50, 0, 100),
            Math.clamp(tag.contains("NpcCaution") ? tag.getInt("NpcCaution") : 50, 0, 100)));
        homePos = tag.contains("NpcHomePos", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcHomePos")) : blockPosition();
        workPos = tag.contains("NpcWorkPos", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcWorkPos")) : blockPosition();
        hunger = Math.clamp(tag.contains("NpcHunger") ? tag.getInt("NpcHunger") : 90, 0, 100);
        energy = Math.clamp(tag.contains("NpcEnergy") ? tag.getInt("NpcEnergy") : 85, 0, 100);
        socialNeed = Math.clamp(tag.contains("NpcSocial") ? tag.getInt("NpcSocial") : 65, 0, 100);
        lastAction = tag.contains("NpcAction", Tag.TAG_STRING)
            ? tag.getString("NpcAction") : "resumed after reload";
        memory.clear();
        ListTag events = tag.getList("NpcMemories", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(events.size(), 48); i++) {
            CompoundTag entry = events.getCompound(i);
            if (!entry.hasUUID("Actor")) continue;
            try {
                memory.restoreEvent(entry.getLong("Tick"), entry.getUUID("Actor"),
                    ResidentMemory.Kind.valueOf(entry.getString("Kind")), entry.getString("Place"));
            } catch (IllegalArgumentException ignored) { /* forward-compatible event */ }
        }
        ListTag relations = tag.getList("NpcRelationships", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(relations.size(), 128); i++) {
            CompoundTag entry = relations.getCompound(i);
            if (entry.hasUUID("Actor"))
                memory.restoreTrust(entry.getUUID("Actor"), entry.getInt("Trust"));
        }
        if (tag.contains("NpcBackpack", Tag.TAG_LIST)) {
            backpack.fromTag(tag.getList("NpcBackpack", Tag.TAG_COMPOUND), level().registryAccess());
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Containers.dropContents(level, this, backpack);
        backpack.clearContent();
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
