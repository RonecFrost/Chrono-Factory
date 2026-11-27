package com.ronec.chronofactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
final class WorkstationActions{
    record Capture(AbstractContainerMenu menu,BlockPos pos,ItemStack left,ItemStack right,ItemStack output,int levels,String kind){}
    static Capture before(ServerPlayer p,ChronoFactory.Recording r,int slot,int click){
        var menu=p.containerMenu;if(slot!=2||r.transaction==null||(!(menu instanceof AnvilMenu)&&!(menu instanceof GrindstoneMenu)))return null;
        if(click!=ClickType.PICKUP.ordinal()&&click!=ClickType.QUICK_MOVE.ordinal()&&click!=ClickType.SWAP.ordinal())return null;
        ItemStack out=menu.getSlot(2).getItem();if(out.isEmpty()||ChronoFactory.watch(out))return null;
        r.transaction.putBoolean("enchantMenu",true);
        return new Capture(menu,BlockPos.of(r.transaction.getLong("pos")),menu.getSlot(0).getItem().copy(),menu.getSlot(1).getItem().copy(),out.copy(),p.experienceLevel,menu instanceof AnvilMenu?"anvil":"grindstone");
    }
    static void after(ServerPlayer p,ChronoFactory.Recording r){
        Capture c=r.workstationCapture;r.workstationCapture=null;if(c==null||c.menu()!=p.containerMenu)return;
        int usedLeft=c.left().getCount()-c.menu().getSlot(0).getItem().getCount(),usedRight=c.right().getCount()-c.menu().getSlot(1).getItem().getCount();
        if(usedLeft<=0&&usedRight<=0)return;
        CompoundTag a=ChronoFactory.point(p,"workstation_output");a.putLong("pos",c.pos().asLong());a.putString("station",c.kind());a.putInt("levels",Math.max(0,c.levels()-p.experienceLevel));
        if(usedLeft>0)a.put("left",c.left().copyWithCount(usedLeft).save(p.registryAccess()));
        if(usedRight>0)a.put("right",c.right().copyWithCount(usedRight).save(p.registryAccess()));
        a.put("result",c.output().save(p.registryAccess()));if(c.output().has(DataComponents.CUSTOM_NAME))a.putString("rename",c.output().getHoverName().getString());
        r.add(a);
    }
    // Match actual results from earlier operations first; otherwise use Adaptive
    // equipment matching. Anvil books retain exact enchantments.
    static ItemStack wanted(CompoundTag job,CompoundTag action,String key,ServerLevel l){return action.contains(key)?EnchantReplay.resolveOutput(job,ItemStack.parseOptional(l.registryAccess(),action.getCompound(key)),l):ItemStack.EMPTY;}
    static ItemStack take(StrictReplay.MacroPlayer f,ItemStack wanted){
        if(wanted.isEmpty())return ItemStack.EMPTY;
        for(int k=0;k<36;k++){ItemStack st=f.getInventory().getItem(k);if(ChronoFactory.matches(st,wanted)&&st.getCount()>=wanted.getCount())return st.split(wanted.getCount());}
        return ItemStack.EMPTY;
    }
    static boolean resultMatches(ItemStack actual,ItemStack expected,boolean grind){
        if(actual.isEmpty()||actual.getItem()!=expected.getItem()||actual.getCount()!=expected.getCount())return false;
        if(grind)return true; // Different input durability can produce a different legal repair.
        return Objects.equals(actual.get(DataComponents.CUSTOM_NAME),expected.get(DataComponents.CUSTOM_NAME))
            &&Objects.equals(actual.get(DataComponents.ENCHANTMENTS),expected.get(DataComponents.ENCHANTMENTS))
            &&Objects.equals(actual.get(DataComponents.STORED_ENCHANTMENTS),expected.get(DataComponents.STORED_ENCHANTMENTS))
            &&(!expected.isDamageableItem()||actual.getDamageValue()<=expected.getDamageValue());
    }
    static ItemActions.Outcome perform(ServerLevel l,StrictReplay.MacroPlayer f,CompoundTag job,CompoundTag a,BlockPos pos){
        boolean grind=a.getString("station").equals("grindstone");
        if(grind?!l.getBlockState(pos).is(Blocks.GRINDSTONE):!l.getBlockState(pos).is(BlockTags.ANVIL))return new ItemActions.Outcome(false,false,"Waiting for "+a.getString("station"));
        if(f.experienceLevel<a.getInt("levels"))return new ItemActions.Outcome(false,false,"Need "+a.getInt("levels")+" XP levels");
        ItemStack left=wanted(job,a,"left",l),right=wanted(job,a,"right",l),expected=ItemStack.parseOptional(l.registryAccess(),a.getCompound("result"));
        if(!left.isEmpty()&&ChronoFactory.carried(f,left)<left.getCount()||!right.isEmpty()&&ChronoFactory.carried(f,right)<right.getCount())return new ItemActions.Outcome(false,false,"Missing workstation inputs");
        var provider=l.getBlockState(pos).getMenuProvider(l,pos);
        if(provider==null||f.openMenu(provider,b->{}).isEmpty())return new ItemActions.Outcome(false,false,"Cannot open workstation");
        try{
            var menu=f.containerMenu;
            if(grind?!(menu instanceof GrindstoneMenu):!(menu instanceof AnvilMenu))return new ItemActions.Outcome(false,false,"Unexpected workstation menu");
            ItemStack in=take(f,left);if(!left.isEmpty()&&in.isEmpty())return new ItemActions.Outcome(false,false,"Missing first input");
            menu.getSlot(0).set(in);
            ItemStack extra=take(f,right);if(!right.isEmpty()&&extra.isEmpty())return new ItemActions.Outcome(false,false,"Missing second input");
            menu.getSlot(1).set(extra);
            if(menu instanceof AnvilMenu anvil){
                if(a.contains("rename"))anvil.setItemName(a.getString("rename"));
                if(f.experienceLevel<anvil.getCost())return new ItemActions.Outcome(false,false,"Anvil currently requires "+anvil.getCost()+" levels");
            }
            ItemStack actual=menu.getSlot(2).getItem().copy();
            if(!resultMatches(actual,expected,grind))return new ItemActions.Outcome(false,false,"Workstation result differs; no output taken");
            // Native result slot pays XP, consumes ingredients, and grants real output.
            menu.clicked(2,0,ClickType.QUICK_MOVE,f);
            if(!menu.getSlot(2).getItem().isEmpty()&&menu.getSlot(0).hasItem())return new ItemActions.Outcome(false,false,"No room for workstation output");
            ListTag pairs=job.getList("enchantedOutputs",Tag.TAG_COMPOUND);CompoundTag pair=new CompoundTag();pair.put("expected",a.getCompound("result").copy());pair.put("actual",actual.copyWithCount(1).save(l.registryAccess()));pairs.add(pair);job.put("enchantedOutputs",pairs);
            return new ItemActions.Outcome(true,true,"Completed "+a.getString("station")+" transaction");
        }finally{f.closeContainer();}
    }
}
