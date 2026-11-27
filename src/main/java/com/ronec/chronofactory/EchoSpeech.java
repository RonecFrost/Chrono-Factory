package com.ronec.chronofactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.CommandEvent;

final class EchoSpeech{
    static void chat(ServerChatEvent e){if(e.isCanceled())return;record(e.getPlayer(),e.getRawText(),false);}
    static void command(CommandEvent e){
        if(e.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p&&!(p instanceof FakePlayer))record(p,e.getParseResults().getReader().getString(),true);
    }
    static void record(ServerPlayer p,String message,boolean command){
        if(p instanceof FakePlayer||message==null||message.isBlank()||message.length()>256)return;
        ChronoFactory.Recording r=ChronoFactory.RECORDINGS.get(p.getUUID());if(r==null)return;
        // Do not replay management commands that could recursively manipulate timelines.
        String cmd=message.startsWith("/")?message.substring(1):message;
        if(command&&(cmd.equals("chronofactory")||cmd.startsWith("chronofactory ")||cmd.startsWith("chronofactory:")))return;
        r.move(p);CompoundTag a=ChronoFactory.point(p,"speech");a.putString("message",command?cmd:message);a.putBoolean("command",command);a.putInt("ticks",Math.max(5,Math.min(100,message.length()*2)));r.add(a);
        if(r.gui.size()<4096){CompoundTag raw=a.copy();raw.putString("kind","speech");raw.putInt("tick",r.raw.size()/3);r.gui.add(raw);}
    }
    static ItemActions.Outcome perform(ServerLevel l,FakePlayer f,CompoundTag job,CompoundTag tl,CompoundTag a){
        int elapsed=job.getInt("waited")+1;job.putInt("waited",elapsed);
        if(elapsed<Math.max(1,a.getInt("ticks")))return new ItemActions.Outcome(false,true,"Typing...");
        post(l,f,job,tl,a);return new ItemActions.Outcome(true,true,a.getBoolean("command")?"Command attempted":"Message sent");
    }
    static void post(ServerLevel l,FakePlayer f,CompoundTag job,CompoundTag tl,CompoundTag a){
        String message=a.getString("message");if(message.isBlank()||message.length()>256)return;
        ServerPlayer owner=l.getServer().getPlayerList().getPlayer(job.getUUID("owner"));
        String ownerName=owner!=null?owner.getGameProfile().getName():job.getString("name");
        String label=tl.getString("name")+" "+ownerName;
        if(!a.getBoolean("command")){
            l.getServer().getPlayerList().broadcastSystemMessage(Component.literal("<"+label+"> "+message),false);return;
        }
        // Never grant a fake player additional authority, and never run saved
        // privileged commands when the owner is offline. Commands run at echo.
        if(owner==null||message.equals("chronofactory")||message.startsWith("chronofactory ")||message.startsWith("chronofactory:"))return;
        int perm=0;for(int k=1;k<=4;k++)if(owner.hasPermissions(k))perm=k;
        var source=f.createCommandSourceStack().withPermission(perm);
        l.getServer().getCommands().performPrefixedCommand(source,message);
    }
}
