package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
final class MenuTransactions{
    static Map<String,ItemStack> exactSnapshot(ServerPlayer p){Map<String,ItemStack> out=new HashMap<>();for(int k=0;k<41;k++){ItemStack st=p.getInventory().getItem(k);if(st.isEmpty()||ChronoFactory.watch(st))continue;String key=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem())+":"+st.copyWithCount(1).getComponentsPatch();out.compute(key,(a,b)->{if(b==null)return st.copy();b.grow(st.getCount());return b;});}return out;}
    static void begin(ServerPlayer p,ChronoFactory.Recording r,CompoundTag use){r.transaction=use.copy();r.transaction.put("clicks",new ListTag());r.transactionBefore=exactSnapshot(p);r.transactionLevels=p.experienceLevel;}
    static void cancelUnopened(ChronoFactory.Recording r){if(r.transaction!=null&&!r.transaction.getBoolean("opened")){r.transaction=null;r.transactionBefore=null;}}
    static void tick(ServerPlayer p,ChronoFactory.Recording r){
        if(r.transaction==null)return;
        if(p.containerMenu!=p.inventoryMenu){r.transaction.putBoolean("opened",true);r.transaction.putString("menuClass",p.containerMenu.getClass().getName());if(p.containerMenu instanceof EnchantmentMenu||p.containerMenu instanceof AnvilMenu||p.containerMenu instanceof GrindstoneMenu)r.transaction.putBoolean("enchantMenu",true);return;}
        if(r.transaction.getBoolean("opened"))finish(p,r);
        else if(r.tick>r.transaction.getInt("recordTick")){CompoundTag action=r.transaction;r.transaction=null;r.transactionBefore=null;action.remove("clicks");r.add(action);}
    }
    static void click(ServerPlayer p,ChronoFactory.Recording r,int slot,int button,int click){
        if(r.transaction==null||p.containerMenu==p.inventoryMenu)return;
        r.transaction.putBoolean("opened",true);r.transaction.putString("menuClass",p.containerMenu.getClass().getName());if(p.containerMenu instanceof EnchantmentMenu||p.containerMenu instanceof AnvilMenu||p.containerMenu instanceof GrindstoneMenu)r.transaction.putBoolean("enchantMenu",true);
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
        r.transaction.putBoolean("opened",true);r.transaction.putString("menuClass",p.containerMenu.getClass().getName());if(p.containerMenu instanceof EnchantmentMenu||p.containerMenu instanceof AnvilMenu||p.containerMenu instanceof GrindstoneMenu)r.transaction.putBoolean("enchantMenu",true);ListTag clicks=r.transaction.getList("clicks",Tag.TAG_COMPOUND);if(clicks.size()>=512)return;
        CompoundTag a=new CompoundTag();a.putBoolean("menuButton",true);a.putInt("button",button);clicks.add(a);
    }
    static void finish(ServerPlayer p,ChronoFactory.Recording r){
        CompoundTag a=r.transaction;if(a==null)return;r.transaction=null;
        if(a.getBoolean("enchantMenu")){r.transactionBefore=null;return;}
        if(!a.getBoolean("opened")){a.remove("clicks");r.add(a);return;}
        Map<String,ItemStack> after=exactSnapshot(p),before=r.transactionBefore;r.transactionBefore=null;if(before==null)return;
        ListTag inputs=new ListTag(),outputs=new ListTag();Set<String> keys=new HashSet<>(before.keySet());keys.addAll(after.keySet());
        for(String k:keys){ItemStack b=before.getOrDefault(k,ItemStack.EMPTY),n=after.getOrDefault(k,ItemStack.EMPTY);int delta=n.getCount()-b.getCount();if(delta==0)continue;CompoundTag row=new CompoundTag();row.put("item",(delta<0?b:n).copyWithCount(1).save(p.registryAccess()));row.putInt("count",Math.abs(delta));(delta<0?inputs:outputs).add(row);}
        int levels=Math.max(0,r.transactionLevels-p.experienceLevel);
        if(inputs.isEmpty()&&outputs.isEmpty()&&levels==0)return;
        a.putString("type","menu_transaction");a.put("inputs",inputs);a.put("outputs",outputs);a.putInt("levels",levels);
        // Observed net deltas are usable only for a closed server menu with
        // real consumed items and actual outputs, not external pickups/drops.
        a.putBoolean("observedDelta",!a.getBoolean("externalInventoryChange")&&!inputs.isEmpty()&&!outputs.isEmpty()&&a.contains("menuClass"));r.add(a);
    }
    private static boolean exact(ItemStack a,ItemStack b){return !a.isEmpty()&&!b.isEmpty()&&ItemStack.isSameItemSameComponents(a,b);}
    private static boolean insert(List<ItemStack> inv,ItemStack st){
        ItemStack left=st.copy();for(ItemStack into:inv){if(left.isEmpty())return true;if(exact(into,left)){int n=Math.min(left.getCount(),into.getMaxStackSize()-into.getCount());if(n>0){into.grow(n);left.shrink(n);}}}
        for(int k=0;k<inv.size()&&!left.isEmpty();k++)if(inv.get(k).isEmpty()){int n=Math.min(left.getCount(),left.getMaxStackSize());inv.set(k,left.copyWithCount(n));left.shrink(n);}return left.isEmpty();
    }
    static ItemActions.Outcome observed(ServerLevel l,StrictReplay.MacroPlayer f,CompoundTag action){
        List<ItemStack> staged=new ArrayList<>();for(int k=0;k<36;k++)staged.add(f.getInventory().getItem(k).copy());
        for(Tag x:action.getList("inputs",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)x;ItemStack wanted=ItemStack.parseOptional(l.registryAccess(),row.getCompound("item"));int left=row.getInt("count");
            if(wanted.isEmpty()||ChronoFactory.watch(wanted)||left<=0)return new ItemActions.Outcome(false,false,"Invalid recorded transaction input");
            for(ItemStack st:staged){if(left==0)break;if(exact(st,wanted)){int n=Math.min(left,st.getCount());st.shrink(n);left-=n;}}
            if(left>0)return new ItemActions.Outcome(false,false,"Missing exact transaction input: "+ChronoFactory.itemName(wanted));
        }
        for(Tag x:action.getList("outputs",Tag.TAG_COMPOUND)){
            CompoundTag row=(CompoundTag)x;ItemStack result=ItemStack.parseOptional(l.registryAccess(),row.getCompound("item"));int left=row.getInt("count");
            if(result.isEmpty()||ChronoFactory.watch(result)||left<=0||left>2304)return new ItemActions.Outcome(false,false,"Invalid recorded transaction output");
            while(left>0){int n=Math.min(left,result.getMaxStackSize());if(!insert(staged,result.copyWithCount(n)))return new ItemActions.Outcome(false,false,"No space for transaction outputs");left-=n;}
        }
        int levels=Math.max(0,action.getInt("levels"));if(f.experienceLevel<levels)return new ItemActions.Outcome(false,false,"Insufficient levels");
        for(int k=0;k<36;k++)f.getInventory().setItem(k,staged.get(k));f.giveExperienceLevels(-levels);
        return new ItemActions.Outcome(true,true,"Applied recorded inventory and XP transaction");
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
        ItemStack held=ChronoFactory.template(action,l);if(!ChronoFactory.equip(f,held))return new ItemActions.Outcome(false,false,"Missing workstation interaction item");
        CompoundTag use=action.copy();use.putInt("selected",f.getInventory().selected);
        if(!StrictReplay.recordedUse(l,f,use)||f.containerMenu==f.inventoryMenu){f.closeContainer();return new ItemActions.Outcome(false,false,"Workstation did not open a server menu");}
        AbstractContainerMenu menu=f.containerMenu;
        if(action.getBoolean("observedDelta")&&ChronoFactory.handler(l,pos)==null&&menu.getClass().getName().equals(action.getString("menuClass"))){
            // Only replay deltas for witnessed, storage-free menus. This is an
            // explicit Adaptive transaction shortcut, not a recipe engine.
            f.closeContainer();return observed(l,f,action);
        }
        Map<String,ItemStack> before=exactSnapshot(f);int beforeLevels=f.experienceLevel;
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
        f.closeContainer();Map<String,ItemStack> after=exactSnapshot(f);boolean changed=beforeLevels!=f.experienceLevel||!before.keySet().equals(after.keySet());if(!changed)for(String k:before.keySet())if(before.get(k).getCount()!=after.get(k).getCount()){changed=true;break;}
        return new ItemActions.Outcome(changed,changed,changed?"Native transaction changed inventory; actual result retained":"Workstation did not produce a transaction");
    }
}
