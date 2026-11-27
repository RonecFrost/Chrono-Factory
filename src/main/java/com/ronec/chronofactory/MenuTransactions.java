package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
final class MenuTransactions{
    static void begin(ServerPlayer p,ChronoFactory.Recording r,CompoundTag use){r.transaction=use.copy();r.transaction.put("clicks",new ListTag());r.transactionBefore=ChronoFactory.snapshot(p);r.transactionLevels=p.experienceLevel;}
    static void cancelUnopened(ChronoFactory.Recording r){if(r.transaction!=null&&!r.transaction.getBoolean("opened")){r.transaction=null;r.transactionBefore=null;}}
    static void tick(ServerPlayer p,ChronoFactory.Recording r){
        if(r.transaction==null)return;
        if(p.containerMenu!=p.inventoryMenu){r.transaction.putBoolean("opened",true);return;}
        if(r.transaction.getBoolean("opened"))finish(p,r);
        else if(r.tick>r.transaction.getInt("recordTick")){CompoundTag action=r.transaction;r.transaction=null;r.transactionBefore=null;action.remove("clicks");r.add(action);}
    }
    static void click(ServerPlayer p,ChronoFactory.Recording r,int slot,int button,int click){
        if(r.transaction==null||p.containerMenu==p.inventoryMenu)return;
        r.transaction.putBoolean("opened",true);
        ListTag clicks=r.transaction.getList("clicks",Tag.TAG_COMPOUND);if(clicks.size()>=512)return;
        CompoundTag a=new CompoundTag();a.putInt("slot",slot);a.putInt("button",button);a.putInt("click",click);
        if(slot>=0&&slot<p.containerMenu.slots.size()){
            Slot sl=p.containerMenu.getSlot(slot);
            if(sl.container==p.getInventory()){
                a.putBoolean("player",true);ItemStack st=sl.getItem();if(!st.isEmpty())a.put("wanted",st.copyWithCount(1).save(p.registryAccess()));
            }
        }
        clicks.add(a);
    }
    static void button(ServerPlayer p,ChronoFactory.Recording r,int button){
        if(r.transaction==null||p.containerMenu==p.inventoryMenu)return;
        r.transaction.putBoolean("opened",true);ListTag clicks=r.transaction.getList("clicks",Tag.TAG_COMPOUND);if(clicks.size()>=512)return;
        CompoundTag a=new CompoundTag();a.putBoolean("menuButton",true);a.putInt("button",button);clicks.add(a);
    }
    static void finish(ServerPlayer p,ChronoFactory.Recording r){
        CompoundTag a=r.transaction;if(a==null)return;r.transaction=null;
        if(!a.getBoolean("opened")){a.remove("clicks");r.add(a);return;}
        Map<String,ItemStack> after=ChronoFactory.snapshot(p),before=r.transactionBefore;r.transactionBefore=null;if(before==null)return;
        ListTag inputs=new ListTag(),outputs=new ListTag();Set<String> keys=new HashSet<>(before.keySet());keys.addAll(after.keySet());
        for(String k:keys){ItemStack b=before.getOrDefault(k,ItemStack.EMPTY),n=after.getOrDefault(k,ItemStack.EMPTY);int delta=n.getCount()-b.getCount();if(delta==0)continue;CompoundTag row=new CompoundTag();row.put("item",(delta<0?b:n).copyWithCount(1).save(p.registryAccess()));row.putInt("count",Math.abs(delta));(delta<0?inputs:outputs).add(row);}
        int levels=Math.max(0,r.transactionLevels-p.experienceLevel);
        if(inputs.isEmpty()&&outputs.isEmpty()&&levels==0)return;
        a.putString("type","menu_transaction");a.put("inputs",inputs);a.put("outputs",outputs);a.putInt("levels",levels);r.add(a);
    }
    static ItemActions.Outcome perform(ServerLevel l,StrictReplay.MacroPlayer f,CompoundTag job,CompoundTag action){
        // The generic path deliberately executes the real server-side menu.
        // No output is fabricated from deltas; custom client-only menus need adapters.
        BlockPos pos=BlockPos.of(action.getLong("pos"));
        if(!l.hasChunkAt(pos)||!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(pos).getBlock()).toString().equals(action.getString("block")))return new ItemActions.Outcome(false,false,"Waiting for recorded workstation");
        for(Tag x:action.getList("inputs",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)x;ItemStack st=ItemStack.parseOptional(l.registryAccess(),row.getCompound("item"));
            if(ChronoFactory.carried(f,st)<row.getInt("count"))return new ItemActions.Outcome(false,false,"Missing transaction ingredient: "+ChronoFactory.itemName(st));
        }
        if(f.experienceLevel<action.getInt("levels"))return new ItemActions.Outcome(false,false,"Waiting for XP levels ("+f.experienceLevel+"/"+action.getInt("levels")+")");
        if(!StrictReplay.recordedUse(l,f,action)||f.containerMenu==f.inventoryMenu){f.closeContainer();return new ItemActions.Outcome(false,false,"Workstation did not open a server menu");}
        AbstractContainerMenu menu=f.containerMenu;
        for(Tag x:action.getList("clicks",Tag.TAG_COMPOUND)){
            CompoundTag click=(CompoundTag)x;
            if(click.getBoolean("menuButton")){menu.clickMenuButton(f,click.getInt("button"));continue;}
            int slot=click.getInt("slot"),kind=click.getInt("click");if(kind<0||kind>=ClickType.values().length||slot<0||slot>=menu.slots.size())continue;
            if(click.getBoolean("player")){
                ItemStack wanted=click.contains("wanted")?ItemStack.parseOptional(l.registryAccess(),click.getCompound("wanted")):ItemStack.EMPTY;
                Slot original=menu.getSlot(slot);if(original.container!=f.getInventory())continue;
                if(wanted.isEmpty()?!original.getItem().isEmpty():!ChronoFactory.matches(original.getItem(),wanted)){
                    int replacement=-1;for(int i=0;i<menu.slots.size();i++){Slot test=menu.getSlot(i);if(test.container==f.getInventory()&&(wanted.isEmpty()?test.getItem().isEmpty():ChronoFactory.matches(test.getItem(),wanted))){replacement=i;break;}}
                    if(replacement<0)continue;slot=replacement;
                }
            }
            menu.clicked(slot,click.getInt("button"),ClickType.values()[kind],f);
        }
        f.closeContainer();return new ItemActions.Outcome(true,true,"Workstation transaction attempted; real output retained");
    }
}
