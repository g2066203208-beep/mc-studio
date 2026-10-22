package dev.reme.mcstudio.npc;

import java.util.EnumSet;
import java.util.Map;
import dev.reme.mcstudio.npc.life.LivingPlanner;
import dev.reme.mcstudio.npc.life.LivingWorldActions;
import dev.reme.mcstudio.npc.life.ResidentMemory;
import dev.reme.mcstudio.npc.life.SocietyRules;
import dev.reme.mcstudio.npc.life.LifeJourney;
import dev.reme.mcstudio.npc.life.MovementPolicy;
import dev.reme.mcstudio.npc.life.NaturalResidentSpawn;
import dev.reme.mcstudio.npc.life.CivicMind;
import dev.reme.mcstudio.npc.life.CivicSocial;
import dev.reme.mcstudio.npc.life.ShopCounter;
import dev.reme.mcstudio.npc.life.SafeBuilder;
import dev.reme.mcstudio.npc.life.CivicPersistence;
import dev.reme.mcstudio.npc.life.Commission;
import dev.reme.mcstudio.npc.life.CharacterProfile;
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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ServerLevelAccessor;
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
    private static final EntityDataAccessor<Integer> NPC_SKIN =
        SynchedEntityData.defineId(PlayerNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> NPC_VISUAL_POSE =
        SynchedEntityData.defineId(PlayerNpcEntity.class, EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(NPC_SKIN, 0);
        builder.define(NPC_VISUAL_POSE, NpcPose.IDLE.ordinal());
    }

    public enum Mode { LIVE, FOLLOW, STAY, WANDER, GUARD }
    public enum Profession { FARMER, BAKER, RESIDENT, GUARD, SCOUT, COURIER, BUILDER, SHOPKEEPER }

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
    // Native vanilla bed HEAD, saved in the existing REME NPC NBT.
    @Nullable private BlockPos assignedBedPos;
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
    @Nullable private BlockPos caravanPickup;
    @Nullable private BlockPos caravanDropoff;
    @Nullable private BlockPos shopStockChest;
    @Nullable private UUID escortLeader;
    @Nullable private BlockPos exploreTarget;
    private final Set<Long> recentChunks = new LinkedHashSet<>();
    private int parcelsDelivered = 0;
    private int formationSlot = 0;
    private int roamingRadius = 48;
    public int skinId() { return this.entityData.get(NPC_SKIN); }
    public NpcPose visualPose() {
        return NpcPose.fromNetworkId(this.entityData.get(NPC_VISUAL_POSE));
    }
    /** Server-authored, eventful state only. Each client interpolates its
     * own vanilla-scale bones; we never spam six limb angles over network.
     */
    public void setVisualPose(NpcPose value) {
        if (!level().isClientSide && visualPose() != value)
            entityData.set(NPC_VISUAL_POSE, value.ordinal());
    }
    public void setSkinId(int value) {
        this.entityData.set(NPC_SKIN, Math.clamp(value, 0, 5));
    }
    private boolean naturalResident = false;
    private int dailyShiftTicks = 0;
    @Nullable private BlockPos cachedCropTarget;
    // Single operator-authorized mining order. Progress is transient; reload
    // safely restarts the survival-rate action instead of instant completing.
    @Nullable private BlockPos miningTarget;
    private float miningProgress = 0.0F;
    // Exact UUIDs of drops created by this NPC's *real* block break, not any
    // player's unrelated floor loot. Hard cap and expiry prevent runaway state.
    private final Map<UUID, Integer> ownedMiningDrops = new java.util.LinkedHashMap<>();
    private long miningLootExpiry = 0L;
    private long miningLootStarted = 0L;
    @Nullable private BlockPos lastFootTrafficSample;
    private BlockPos lastNavDestination = BlockPos.ZERO;
    private long nextNavRepathTick = 0;
    private final CivicMind civic = new CivicMind();
    private CharacterProfile character = new CharacterProfile(60, 65, 60, 60);
    @Nullable private Commission commission;
    private long lastAttackReport = -10000L;
    private long nextRaidTick = 0L;
    // Bounded local police case. No global suspect scans or arbitrary arrests.
    @Nullable private UUID policeSuspect;
    private long policeAlertUntil = 0L;
    @Nullable private BlockPos policeJail;
    private long detainedUntil = 0L;
    @Nullable private BlockPos detentionJail;
    private boolean buildEnabled = false;
    private int buildCursor = 0;
    private SafeBuilder.Template buildTemplate = SafeBuilder.Template.SHELTER;
    private long nextSocialTick = 0;
    @Nullable private UUID ownerUuid;

    public PlayerNpcEntity(EntityType<? extends PlayerNpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.getNavigation().setCanFloat(true);
        this.homePos = blockPosition();
        this.workPos = blockPosition();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world,
            DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData group) {
        SpawnGroupData outcome = super.finalizeSpawn(world, difficulty, reason, group);
        if (NaturalResidentSpawn.allowedNaturalReason(reason)
            || reason == MobSpawnType.SPAWN_EGG) {
            // User-spawned eggs get valid home/work coordinates too, instead
            // of retaining BlockPos.ZERO and navigating towards world origin.
            naturalResident = NaturalResidentSpawn.allowedNaturalReason(reason);
            BlockPos anchor = blockPosition();
            setHomePos(anchor);
            setWorkPos(anchor);
            setIdentity(SocietyRules.civilian(
                MovementPolicy.region(anchor.getX(), anchor.getZ())));
            setRoamingRadius(32 + getRandom().nextInt(33));
            setMode(Mode.LIVE);
            int traits = getRandom().nextInt(1_000_000);
            setSkinId(getRandom().nextInt(6));
            setPersonality(new LivingPlanner.Personality(
                30 + Math.floorMod(traits, 61), 30 + Math.floorMod(traits / 17, 61),
                30 + Math.floorMod(traits / 31, 61)));
            setCharacter(dev.reme.mcstudio.npc.life.CharacterProfile.fromSeed(traits));
            dailyShiftTicks = MovementPolicy.dailyShift(traits);
            var job = getRandom().nextInt(8);
            setProfession(job == 0 ? Profession.GUARD :
                job == 1 ? Profession.SCOUT : Profession.RESIDENT);
            this.setCustomName(Component.literal("旅居者-" +
                Integer.toHexString(traits).toUpperCase(java.util.Locale.ROOT)));
            this.backpack.addItem(new ItemStack(Items.BREAD, 2));
        }
        return outcome;
    }

    public void setOwner(Player player) { this.ownerUuid = player.getUUID(); }
    public boolean isOwnedBy(Player player) { return ownerUuid != null && ownerUuid.equals(player.getUUID()); }

    @Nullable
    public Player owner() {
        return ownerUuid == null ? null : level().getPlayerByUUID(ownerUuid);
    }

    public Mode mode() { return mode; }
    public void setMode(Mode newMode) {
        if (newMode != Mode.LIVE && isSleeping() && !level().isClientSide)
            stopSleeping(); // release occupied real bed before follow/stay
        mode = newMode;
        this.setTarget(null);
        this.getNavigation().stop();
    }

    public SimpleContainer backpack() { return backpack; }
    public CivicMind civic() { return civic; }
    public CharacterProfile character() { return character; }
    public String conversationWith(Player player) {
        CivicMind.Standing status = civic.standing(player.getUUID(), character.justice());
        if (status == CivicMind.Standing.WANTED) {
            return "I have heard of your violence. Stay away from my friends.";
        }
        if (commission != null && commission.state() == Commission.State.OPEN) {
            return "Could you bring " + commission.quantity() + " " +
                commission.goods() + "? My reward is " +
                commission.emeraldReward() + " real emeralds.";
        }
        var reports = civic.reports();
        if (!reports.isEmpty() && character.honesty() > 35) {
            CivicMind.Report report = reports.get(reports.size() - 1);
            return (report.direct() ? "I witnessed " : "Someone told me about ") +
                report.kind().name().toLowerCase(java.util.Locale.ROOT) +
                " involving " + report.actor().toString().substring(0, 8) +
                ". I am " + (report.direct() ? "sure." : "not an eyewitness.");
        }
        if (hunger < 30) return "I need some food. " + lastAction;
        return "Today I am " + lastAction + ".";
    }
    public void setCharacter(CharacterProfile next) { character = next; }
    @Nullable public Commission commission() { return commission; }
    public void setCommission(@Nullable Commission next) { commission = next; }
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
    @Nullable public BlockPos shopStockChest() { return shopStockChest; }
    public void setShopStockChest(@Nullable BlockPos chest) {
        shopStockChest = chest == null ? null : chest.immutable();
    }
    @Nullable public BlockPos caravanPickup() { return caravanPickup; }
    @Nullable public BlockPos caravanDropoff() { return caravanDropoff; }
    public void setCaravanPickup(@Nullable BlockPos pos) {
        caravanPickup = pos == null ? null : pos.immutable();
    }
    public void setCaravanDropoff(@Nullable BlockPos pos) {
        caravanDropoff = pos == null ? null : pos.immutable();
    }
    @Nullable public UUID escortLeader() { return escortLeader; }
    public int roamingRadius() { return roamingRadius; }
    public void setRoamingRadius(int next) { roamingRadius = MovementPolicy.radius(next); }
    public boolean naturalResident() { return naturalResident; }
    public int dailyShiftTicks() { return dailyShiftTicks; }
    @Nullable public BlockPos cachedCropTarget() { return cachedCropTarget; }
    @Nullable public BlockPos miningTarget() { return miningTarget; }
    public void setMiningTarget(@Nullable BlockPos target) {
        miningTarget = target == null ? null : target.immutable();
        miningProgress = 0.0F;
    }
    public void clearMiningTarget() { miningTarget = null; miningProgress = 0.0F; }
    public Set<UUID> pendingMiningDrops() {
        return java.util.Collections.unmodifiableSet(ownedMiningDrops.keySet());
    }
    public int remainingMiningDrop(UUID id) {
        return ownedMiningDrops.getOrDefault(id, 0);
    }
    public void recordMinedPickup(UUID id, int count) {
        if (count <= 0) return;
        int remaining = remainingMiningDrop(id) - count;
        if (remaining <= 0) ownedMiningDrops.remove(id);
        else ownedMiningDrops.put(id, remaining);
    }
    public long miningLootExpiry() { return miningLootExpiry; }
    public long miningLootStarted() { return miningLootStarted; }
    public void claimMiningDrops(Map<UUID, Integer> ids, long expiry) {
        ownedMiningDrops.clear();
        for (Map.Entry<UUID, Integer> entry : ids.entrySet()) {
            if (ownedMiningDrops.size() >= 24) break;
            int amount = Math.clamp(entry.getValue(), 0, 2304);
            if (amount > 0) ownedMiningDrops.put(entry.getKey(), amount);
        }
        miningLootExpiry = Math.max(0L, expiry);
        miningLootStarted = level().getGameTime();
    }
    public void forgetMiningDrop(UUID id) { ownedMiningDrops.remove(id); }
    public void clearMiningDrops() {
        ownedMiningDrops.clear();
        miningLootExpiry = 0L;
        miningLootStarted = 0L;
    }
    public void resetMiningProgress() { miningProgress = 0.0F; }
    public float miningProgress() { return miningProgress; }
    public float advanceMiningProgress(float increment) {
        miningProgress = Math.clamp(miningProgress + increment, 0.0F, 1.0F);
        return miningProgress;
    }
    public void setCachedCropTarget(@Nullable BlockPos target) { cachedCropTarget = target; }
    /** Checks measured foot-cell transition, not a repeated tick at a camp.
     * Transit heatmap additionally throttles repeat visits by resident UUID.
     */
    public boolean hasNewFootTrafficPosition(BlockPos foot) {
        if (lastFootTrafficSample == null) {
            lastFootTrafficSample = foot.immutable();
            return false; // never treat initial spawn as pedestrian passage
        }
        if (lastFootTrafficSample.equals(foot)) return false;
        lastFootTrafficSample = foot.immutable();
        return true;
    }
    public boolean canRepath(BlockPos target, long tick) {
        if (tick >= nextNavRepathTick || !target.equals(lastNavDestination) &&
            tick + 8 >= nextNavRepathTick) {
            lastNavDestination = target.immutable();
            nextNavRepathTick = tick + 30;
            return true;
        }
        return false;
    }
    public int formationSlot() { return formationSlot; }
    public void setFormationSlot(int slot) { formationSlot = Math.clamp(slot, 0, 15); }
    public void setEscortLeader(@Nullable UUID uuid) { escortLeader = uuid; }
    @Nullable public BlockPos exploreTarget() { return exploreTarget; }
    public void setExploreTarget(@Nullable BlockPos pos) {
        exploreTarget = pos == null ? null : pos.immutable();
    }
    public int parcelsDelivered() { return parcelsDelivered; }
    public SafeBuilder.Template buildTemplate() { return buildTemplate; }
    public void setBuildTemplate(SafeBuilder.Template type) {
        if (buildTemplate != type) {
            buildEnabled = false; // switching blueprints cannot replay a half-built building
            buildCursor = 0;
            buildTemplate = type;
        }
    }
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
    @Nullable public BlockPos assignedBedPos() { return assignedBedPos; }
    public void setAssignedBedPos(@Nullable BlockPos pos) {
        assignedBedPos = pos == null ? null : pos.immutable();
    }
    public BlockPos workPos() { return workPos; }
    public LivingPlanner planner() { return planner; }
    public LivingPlanner.Personality personality() { return personality; }
    public void setPersonality(LivingPlanner.Personality traits) {
        personality = traits;
        planner = new LivingPlanner(traits);
    }
    public UUID recentAggressor() { return recentAggressor; }
    public boolean isAfraid(long tick) { return recentAggressor != null && tick < afraidUntil; }
    public long nextRaidTick() { return nextRaidTick; }
    @Nullable public UUID policeSuspect() { return policeSuspect; }
    public long policeAlertUntil() { return policeAlertUntil; }
    @Nullable public BlockPos policeJail() { return policeJail; }
    public void setPoliceJail(@Nullable BlockPos jail) {
        policeJail = jail == null ? null : jail.immutable();
    }
    public void assignPoliceCase(UUID suspect, long until) {
        policeSuspect = suspect;
        policeAlertUntil = Math.max(0L, until);
    }
    public void clearPoliceCase() {
        policeSuspect = null;
        policeAlertUntil = 0L;
    }
    public boolean isDetained(long tick) {
        return detentionJail != null && tick < detainedUntil;
    }
    @Nullable public BlockPos detentionJail() { return detentionJail; }
    public long detainedUntil() { return detainedUntil; }
    public void detainAt(BlockPos jail, long until) {
        detentionJail = jail.immutable();
        detainedUntil = Math.max(0L, until);
        clearMiningTarget();
        getNavigation().stop();
        setTarget(null);
    }
    public void releaseDetention() {
        detentionJail = null;
        detainedUntil = 0L;
    }
    public void postponeRaid(long tick) { nextRaidTick = Math.max(nextRaidTick, tick); }
    /** Non-player assault also has consequences: victim fear and personal
     * memory. Only called after an ACTUAL transfer of existing cargo.
     */
    public void witnessedBanditTheft(PlayerNpcEntity raider, long tick) {
        recentAggressor = raider.getUUID();
        afraidUntil = Math.max(afraidUntil, tick + 240L);
        memory.witness(tick, raider.getUUID(),
            ResidentMemory.Kind.THEFT_WITNESSED, blockPosition().toShortString());
    }
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
            if (level() instanceof ServerLevel server &&
                level().getGameTime() - lastAttackReport >= 100) {
                CivicSocial.witnessedAssault(this, player, server);
                lastAttackReport = level().getGameTime();
            }
            lastAction = "injured by " + player.getName().getString();
        }
        return damaged;
    }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel server) {
            LivingWorldActions.tick(this, server);
            // 5 Hz server classification, only network-synced upon change.
            // Actual per-frame animation stays purely client-render-side.
            if ((server.getGameTime() + getId()) % 4L == 0L)
                setVisualPose(NpcPoseSelection.select(this, server.getGameTime()));
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
        if (profession != Profession.BAKER || !payment.is(Items.EMERALD) ||
            (memory.trust(customer.getUUID()) <= -25 ||
             civic.standing(customer.getUUID(), character.justice()) ==
                CivicMind.Standing.WANTED)) return false;
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
        int freeSlot = customer.getInventory().getFreeSlot();
        if (freeSlot < 0) return false;
        for (int i = 0; i < 36; i++) backpack.setItem(i, snapshot.getItem(i).copy());
        backpack.setChanged();
        payment.shrink(1);
        customer.getInventory().setItem(freeSlot, new ItemStack(Items.BREAD));
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
        if (profession == Profession.BAKER && held.is(Items.EMERALD)
            && (memory.trust(player.getUUID()) <= -25 ||
                civic.standing(player.getUUID(), character.justice()) ==
                    CivicMind.Standing.WANTED)) {
            player.displayClientMessage(Component.literal(getName().getString()
                + " refuses to trade because of your past aggression."), false);
            return InteractionResult.CONSUME;
        }
        if (sellBread(player, held)) {
            player.displayClientMessage(Component.literal("Bought real bread for one emerald"), false);
            return InteractionResult.CONSUME;
        }
        if (profession == Profession.SHOPKEEPER && held.is(Items.EMERALD)) {
            if (ShopCounter.purchase(this, player, held)) {
                player.displayClientMessage(Component.literal(
                    "Shop: paid one emerald; received stocked food item"), false);
            } else {
                player.displayClientMessage(Component.literal(
                    "Shop unavailable: empty stock, inventory full, refusal or too far"), false);
            }
            return InteractionResult.CONSUME;
        }
        if (!isOwnedBy(player)) {
            if (held.isEmpty()) {
                memory.witness(level().getGameTime(), player.getUUID(),
                    ResidentMemory.Kind.GREETING, blockPosition().toShortString());
                player.displayClientMessage(Component.literal(getName().getString()
                    + ": " + conversationWith(player)
                    + " | trust " + memory.trust(player.getUUID())), false);
            } else if (held.is(Items.BREAD) || held.is(Items.CARROT) ||
                    held.is(Items.APPLE) || held.is(Items.BAKED_POTATO)) {
                ItemStack gift = held.copyWithCount(1);
                if (backpack.canAddItem(gift)) {
                    backpack.addItem(gift);
                    held.shrink(1);
                    memory.witness(level().getGameTime(), player.getUUID(),
                        ResidentMemory.Kind.GIFT, blockPosition().toShortString());
                    CivicSocial.giveCredit(this, player, CivicMind.Kind.GIFT);
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
                + ": " + conversationWith(player) + " | " + intent()
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
            CivicSocial.giveCredit(this, player, CivicMind.Kind.GIFT);
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
        if (caravanPickup != null) tag.putLong("NpcCaravanPickup", caravanPickup.asLong());
        if (caravanDropoff != null) tag.putLong("NpcCaravanDropoff", caravanDropoff.asLong());
        if (shopStockChest != null) tag.putLong("NpcShopStockChest", shopStockChest.asLong());
        if (escortLeader != null) tag.putUUID("NpcEscortLeader", escortLeader);
        if (exploreTarget != null) tag.putLong("NpcExploreTarget", exploreTarget.asLong());
        tag.putLongArray("NpcRecentChunks", recentChunks.stream().mapToLong(Long::longValue).toArray());
        tag.putInt("NpcParcels", parcelsDelivered);
        tag.putInt("NpcFormationSlot", formationSlot);
        tag.putInt("NpcRoamingRadius", roamingRadius);
        tag.putInt("NpcSkinId", skinId());
        tag.putLong("NpcNextRaidTick", nextRaidTick);
        if (policeSuspect != null) tag.putUUID("NpcPoliceSuspect", policeSuspect);
        tag.putLong("NpcPoliceAlertUntil", policeAlertUntil);
        if (policeJail != null) tag.putLong("NpcPoliceJail", policeJail.asLong());
        if (detentionJail != null) tag.putLong("NpcDetentionJail", detentionJail.asLong());
        tag.putLong("NpcDetainedUntil", detainedUntil);
        if (miningTarget != null) tag.putLong("NpcMiningTarget", miningTarget.asLong());
        if (!ownedMiningDrops.isEmpty()) {
            ListTag claims = new ListTag();
            for (Map.Entry<UUID, Integer> item : ownedMiningDrops.entrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("Id", item.getKey());
                entry.putInt("Remaining", item.getValue());
                claims.add(entry);
            }
            tag.put("NpcMiningDrops", claims);
            tag.putLong("NpcMiningDropExpiry", miningLootExpiry);
            tag.putLong("NpcMiningDropStarted", miningLootStarted);
        }
        tag.putBoolean("NpcNaturallySpawned", naturalResident);
        tag.putInt("NpcDailyShift", dailyShiftTicks);
        tag.putBoolean("NpcBuildEnabled", buildEnabled);
        tag.putInt("NpcBuildCursor", buildCursor);
        tag.putString("NpcBuildTemplate", buildTemplate.name());
        tag.putInt("NpcDiligence", personality.diligence());
        tag.putInt("NpcSociability", personality.sociability());
        tag.putInt("NpcCaution", personality.caution());
        tag.putLong("NpcHomePos", homePos.asLong());
        tag.putLong("NpcWorkPos", workPos.asLong());
        if (assignedBedPos != null) tag.putLong("NpcAssignedBedHead", assignedBedPos.asLong());
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
        CivicPersistence.save(this, tag);
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
        caravanPickup = tag.contains("NpcCaravanPickup", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcCaravanPickup")) : null;
        caravanDropoff = tag.contains("NpcCaravanDropoff", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcCaravanDropoff")) : null;
        shopStockChest = tag.contains("NpcShopStockChest", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcShopStockChest")) : null;
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
        formationSlot = Math.clamp(tag.getInt("NpcFormationSlot"), 0, 15);
        miningTarget = tag.contains("NpcMiningTarget", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcMiningTarget")) : null;
        nextRaidTick = Math.max(0L, tag.getLong("NpcNextRaidTick"));
        policeSuspect = tag.hasUUID("NpcPoliceSuspect")
            ? tag.getUUID("NpcPoliceSuspect") : null;
        policeAlertUntil = Math.max(0L, tag.getLong("NpcPoliceAlertUntil"));
        policeJail = tag.contains("NpcPoliceJail", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcPoliceJail")) : null;
        detentionJail = tag.contains("NpcDetentionJail", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcDetentionJail")) : null;
        detainedUntil = Math.max(0L, tag.getLong("NpcDetainedUntil"));
        if (detentionJail != null && detainedUntil <= level().getGameTime())
            releaseDetention();
        miningProgress = 0.0F;
        ownedMiningDrops.clear();
        miningLootExpiry = Math.max(0L, tag.getLong("NpcMiningDropExpiry"));
        miningLootStarted = Math.max(0L, tag.getLong("NpcMiningDropStarted"));
        if (tag.contains("NpcMiningDrops", Tag.TAG_LIST)) {
            ListTag claims = tag.getList("NpcMiningDrops", Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(24, claims.size()); i++) {
                CompoundTag entry = claims.getCompound(i);
                if (entry.hasUUID("Id")) {
                    int count = Math.clamp(entry.getInt("Remaining"), 0, 2304);
                    if (count > 0) ownedMiningDrops.put(entry.getUUID("Id"), count);
                }
            }
        }
        setSkinId(tag.contains("NpcSkinId", Tag.TAG_INT)
            ? tag.getInt("NpcSkinId") : Math.floorMod(getUUID().hashCode(), 6));
        setRoamingRadius(tag.contains("NpcRoamingRadius", Tag.TAG_INT)
            ? tag.getInt("NpcRoamingRadius") : 48);
        naturalResident = tag.getBoolean("NpcNaturallySpawned");
        dailyShiftTicks = Math.clamp(tag.getInt("NpcDailyShift"), -400, 400);
        cachedCropTarget = null;
        lastFootTrafficSample = null;
        lastNavDestination = BlockPos.ZERO;
        nextNavRepathTick = 0;
        buildEnabled = tag.getBoolean("NpcBuildEnabled");
        buildCursor = Math.clamp(tag.getInt("NpcBuildCursor"), 0, 100);
        try { buildTemplate = SafeBuilder.Template.valueOf(tag.getString("NpcBuildTemplate")); }
        catch (IllegalArgumentException ignored) {
            buildTemplate = SafeBuilder.Template.SHELTER;
        }
        setPersonality(new LivingPlanner.Personality(
            Math.clamp(tag.contains("NpcDiligence") ? tag.getInt("NpcDiligence") : 50, 0, 100),
            Math.clamp(tag.contains("NpcSociability") ? tag.getInt("NpcSociability") : 50, 0, 100),
            Math.clamp(tag.contains("NpcCaution") ? tag.getInt("NpcCaution") : 50, 0, 100)));
        homePos = tag.contains("NpcHomePos", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcHomePos")) : blockPosition();
        workPos = tag.contains("NpcWorkPos", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcWorkPos")) : blockPosition();
        assignedBedPos = tag.contains("NpcAssignedBedHead", Tag.TAG_LONG)
            ? BlockPos.of(tag.getLong("NpcAssignedBedHead")) : null;
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
        CivicPersistence.load(this, tag);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Containers.dropContents(level, this, backpack);
        backpack.clearContent();
        if (commission != null &&
            (commission.state() == Commission.State.OPEN ||
             commission.state() == Commission.State.ACCEPTED)) {
            this.spawnAtLocation(new ItemStack(Items.EMERALD, commission.emeraldReward()));
            commission.cancel();
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
