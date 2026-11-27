package com.ronec.chronofactory;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

final class TeleportReplay{
    static CompoundTag coordinates(Vec3 v){CompoundTag a=new CompoundTag();a.putDouble("x",v.x);a.putDouble("y",v.y);a.putDouble("z",v.z);return a;}
    static void teleport(EntityTeleportEvent e){
        if(e.isCanceled()||!(e.getEntity() instanceof ServerPlayer p))return;
        String cause=e instanceof EntityTeleportEvent.TeleportCommand?"command":e instanceof EntityTeleportEvent.EnderPearl?"pearl":"other";
        if(p instanceof FakePlayer){
            if("command".equals(cause)){CompoundTag job=ChronoFactory.echoData(p.serverLevel(),p.getUUID());if(job!=null)job.put("commandLanding",coordinates(e.getTarget()));}
            return;
        }
        ChronoFactory.Recording r=ChronoFactory.RECORDINGS.get(p.getUUID());if(r!=null)r.teleportKind=cause;
    }
    static boolean recordSample(ServerPlayer p,ChronoFactory.Recording r){
        if(r.teleportKind.isEmpty())return false;
        String cause=r.teleportKind;r.teleportKind="";
        if(p.position().distanceToSqr(r.lastRawPos)<.000001)return false;
        CompoundTag action=ChronoFactory.point(p,"teleport");action.putString("cause",cause);r.add(action);r.last=ChronoFactory.point(p,"move");r.lastMoveTick=r.tick;
        if(r.gui.size()<4096){CompoundTag raw=action.copy();raw.putString("kind","teleport");raw.putInt("tick",r.raw.size()/3-1);r.gui.add(raw);}
        return true;
    }
    static Vec3 destination(CompoundTag job,CompoundTag action,Vec3 here){
        String cause=action.getString("cause");
        if("pearl".equals(cause))return job.contains("pearlLanding")?ChronoFactory.vec(job.getCompound("pearlLanding")):null;
        if("command".equals(cause))return job.contains("commandLanding")?ChronoFactory.vec(job.getCompound("commandLanding")):null;
        // Other teleport mechanics must actually succeed; no arbitrary free teleport.
        return here.distanceToSqr(ChronoFactory.vec(action))<.1?here:null;
    }
    static void consumed(CompoundTag job,CompoundTag action){if("pearl".equals(action.getString("cause")))job.remove("pearlLanding");if("command".equals(action.getString("cause")))job.remove("commandLanding");}
}
