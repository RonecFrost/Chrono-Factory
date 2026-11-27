package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** A bounded flight trace, not a second projectile simulator. Real item use
 * creates a real projectile; the recorded per-tick positions guide its motion.
 * Vanilla collision, damage, pickup and impact effects still run. */
final class FlightReplay{
    static final int MAX_FLIGHTS=96,MAX_SAMPLES=160;
    record Capture(Projectile projectile,ChronoFactory.Recording recording,CompoundTag trace,Vec3 origin){}
    record Guided(Projectile projectile,UUID job,int cycle,CompoundTag trace,Vec3 origin,int index){}
    record Context(ServerLevel level,CompoundTag job,CompoundTag timeline,int tick){}
    private static final Map<UUID,Capture> CAPTURE=new HashMap<>();
    private static final Map<UUID,Guided> GUIDED=new HashMap<>();
    private static final ThreadLocal<Context> ACTIVE=new ThreadLocal<>();
    static void context(ServerLevel l,CompoundTag job,CompoundTag tl,int tick){ACTIVE.set(new Context(l,job,tl,tick));}
    static void retime(int tick){Context c=ACTIVE.get();if(c!=null)ACTIVE.set(new Context(c.level,c.job,c.timeline,tick));}
    static void clear(){ACTIVE.remove();}
    static String kind(Projectile p){return BuiltInRegistries.ENTITY_TYPE.getKey(p.getType()).toString();}
    static boolean supported(Projectile p){return p instanceof AbstractArrow||p instanceof ThrowableProjectile||p instanceof FireworkRocketEntity;}
    static void join(EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel l)||!(event.getEntity() instanceof Projectile p)||!supported(p))return;
        var owner=p.getOwner();if(!(owner instanceof ServerPlayer player))return;
        if(!(player instanceof FakePlayer)){
            ChronoFactory.Recording r=ChronoFactory.RECORDINGS.get(player.getUUID());if(r==null||r.flights.size()>=MAX_FLIGHTS)return;
            CompoundTag trace=new CompoundTag();trace.putString("kind",kind(p));trace.putInt("tick",r.tick);trace.put("initial",TeleportReplay.coordinates(p.getDeltaMovement()));trace.put("samples",new ListTag());
            r.flights.add(trace);CAPTURE.put(p.getUUID(),new Capture(p,r,trace,p.position()));return;
        }
        Context c=ACTIVE.get();if(c==null||c.level!=l||!c.job.getUUID("id").equals(player.getUUID())||c.tick<0)return;
        ListTag traces=c.timeline.getList("flights",Tag.TAG_COMPOUND);CompoundTag used=c.job.getCompound("flightUsed");
        for(int k=0;k<traces.size();k++){
            CompoundTag trace=traces.getCompound(k);
            if(used.getBoolean(Integer.toString(k))||!trace.getString("kind").equals(kind(p))||Math.abs(trace.getInt("tick")-c.tick)>1)continue;
            used.putBoolean(Integer.toString(k),true);c.job.put("flightUsed",used);
            GUIDED.put(p.getUUID(),new Guided(p,c.job.getUUID("id"),c.job.getInt("cycles"),trace,p.position(),0));Vec3 velocity=ChronoFactory.vec(trace.getCompound("initial"));if(velocity.lengthSqr()<=256)p.setDeltaMovement(velocity);return;
        }
        // Unguided pearls still need native impact forwarding because a fake
        // player's network connection cannot complete vanilla player teleport.
        if(p instanceof ThrownEnderpearl)GUIDED.put(p.getUUID(),new Guided(p,c.job.getUUID("id"),c.job.getInt("cycles"),new CompoundTag(),p.position(),0));
    }
    static void beforeTick(EntityTickEvent.Pre event){
        if(!(event.getEntity() instanceof Projectile p)||!(p.level() instanceof ServerLevel l))return;
        Guided g=GUIDED.get(p.getUUID());if(g==null)return;
        ListTag points=g.trace.getList("samples",Tag.TAG_COMPOUND);if(g.index>=points.size())return;
        Vec3 target=g.origin.add(ChronoFactory.vec(points.getCompound(g.index))),delta=target.subtract(p.position());
        if(!Double.isFinite(delta.lengthSqr())||delta.lengthSqr()>256||!l.hasChunkAt(BlockPos.containing(target))){GUIDED.remove(p.getUUID());return;}
        p.setDeltaMovement(delta);GUIDED.put(p.getUUID(),new Guided(p,g.job,g.cycle,g.trace,g.origin,g.index+1));
    }
    static void afterTick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof Projectile p)||!(p.level() instanceof ServerLevel l))return;
        Capture c=CAPTURE.get(p.getUUID());if(c==null)return;
        if(ChronoFactory.RECORDINGS.get(p.getOwner()==null?new UUID(0,0):p.getOwner().getUUID())!=c.recording){CAPTURE.remove(p.getUUID());return;}
        ListTag points=c.trace.getList("samples",Tag.TAG_COMPOUND);if(points.size()>=MAX_SAMPLES){CAPTURE.remove(p.getUUID());return;}
        points.add(TeleportReplay.coordinates(p.position().subtract(c.origin)));
    }
    static void impact(ProjectileImpactEvent event){
        if(event.isCanceled()||!(event.getProjectile().level() instanceof ServerLevel l))return;
        Projectile p=event.getProjectile();Capture c=CAPTURE.remove(p.getUUID());
        if(c!=null){HitResult hit=event.getRayTraceResult();c.trace.put("impact",TeleportReplay.coordinates(hit.getLocation().subtract(c.origin)));c.trace.putString("impactType",hit.getType().name());
            if(hit instanceof BlockHitResult block){c.trace.putLong("impactBlock",block.getBlockPos().asLong());c.trace.putInt("face",block.getDirection().get3DDataValue());}
            ListTag points=c.trace.getList("samples",Tag.TAG_COMPOUND);if(points.size()<MAX_SAMPLES)points.add(TeleportReplay.coordinates(hit.getLocation().subtract(c.origin)));
        }
        Guided g=GUIDED.remove(p.getUUID());
        if(p instanceof ThrownEnderpearl&&g!=null){CompoundTag job=ChronoFactory.echoData(l,g.job);
            if(job!=null&&job.getInt("cycles")==g.cycle&&!job.getBoolean("dismiss")){
                Vec3 dest=event.getRayTraceResult().getLocation();job.put("pearlLanding",TeleportReplay.coordinates(dest));ChronoFactory.world(l).setDirty();
            }
        }
    }
    static void clean(ServerLevel l){
        CAPTURE.values().removeIf(c->c.projectile.level()==l&&(c.projectile.isRemoved()||c.projectile.tickCount>1200));
        GUIDED.values().removeIf(g->g.projectile.level()==l&&(g.projectile.isRemoved()||g.projectile.tickCount>1200));
    }
}
