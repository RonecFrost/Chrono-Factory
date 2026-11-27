package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative playback cursor controls. Seeking never rewinds world
 * or inventory state, and never teleports an Echo to a skipped waypoint. */
final class PlaybackControls{
    static int[] boundaries(CompoundTag tl){
        int end=tl.getIntArray("raw").length/3;TreeSet<Integer> ticks=new TreeSet<>();
        if(end>0)ticks.add(0);
        for(Tag x:ChronoFactory.timeline(tl)){int n=((CompoundTag)x).getInt("recordTick");if(n>=0&&n<end)ticks.add(n);}
        for(Tag x:tl.getList("gui",Tag.TAG_COMPOUND)){int n=((CompoundTag)x).getInt("tick");if(n>=0&&n<end)ticks.add(n);}
        return ticks.stream().mapToInt(Integer::intValue).toArray();
    }
    static int target(CompoundTag a,int direction){
        CompoundTag tl=a.getCompound("timeline");
        if(a.getBoolean("dismiss")||a.getInt("phase")<24||a.getInt("fade")>0)return -1;
        if(!tl.getBoolean("strict")){
            int next=a.getInt("index")+direction;
            return next>=0&&next<ChronoFactory.timeline(tl).size()?next:-1;
        }
        int now=a.getInt("rawIndex");int[] ticks=boundaries(tl);
        // Previous means previous task boundary, not the previous raw frame.
        int current=0;for(int k=0;k<ticks.length;k++){if(ticks[k]>now)break;current=k;}
        int dest=current+direction;return dest>=0&&dest<ticks.length?ticks[dest]:-1;
    }
    static void status(CompoundTag a,CompoundTag out){
        out.putBoolean("paused",a.getBoolean("paused"));out.putBoolean("canPrev",target(a,-1)>=0);out.putBoolean("canNext",target(a,1)>=0);
        CompoundTag tl=a.getCompound("timeline");boolean strict=tl.getBoolean("strict");out.putBoolean("strict",strict);
        out.putInt("step",strict?a.getInt("rawIndex"):Math.min(a.getInt("index")+1,ChronoFactory.timeline(tl).size()));
        out.putInt("total",strict?tl.getIntArray("raw").length/3:ChronoFactory.timeline(tl).size());
        out.putString("reason",a.getString("reason"));out.putString("name",tl.getString("name"));out.putInt("cycle",a.getInt("cycles")+1);
        ListTag actions=ChronoFactory.timeline(tl);int index=a.getInt("index");out.putString("task",strict?a.getString("strictTask"):index<actions.size()?actions.getCompound(index).getString("type"):"Remanifesting");
    }
    static void watchStatus(ServerPlayer p,UUID watch,CompoundTag out){
        ListTag rows=new ListTag();int paused=0;
        for(ServerLevel l:p.server.getAllLevels())for(Tag x:ChronoFactory.world(l).echoes){
            CompoundTag a=(CompoundTag)x;if(a.getBoolean("dismiss")||!a.getUUID("owner").equals(p.getUUID())||!a.getUUID("watch").equals(watch))continue;
            CompoundTag row=new CompoundTag();row.putUUID("id",a.getUUID("id"));status(a,row);rows.add(row);if(a.getBoolean("paused"))paused++;
        }
        out.put("jobs",rows);out.putInt("pausedCount",paused);
    }
    static void pauseWatch(ServerPlayer p,UUID watch,boolean pause){
        for(ServerLevel l:p.server.getAllLevels())for(Tag x:ChronoFactory.world(l).echoes){
            CompoundTag a=(CompoundTag)x;if(a.getBoolean("dismiss")||!a.getUUID("owner").equals(p.getUUID())||!a.getUUID("watch").equals(watch))continue;
            a.putBoolean("paused",pause);ChronoFactory.world(l).setDirty();
        }
    }
    static void seek(ServerLevel l,CompoundTag a,int destination){
        if(destination<0)return;
        CompoundTag tl=a.getCompound("timeline");boolean strict=tl.getBoolean("strict");
        // Return any physical items held in an abandoned native crafting/menu
        // cursor before abandoning that operation. Do not discard or recreate them.
        if(strict){
            StrictReplay.MacroPlayer f=StrictReplay.player(l,a.getUUID("id"));f.getInventory().clearContent();f.getInventory().load(a.getList("inventory",Tag.TAG_COMPOUND));
            var pos=ChronoFactory.vec(a.getCompound("position"));f.setPos(pos.x,pos.y,pos.z);
            StrictReplay.restoreCursor(f,a);StrictReplay.finishCycle(f,a);
            a.put("inventory",f.getInventory().save(new ListTag()));f.getInventory().clearContent();EchoInventoryMenu.syncFromJob(l,a.getUUID("id"));
            a.putInt("rawIndex",destination);ListTag gui=tl.getList("gui",Tag.TAG_COMPOUND);int cursor=0;while(cursor<gui.size()&&gui.getCompound(cursor).getInt("tick")<destination)cursor++;a.putInt("guiIndex",cursor);a.putString("strictTask","Selected recorded task boundary");
        }else a.putInt("index",destination);
        for(String k:new String[]{"progress","transferred","waited","heldElapsed","useCursor","brokenForHold","crafted","itemStarted","itemFired","segmentSpeed","segmentIndex","sweepPos","sweepProgress","strictHeldTicks","strictHeldItem","strictHeldOff","strictMinePos","strictMineProgress","strictUseDelay","strictAttackDelay"})a.remove(k);
        a.putString("reason","Task selected; prior world and inventory changes retained");
    }
    static void direct(ServerPlayer p,ChronoFactory.UiCommand cmd){
        for(ServerLevel l:p.server.getAllLevels()){
            CompoundTag a=ChronoFactory.echoData(l,cmd.key());if(a==null||a.getBoolean("dismiss"))continue;
            boolean owner=a.getUUID("owner").equals(p.getUUID());
            boolean nearby=p.level()==l&&p.position().distanceToSqr(ChronoFactory.vec(a.getCompound("position")))<=64;
            boolean remote=owner&&!ChronoFactory.find(p,a.getUUID("watch")).isEmpty();
            if(!(nearby&&(owner||p.hasPermissions(2)))&&!remote)return;
            switch(cmd.action()){
                case "echo_pause" -> a.putBoolean("paused",true);
                case "echo_resume" -> a.putBoolean("paused",false);
                case "echo_prev","echo_next" -> {
                    int dest=target(a,cmd.action().equals("echo_prev")?-1:1);
                    if(dest>=0){
                        if(!EchoInventoryMenu.isOpen(l,cmd.key()))a.putBoolean("paused",true);
                        seek(l,a,dest);
                    }
                }
                default -> {return;}
            }
            ChronoFactory.world(l).setDirty();
            if(nearby)ChronoFactory.inspect(p,cmd.key(),false);
            if(remote&&!(p.containerMenu instanceof EchoInventoryMenu)){ItemStack watch=ChronoFactory.find(p,a.getUUID("watch"));if(!watch.isEmpty())ChronoFactory.sendUi(p,watch);}
            return;
        }
    }
}
