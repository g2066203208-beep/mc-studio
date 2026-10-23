package dev.reme.mcstudio.npc.test;

import dev.reme.mcstudio.StudioMod;
import dev.reme.mcstudio.npc.NpcRegistry;
import dev.reme.mcstudio.npc.PlayerNpcEntity;
import dev.reme.mcstudio.npc.life.PublicSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real NeoForge 1.21.1 server tests: a jail chunk outside loaded
 * simulation distance is NOT a demolished holding cell. Neither the
 * sentence nor the persisted arresting-officer UUID may disappear.
 * No fake loaded chunk, teleport, or forced chunk ticket is allowed.
 */
@GameTestHolder(StudioMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnloadedJailCustodyGameTests {
    private UnloadedJailCustodyGameTests() {}

    private static PlayerNpcEntity spawn(ServerLevel level, BlockPos feet,
                                         PlayerNpcEntity.Profession profession) {
        PlayerNpcEntity actor=NpcRegistry.PLAYER_NPC.get().create(level);
        if(actor==null)throw new AssertionError("Real NPC registry unavailable");
        actor.setMode(PlayerNpcEntity.Mode.LIVE);
        actor.setProfession(profession);
        actor.setHomePos(feet);
        actor.setRoamingRadius(64);
        actor.moveTo(feet.getX()+0.5,feet.getY(),feet.getZ()+0.5,0,0);
        if(!level.addFreshEntity(actor))
            throw new AssertionError("Real actor spawn failed at "+feet);
        return actor;
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void remote_unloaded_jail_preserves_officer_and_prisoner_custody(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos feet=helper.absolutePos(new BlockPos(8,2,8));
        // A distant, not force-loaded jail. Real city NPCs can be separated
        // from the station by more than the server's simulation distance.
        BlockPos remoteJail=feet.offset(2048,0,2048);
        if(level.hasChunkAt(remoteJail))
            throw new AssertionError("Test setup unexpectedly loaded remote jail");
        PlayerNpcEntity prisoner=spawn(level,feet,
                                       PlayerNpcEntity.Profession.RESIDENT);
        PlayerNpcEntity officer=spawn(level,feet.east(2),
                                      PlayerNpcEntity.Profession.GUARD);
        long now=level.getGameTime();
        prisoner.detainAt(remoteJail,now+500);
        prisoner.assignCustodyOfficer(officer.getUUID());
        officer.assignPrisonerEscort(prisoner.getUUID(),now+500);
        if(!PublicSafety.tickPrisoner(prisoner,level,now))
            throw new AssertionError("Unloaded jail silently released prisoner");
        if(!PublicSafety.tickGuard(officer,level,now))
            throw new AssertionError("Unloaded jail discarded real officer escort");
        if(!prisoner.isDetained(now)
            || !remoteJail.equals(prisoner.detentionJail())
            || !officer.getUUID().equals(prisoner.custodyOfficer())
            || !prisoner.getUUID().equals(officer.escortedPrisoner()))
            throw new AssertionError("Remote chunk corrupted reciprocal custody");
        if(!prisoner.lastAction().contains("jail chunk unloaded")
            || !officer.lastAction().contains("jail chunk unloaded"))
            throw new AssertionError("Unloaded custody must visibly pause, not ghost walk");
        CompoundTag saved=new CompoundTag();
        prisoner.saveWithoutId(saved);
        if(!saved.hasUUID("NpcCustodyOfficer")
            || !officer.getUUID().equals(saved.getUUID("NpcCustodyOfficer")))
            throw new AssertionError("Unloaded custody officer identity not saved");
        if(level.hasChunkAt(remoteJail))
            throw new AssertionError("Custody code force-loaded a distant jail chunk");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void loaded_destroyed_jail_releases_without_ghost_detention(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos feet=helper.absolutePos(new BlockPos(8,2,8));
        BlockPos jail=feet.south(3);
        level.setBlock(jail.below(),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(jail,Blocks.AIR.defaultBlockState(),2);
        level.setBlock(jail.above(),Blocks.AIR.defaultBlockState(),2);
        for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL)
            level.setBlock(jail.relative(dir),Blocks.AIR.defaultBlockState(),2);
        if(!level.hasChunkAt(jail) || PublicSafety.holdingSpot(level,jail))
            throw new AssertionError("Destroyed cell fixture not loaded/invalid");
        PlayerNpcEntity prisoner=spawn(level,feet,
                                       PlayerNpcEntity.Profession.RESIDENT);
        long now=level.getGameTime();
        prisoner.detainAt(jail,now+500);
        prisoner.assignCustodyOfficer(java.util.UUID.randomUUID());
        if(PublicSafety.tickPrisoner(prisoner,level,now)
            || prisoner.isDetained(now)
            || prisoner.detentionJail()!=null
            || prisoner.custodyOfficer()!=null)
            throw new AssertionError("Destroyed loaded jail retained ghost custody");
        helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100)
    public static void sentence_expiry_does_not_force_load_distant_jail(
        GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        BlockPos feet=helper.absolutePos(new BlockPos(8,2,8));
        BlockPos remoteJail=feet.offset(2048,0,-2048);
        if(level.hasChunkAt(remoteJail))
            throw new AssertionError("Remote jail must be unloaded");
        PlayerNpcEntity prisoner=spawn(level,feet,
                                       PlayerNpcEntity.Profession.RESIDENT);
        long now=level.getGameTime();
        prisoner.detainAt(remoteJail,now);
        prisoner.assignCustodyOfficer(java.util.UUID.randomUUID());
        if(PublicSafety.tickPrisoner(prisoner,level,now)
            || prisoner.detentionJail()!=null
            || prisoner.custodyOfficer()!=null
            || level.hasChunkAt(remoteJail))
            throw new AssertionError("Expired sentence remained active or loaded chunks");
        helper.succeed();
    }
}
