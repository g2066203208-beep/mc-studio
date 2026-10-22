package dev.reme.mcstudio.npc.life;

import dev.reme.mcstudio.npc.PlayerNpcEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * OP-only, bounded 24,000 *real server ticks* city observation. Does NOT
 * accelerate time or fabricate activities. The native /tick rate command,
 * if used by a disposable test world, changes only the real tick cadence.
 *
 * One observer per ServerLevel, at most 32 real loaded actors within 28 blocks
 * of an authorized city anchor, sampled once every 400 ticks. No chunk loads,
 * no persistent timers, no global entity scan, no data broadcast each tick.
 * Once complete the observer stops collecting; report remains in memory for
 * explicit /npc city day status until this server is shut down.
 */
public final class CityDayJournal {
    private CityDayJournal() {}
    public static final long GAME_DAY_TICKS = 24000L;
    private static final int SAMPLE_PERIOD = 400;
    private static final Map<ServerLevel, Recording> SESSIONS = new WeakHashMap<>();

    private static final class Citizen {
        final UUID id;
        final String name;
        final String job;
        BlockPos last;
        int samples;
        int walking;
        int working;
        int home;
        int sleeping;
        int shopping;
        int police;
        int detained;
        int activityChanges;
        String lastAction="";
        Citizen(PlayerNpcEntity p) {
            id=p.getUUID();
            name=p.getName().getString();
            job=p.profession().name();
            last=p.blockPosition();
        }
        void record(PlayerNpcEntity p, long tick) {
            samples++;
            BlockPos pos=p.blockPosition();
            if(pos.distSqr(last)>1) walking++;
            last=pos.immutable();
            if(p.blockPosition().distSqr(p.workPos())<=9
                && (p.profession()!=PlayerNpcEntity.Profession.RESIDENT
                    || p.getTags().contains("reme_city_office_worker"))) working++;
            if(p.blockPosition().distSqr(p.homePos())<=9) home++;
            if(p.isSleeping()) sleeping++;
            if(p.lastAction().contains("buy")||p.lastAction().contains("sold")
                ||p.lastAction().contains("shopping")
                ||p.lastAction().contains("purchased")) shopping++;
            if(p.policeSuspect()!=null||p.escortedPrisoner()!=null) police++;
            if(p.isDetained(tick)) detained++;
            if(!p.lastAction().equals(lastAction)) {
                activityChanges++;
                lastAction=p.lastAction();
            }
        }
        String report(){
            return "name="+name.replace(" ","_")+" profession="+job
                +" samples="+samples+" moved="+walking+" atWork="+working
                +" atHome="+home+" slept="+sleeping+" trade="+shopping
                +" policing="+police+" detained="+detained
                +" actionChanges="+activityChanges
                +" lastAction="+lastAction.replace(" ","_");
        }
    }
    private static final class Recording {
        final BlockPos center;
        final long started;
        final long startDayTime;
        final LinkedHashMap<UUID,Citizen> people=new LinkedHashMap<>();
        long lastSample;
        long accumulatedNanos;
        int samples;
        boolean complete;
        Recording(ServerLevel level,BlockPos anchor){
            center=anchor.immutable();
            started=level.getGameTime();
            startDayTime=level.getDayTime();
            lastSample=started-SAMPLE_PERIOD;
        }
    }

    public static String start(ServerLevel level,BlockPos center) {
        if(!level.getServer().isSameThread()) return "必须在游戏服务器主线程开始";
        Recording current=SESSIONS.get(level);
        if(current!=null && !current.complete)
            return "已有正在进行的真实24,000 tick城市记录，不能重复覆盖";
        Recording rec=new Recording(level,center);
        AABB area=new AABB(center).inflate(28,12,28);
        var people=level.getEntitiesOfClass(PlayerNpcEntity.class,area,
            e->e.isAlive()&&e.getTags().contains("reme_city_citizen"))
            .stream().sorted(Comparator.comparing(e->e.getUUID().toString()))
            .limit(33).toList();
        if(people.size()!=13) return "演示区需要且只能有13位真实城市居民，当前="+people.size();
        for(var p:people)rec.people.put(p.getUUID(),new Citizen(p));
        SESSIONS.put(level,rec);
        System.out.println("REME_NPC_CITY_DAY_START gametime="+rec.started
            +" dayTime="+rec.startDayTime+" citizens="+rec.people.size()
            +" duration="+GAME_DAY_TICKS+" actualServerTicks=true");
        return "已开始跟踪13位真实NPC完整Minecraft昼夜（24,000真实游戏tick），"
            +"每400tick读取实际坐标/工作/睡眠/库存行为，后台记录仅在游戏运行时进行；"
            +"输入 /npc city day status 查看真实进度";
    }

    public static void observe(PlayerNpcEntity npc, ServerLevel level, long tick) {
        if(!npc.getTags().contains("reme_city_citizen"))return;
        Recording rec=SESSIONS.get(level);
        if(rec==null||rec.complete||!rec.people.containsKey(npc.getUUID())
            ||tick-rec.lastSample<SAMPLE_PERIOD)return;
        if(!level.getServer().isSameThread())return;
        long startedNs=System.nanoTime();
        rec.lastSample=tick;
        rec.samples++;
        AABB area=new AABB(rec.center).inflate(28,12,28);
        // Bounded, loaded local entity query; missing/dead NPC is NOT silently
        // treated as an active worker.
        var current=level.getEntitiesOfClass(PlayerNpcEntity.class,area,
            e->e.isAlive()&&rec.people.containsKey(e.getUUID()));
        for(var actor:current)rec.people.get(actor.getUUID()).record(actor,tick);
        rec.accumulatedNanos+=System.nanoTime()-startedNs;
        long elapsed=tick-rec.started;
        if(elapsed>0&&elapsed%2400<SAMPLE_PERIOD){
            int sleeping=(int)current.stream().filter(PlayerNpcEntity::isSleeping).count();
            int atWork=(int)current.stream().filter(p->
                p.blockPosition().distSqr(p.workPos())<=9).count();
            System.out.println("REME_NPC_CITY_DAY_PROGRESS ticks="+elapsed
                +" dayTime="+level.getDayTime()
                +" alive="+current.size()+" sleeping="+sleeping+" atWork="+atWork
                +" scans="+rec.samples);
        }
        if(elapsed>=GAME_DAY_TICKS){
            rec.complete=true;
            int missing=0,inert=0,active=0;
            for(Citizen citizen:rec.people.values()){
                if(citizen.samples==0)missing++;
                if(citizen.walking==0&&citizen.sleeping==0&&citizen.activityChanges<=1)inert++;
                if(citizen.walking>0||citizen.sleeping>0||citizen.activityChanges>1)active++;
                System.out.println("REME_NPC_CITY_DAY_PERSON "+citizen.report());
            }
            long avgMicros=rec.samples==0?0:rec.accumulatedNanos/rec.samples/1000;
            System.out.println("REME_NPC_CITY_DAY_FINISH ticks="+elapsed
                +" startDayTime="+rec.startDayTime+" endDayTime="+level.getDayTime()
                +" residents="+rec.people.size()+" sampled="+rec.samples
                +" missing="+missing+" active="+active+" inert="+inert
                +" journalAvgMicros="+avgMicros
                +" actualServerTicks=true");
        }
    }

    public static String status(ServerLevel level){
        Recording s=SESSIONS.get(level);
        if(s==null)return "当前世界没有城市昼夜跟踪记录；输入 /npc city day start";
        long ticks=s.complete?GAME_DAY_TICKS:
            Math.max(0L,level.getGameTime()-s.started);
        int active=(int)s.people.values().stream().filter(p->
            p.walking>0||p.sleeping>0||p.activityChanges>1).count();
        return "城市真实昼夜记录 "+ticks+"/"+GAME_DAY_TICKS+" tick"
            +"；13位NPC实际采样次数="+s.samples+"；当前已观察到活动人数="+active
            +"；状态="+(s.complete?"完成，可到GitHub日志查看逐人数据":"正在运行")
            +"（非预设动画，也不跨区块强制加载）";
    }
}
