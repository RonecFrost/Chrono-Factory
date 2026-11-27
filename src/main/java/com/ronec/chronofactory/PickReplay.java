package com.ronec.chronofactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;

final class PickReplay{
    static void record(ServerPlayer p){
        if(p instanceof FakePlayer)return;ChronoFactory.Recording r=ChronoFactory.RECORDINGS.get(p.getUUID());if(r==null||p.containerMenu!=p.inventoryMenu)return;
        BlockHitResult hit=ChronoFactory.aimedBlock(p);if(hit.getType()==HitResult.Type.MISS)return;
        BlockPos pos=hit.getBlockPos();ItemStack st=new ItemStack(p.level().getBlockState(pos).getBlock().asItem());if(st.isEmpty()||ChronoFactory.watch(st))return;
        r.move(p);CompoundTag a=ChronoFactory.point(p,"pick_block");a.putLong("pos",pos.asLong());a.putInt("selected",p.getInventory().selected);a.putBoolean("creativePick",p.isCreative());a.put("item",st.save(p.registryAccess()));r.add(a);
        if(r.gui.size()<4096){CompoundTag raw=a.copy();raw.putString("kind","pick_block");raw.putInt("tick",r.raw.size()/3);r.gui.add(raw);}
    }
    static boolean perform(ServerLevel l,FakePlayer f,CompoundTag job,CompoundTag a){
        ItemStack wanted=ChronoFactory.template(a,l);if(wanted.isEmpty())return false;
        if(ChronoFactory.equip(f,wanted))return true;
        ServerPlayer owner=l.getServer().getPlayerList().getPlayer(job.getUUID("owner"));
        if(!a.getBoolean("creativePick")||!job.getBoolean("creative")||owner==null||!owner.isCreative())return false;
        ItemActions.aim(f,a.getFloat("yaw"),a.getFloat("pitch"));
        BlockHitResult hit=ChronoFactory.aimedBlock(f);if(hit.getType()==HitResult.Type.MISS)return false;
        ItemStack actual=new ItemStack(l.getBlockState(hit.getBlockPos()).getBlock().asItem());if(actual.isEmpty()||!ChronoFactory.matches(actual,wanted))return false;
        int slot=-1;for(int k=0;k<9;k++)if(f.getInventory().getItem(k).isEmpty()){slot=k;break;}
        if(slot<0){slot=f.getInventory().selected;ItemStack prior=f.getInventory().getItem(slot);int free=-1;for(int k=9;k<36;k++)if(f.getInventory().getItem(k).isEmpty()){free=k;break;}if(free<0)return false;f.getInventory().setItem(free,prior);}
        f.getInventory().setItem(slot,actual);f.getInventory().selected=slot;return true;
    }
}
