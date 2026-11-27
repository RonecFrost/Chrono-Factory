package com.ronec.chronofactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;

final class EnchantReplay{
    record Capture(EnchantmentMenu menu,BlockPos pos,int option,int required,int level,ItemStack item){}
    static void before(ServerPlayer p,ChronoFactory.Recording r,int option){
        r.enchantCapture=null;
        if(!(p.containerMenu instanceof EnchantmentMenu menu)||r.transaction==null||option<0||option>=3)return;
        r.transaction.putBoolean("enchantMenu",true);ItemStack item=menu.getSlot(0).getItem();
        if(item.isEmpty()||ChronoFactory.watch(item)||menu.costs[option]<=0)return;
        r.enchantCapture=new Capture(menu,BlockPos.of(r.transaction.getLong("pos")),option,menu.costs[option],p.experienceLevel,item.copyWithCount(1));
    }
    static void after(ServerPlayer p,ChronoFactory.Recording r){
        Capture c=r.enchantCapture;r.enchantCapture=null;
        if(c==null||p.containerMenu!=c.menu()||p.experienceLevel>=c.level())return;
        ItemStack result=c.menu().getSlot(0).getItem();if(result.isEmpty()||ItemStack.isSameItemSameComponents(result,c.item()))return;
        CompoundTag a=ChronoFactory.point(p,"enchant");a.putLong("pos",c.pos().asLong());a.putInt("option",c.option());a.putInt("required",c.required());a.putInt("spent",c.level()-p.experienceLevel);
        a.put("item",c.item().save(p.registryAccess()));a.put("observedResult",result.copyWithCount(1).save(p.registryAccess()));r.add(a);
    }
    static ItemStack take(StrictReplay.MacroPlayer f,ItemStack wanted,int n){
        for(int i=0;i<36;i++){ItemStack st=f.getInventory().getItem(i);if(ChronoFactory.matches(st,wanted)&&st.getCount()>=n)return st.split(n);}
        return ItemStack.EMPTY;
    }
    static ItemStack resolveOutput(CompoundTag job,ItemStack wanted,ServerLevel l){
        if(wanted.isEmpty())return wanted;
        for(Tag x:job.getList("enchantedOutputs",Tag.TAG_COMPOUND)){CompoundTag pair=(CompoundTag)x;ItemStack recorded=ItemStack.parseOptional(l.registryAccess(),pair.getCompound("expected"));if(ChronoFactory.matches(recorded,wanted))return ItemStack.parseOptional(l.registryAccess(),pair.getCompound("actual"));}
        return wanted;
    }
    static ItemActions.Outcome perform(ServerLevel l,StrictReplay.MacroPlayer f,CompoundTag job,CompoundTag a,BlockPos pos){
        int option=a.getInt("option"),fee=option+1,required=Math.max(fee,a.getInt("required"));
        if(option<0||option>=3||!l.getBlockState(pos).is(Blocks.ENCHANTING_TABLE))return new ItemActions.Outcome(false,false,"Waiting for enchanting table");
        if(f.experienceLevel<required)return new ItemActions.Outcome(false,false,"Enchant option "+fee+": need "+required+" levels, have "+f.experienceLevel);
        ItemStack wanted=ChronoFactory.template(a,l),lapis=new ItemStack(Items.LAPIS_LAZULI);
        if(ChronoFactory.carried(f,wanted)<1)return new ItemActions.Outcome(false,false,"Missing enchantment item: "+ChronoFactory.itemName(wanted));
        if(ChronoFactory.carried(f,lapis)<fee)return new ItemActions.Outcome(false,false,"Need "+fee+" lapis for option "+fee);
        var provider=l.getBlockState(pos).getMenuProvider(l,pos);
        if(provider==null||f.openMenu(provider,b->{}).isEmpty()||!(f.containerMenu instanceof EnchantmentMenu menu)){f.closeContainer();return new ItemActions.Outcome(false,false,"Could not open enchanting table");}
        try{
            ItemStack input=take(f,wanted,1);if(input.isEmpty())return new ItemActions.Outcome(false,false,"Missing enchantment item");
            menu.getSlot(0).set(input);
            int left=fee;
            for(int i=0;i<36&&left>0;i++){ItemStack st=f.getInventory().getItem(i);if(!ChronoFactory.matches(st,lapis))continue;int n=Math.min(left,st.getCount());st.shrink(n);left-=n;}
            menu.getSlot(1).set(lapis.copyWithCount(fee-left));
            int current=menu.costs[option];
            if(current<=0)return new ItemActions.Outcome(false,false,"Recorded enchant option "+fee+" unavailable");
            if(f.experienceLevel<current)return new ItemActions.Outcome(false,false,"Enchant option "+fee+": current table requires "+current+" levels");
            int before=f.experienceLevel;boolean accepted=menu.clickMenuButton(f,option);
            boolean done=accepted&&f.experienceLevel<before;
            if(done&&a.contains("observedResult")&&!menu.getSlot(0).getItem().isEmpty()){
                ListTag pairs=job.getList("enchantedOutputs",Tag.TAG_COMPOUND);CompoundTag pair=new CompoundTag();pair.put("expected",a.getCompound("observedResult").copy());pair.put("actual",menu.getSlot(0).getItem().copyWithCount(1).save(l.registryAccess()));pairs.add(pair);job.put("enchantedOutputs",pairs);
            }
            return new ItemActions.Outcome(done,done,done?"Enchanted using option "+fee:"Enchant option "+fee+" rejected");
        }finally{f.closeContainer();}
    }
}
