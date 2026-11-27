package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
final class CreativePlayback{
    private static final Map<UUID,List<ItemStack>> BEFORE=new HashMap<>();
    static void mode(FakePlayer f,boolean creative){
        GameType desired=creative?GameType.CREATIVE:GameType.SURVIVAL;
        if(f.gameMode.getGameModeForPlayer()!=desired)f.gameMode.changeGameModeForPlayer(desired);
        f.getAbilities().instabuild=creative;f.getAbilities().invulnerable=creative;
    }
    static void before(ServerPlayer p){
        ChronoFactory.Recording r=ChronoFactory.RECORDINGS.get(p.getUUID());if(r==null||!r.creativeRecording||!p.isCreative())return;
        List<ItemStack> before=new ArrayList<>();for(int k=0;k<41;k++)before.add(p.getInventory().getItem(k).copy());BEFORE.put(p.getUUID(),before);
    }
    static void after(ServerPlayer p){
        List<ItemStack> old=BEFORE.remove(p.getUUID());ChronoFactory.Recording r=ChronoFactory.RECORDINGS.get(p.getUUID());if(old==null||r==null||!p.isCreative())return;
        for(int k=0;k<41;k++){ItemStack st=p.getInventory().getItem(k);if(ItemStack.matches(old.get(k),st)||ChronoFactory.watch(st)||ChronoFactory.watch(old.get(k)))continue;
            CompoundTag a=ChronoFactory.point(p,"creative_slot");a.putInt("slot",k);if(!st.isEmpty())a.put("stack",st.save(p.registryAccess()));r.add(a);
            if(r.gui.size()<TimelineStorage.MAX_GUI){CompoundTag raw=a.copy();raw.putString("kind","creative_slot");raw.putInt("tick",r.raw.size()/3);r.gui.add(raw);}
        }
    }
    static boolean apply(FakePlayer f,CompoundTag a,ServerLevel l){
        int k=a.getInt("slot");if(!f.isCreative()||k<0||k>=41)return false;
        ItemStack st=a.contains("stack")?ItemStack.parseOptional(l.registryAccess(),a.getCompound("stack")):ItemStack.EMPTY;
        if(ChronoFactory.watch(st)||ChronoFactory.watch(f.getInventory().getItem(k)))return false;f.getInventory().setItem(k,st);return true;
    }
}
