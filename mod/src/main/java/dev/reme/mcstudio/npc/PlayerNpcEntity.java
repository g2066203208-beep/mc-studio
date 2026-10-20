package dev.reme.mcstudio.npc;

import java.util.EnumSet;
import java.util.Map;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.LivingWorldActions;
import dev.reme.mcstudio.npc.life.ResidentMemory;
import dev.reme.mcstudio.npc.life.SocietyRules;
import dev.reme.mcstudio.npc.life.LifeJourney;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
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
import net.minecraft.world.entity.Entity;
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
    public enum Profession { FARMER, BAKER, RESIDENT, GUARD, SCOUT, COURIER, BUILDER }

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
    private SocietyRules.Identity identity = SocietyRules.civilian("river");
    private LifeJourney journey = new LifeJourney(LifeJourney.Goal.LIVE_PEACEFULLY);
    @Nullable private BlockPos supplyPos;
    @Nullable private UUID escortLeader;
    @Nullable private BlockPos exploreTarget;
    private final Set<Long> recentChunks = new LinkedHashSet<>();
    private int parcelsDelivered = 0;
    private boolean buildEnabled = false;
    private int buildCursor = 0;
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
    public SocietyRules.Identity identity() { return identity; }
    public void setIdentity(SocietyRules.Identity next) { identity = next; }
    public boolean hostileTo(PlayerNpcEntity other) {
        return SocietyRules.hostile(identity, other.identity());
    }
    public LifeJourney journey() { return journey; }
    @Nullable public BlockPos supplyPos() { return supplyPos; }
    public void setSupplyPos(@Nullable BlockPos pos) {
        supplyPos = pos == null ? null : pos.immutable();
    }
    @Nullable public UUID escortLeader() { return escortLeader; }
    public void setEscortLeader(@Nullable UUID uuid) { escortLeader = uuid; }
    @Nullable public BlockPos exploreTarget() { return exploreTarget; }
    public void setExploreTarget(@Nullable BlockPos pos) {
        exploreTarget = pos == null ? null : pos.immutable();
    }
    public int parcelsDelivered() { return parcelsDelivered; }
    public boolean buildEnabled() { return buildEnabled; }
    public void setBuildEnabled(boolean enabled) { buildEnabled = enabled; }
    public int buildCursor() { return buildCursor; }
    public void advanceBuildCursor() { buildCursor++; }
    public void resetBuildCursor() { buildCursor = 0; }
    public void recordDelivery() { parcelsDelivered++; journey.succeeded(LifeJourney.Achievement.BARTER); }
    public boolean visitChunk(int x, int z) {
        long encoded = ChunkPos.asLong(x, z);
        if (recentChunks.contains(encoded)) return false;
        if (recentChunks.size() >= 64) {
            var iterator = recentChunks.iterator();
            iterator.next();
            iterator.remove();
        }
        recentChunks.add(encoded);
        journey.succeeded(LifeJourney.Achievement.VISIT_NEW_CHUNK);
        return true;
    }
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
    @Override public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && mode == Mode.LIVE && profession == Profession.GUARD &&
            (target instanceof net.minecraft.world.entity.monster.Monster ||
             target instanceof PlayerNpcEntity other && hostileTo(other))) {
            journey.succeeded(LifeJourney.Achievement.DEFEND);
        }
        return hit;
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
                return mode != Mode.STAY && (mode != Mode.LIVE || profession == Profession.GUARD)
                    && super.canUse();
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
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override public boolean canUse() {
                return mode != Mode.LIVE && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true) {
            @Override public boolean canUse() {
                return (mode == Mode.GUARD || mode == Mode.WANDER) && super.canUse();
            }
            @Override public boolean canContinueToUse() {
                return (mode == Mode.GUARD || mode == Mode.WANDER) && super.canContinueToUse();
            }
        });
    }

    private boolean sellBread(Player customer, ItemStack payment) {
        if (profession != Profession.BAKER || !payment.is(Items.EMERALD)) return false;
        SimpleContainer snapshot = new SimpleContainer(36);
        for (int i = 0; i < 36; i++) snapshot.setItem(i, backpack.getItem(i).copy());
        boolean hasBread = false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = snapshot.getItem(i);
            if (stack.is(Items.BREAD) && !stack.isEmpty()) {
                stack.shrink(1);
                hasBread = true;
                break;
            }
        }
        if (!hasBread || !snapshot.addItem(new ItemStack(Items.EMERALD)).isEmpty())
            return false;
        ItemStack loaf = new ItemStack(Items.BREAD);
        if (!customer.getInventory().add(loaf)) return false;
        for (int i = 0; i < 36; i++) backpack.setItem(i, snapshot.getItem(i).copy());
        backpack.setChanged();
        payment.shrink(1);
        memory.witness(level().getGameTime(), customer.getUUID(),
            ResidentMemory.Kind.GIFT, blockPosition().toShortString());
        journey.succeeded(LifeJourney.Achievement.BARTER);
        setAction("sold 1 real bread for 1 emerald");
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        if (sellBread(player, held)) {
            player.displayClientMessage(Component.literal("Bought real bread for one emerald"), false);
            return InteractionResult.CONSUME;
        }
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
            memory.witness(level().getGameTime(), player.getUUID(),
                ResidentMemory.Kind.GREETING, blockPosition().toShortString());
            player.displayClientMessage(Component.literal(getName().getString()
                + ": " + lastAction + " | " + intent()
                + " | trust " + memory.trust(player.getUUID())), false);
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
        if (one.is(Items.BREAD) || one.is(Items.CARROT) ||
                one.is(Items.APPLE) || one.is(Items.BAKED_POTATO)) {
            memory.witness(level().getGameTime(), player.getUUID(),
                ResidentMemory.Kind.GIFT, blockPosition().toShortString());
        }
        player.displayClientMessage(Component.literal("NPC backpack: item received"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("NpcOwner", ownerUuid);
        tag.putString("NpcMode", mode.name());
        tag.putString("NpcProfession", profession.name());
        tag.putString("NpcNation", identity.nation().name());
        tag.putString("NpcFaction", identity.faction().name());
        tag.putString("NpcRegion", identity.region());
        tag.putString("NpcLifeGoal", journey.goal().name());
        tag.putInt("NpcGoalProgress", journey.progress());
        if (supplyPos != null) tag.putLong("NpcSupplyPos", supplyPos.asLong());
        if (escortLeader != null) tag.putUUID("NpcEscortLeader", escortLeader);
        if (exploreTarget != null) tag.putLong("NpcExploreTarget", exploreTarget.asLong());
        tag.putLongArray("NpcRecentChunks", recentChunks.stream().mapToLong(Long::longValue).toArray());
        tag.putInt("NpcParcels", parcelsDelivered);
        tag.putBoolean("NpcBuildEnabled", buildEnabled);
        tag.putInt("NpcBuildCursor", buildCursor);
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
        try {
            identity = new SocietyRules.Identity(
                SocietyRules.Nation.valueOf(tag.getString("NpcNation")),
                SocietyRules.Faction.valueOf(tag.getString("NpcFaction")),
                tag.getString("NpcRegion"));
        } catch (IllegalArgumentException ignored) {
            identity = SocietyRules.civilian("river");
        }
        try {
            journey.restore(LifeJourney.Goal.valueOf(tag.getString("NpcLifeGoal")),
                tag.getInt("NpcGoalProgress"));
        } catch (IllegalArgumentException ignored) {
            journey = new LifeJourney(LifeJourney.Goal.LIVE_PEACEFULLY);
        }
        supplyPos = tag.contains("NpcSupplyPos", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcSupplyPos")) : null;
        escortLeader = tag.hasUUID("NpcEscortLeader")
            ? tag.getUUID("NpcEscortLeader") : null;
        exploreTarget = tag.contains("NpcExploreTarget", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcExploreTarget")) : null;
        recentChunks.clear();
        if (tag.contains("NpcRecentChunks", Tag.TAG_LONG_ARRAY)) {
            for (long key : tag.getLongArray("NpcRecentChunks")) {
                if (recentChunks.size() >= 64) break;
                recentChunks.add(key);
            }
        }
        parcelsDelivered = Math.max(0, tag.getInt("NpcParcels"));
        buildEnabled = tag.getBoolean("NpcBuildEnabled");
        buildCursor = Math.clamp(tag.getInt("NpcBuildCursor"), 0, 100);
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
