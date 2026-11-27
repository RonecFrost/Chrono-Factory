package com.ronec.chronofactory;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Records successful output-slot transactions, not input-grid rearrangements.
 * Playback reconstructs a valid recipe from real carried ingredients. */
final class CraftingReplay {
    record Capture(AbstractContainerMenu menu,BlockPos pos,String kind,ResourceLocation recipe,int width,int height,List<ItemStack> grid,ItemStack result,Map<String,Integer> before,Map<String,Integer> unit){}
    record Outcome(boolean done,boolean working,String reason){}
    private static String key(ItemStack st){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem())+":"+st.getComponentsPatch();}
    private static void add(Map<String,Integer> map,ItemStack st,int sign){if(st.isEmpty())return;map.merge(key(st),sign*st.getCount(),Integer::sum);}
    private static Map<String,Integer> clean(Map<String,Integer> map){map.values().removeIf(v->v==0);return map;}
    private static Map<String,Integer> owned(ServerPlayer p,AbstractContainerMenu menu,int start,int end){
        Map<String,Integer> map=new HashMap<>();
        for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack st=p.getInventory().getItem(i);if(!ChronoFactory.watch(st))add(map,st,1);}
        add(map,menu.getCarried(),1);
        for(int i=start;i<end;i++)add(map,menu.getSlot(i).getItem(),1);
        return clean(map);
    }
    private static List<ItemStack> grid(AbstractContainerMenu menu,int start,int end){
        List<ItemStack> stacks=new ArrayList<>();for(int i=start;i<end;i++){ItemStack st=menu.getSlot(i).getItem();stacks.add(st.isEmpty()?ItemStack.EMPTY:st.copyWithCount(1));}return stacks;
    }
    private static Map<String,Integer> unit(List<ItemStack> grid,ItemStack result,List<ItemStack> remainders){
        Map<String,Integer> m=new HashMap<>();for(ItemStack st:grid)add(m,st,-1);add(m,result,1);for(ItemStack st:remainders)add(m,st,1);return clean(m);
    }
    static Capture before(ServerPlayer p,ChronoFactory.Recording r,int slot,int click){
        AbstractContainerMenu menu=p.containerMenu;ServerLevel l=p.serverLevel();
        // Ignore creative cloning, throwing outputs and other actions that do
        // not put the result into the demonstrator's inventory or cursor.
        if(click!=ClickType.PICKUP.ordinal()&&click!=ClickType.QUICK_MOVE.ordinal()&&click!=ClickType.SWAP.ordinal())return null;
        if((menu instanceof CraftingMenu||menu instanceof InventoryMenu)&&slot==0){
            int side=menu instanceof CraftingMenu?3:2;BlockPos pos=side==3?r.craftStation:p.blockPosition();
            if(pos==null)return null;
            List<ItemStack> g=grid(menu,1,1+side*side);
            CraftingInput input=CraftingInput.of(side,side,g);
            var found=l.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,l);
            if(found.isEmpty())return null;
            var holder=found.get();ItemStack result=holder.value().assemble(input,l.registryAccess());
            if(result.isEmpty()||!same(result,menu.getSlot(0).getItem()))return null;
            return new Capture(menu,pos,side==3?"table":"inventory",holder.id(),side,side,g,result,owned(p,menu,1,1+side*side),unit(g,result,holder.value().getRemainingItems(input)));
        }
        if(menu instanceof StonecutterMenu stone&&slot==1&&r.craftStation!=null){
            int selected=stone.getSelectedRecipeIndex();if(selected<0||selected>=stone.getRecipes().size())return null;
            var holder=stone.getRecipes().get(selected);List<ItemStack> g=grid(menu,0,1);SingleRecipeInput input=new SingleRecipeInput(g.get(0));
            ItemStack result=holder.value().assemble(input,l.registryAccess());
            if(result.isEmpty()||!same(result,menu.getSlot(1).getItem()))return null;
            return new Capture(menu,r.craftStation,"stonecutter",holder.id(),1,1,g,result,owned(p,menu,0,1),unit(g,result,holder.value().getRemainingItems(input)));
        }
        return null;
    }
    static void after(ServerPlayer p,ChronoFactory.Recording r){
        Capture c=r.craftCapture;r.craftCapture=null;if(c==null||p.containerMenu!=c.menu())return;
        int start=c.kind().equals("stonecutter")?0:1,end=start+c.width()*c.height();
        Map<String,Integer> actual=owned(p,c.menu(),start,end);c.before().forEach((k,v)->actual.merge(k,-v,Integer::sum));clean(actual);
        if(actual.isEmpty()||c.unit().isEmpty())return;
        int crafts=0;
        for(var e:c.unit().entrySet()){
            int changed=actual.getOrDefault(e.getKey(),0),each=e.getValue();
            if(changed%each!=0)return;int n=changed/each;if(n<=0||n>2304)return;
            if(crafts!=0&&crafts!=n)return;crafts=n;
        }
        if(crafts==0||!actual.keySet().equals(c.unit().keySet()))return;
        CompoundTag action=ChronoFactory.point(p,"craft");action.putLong("pos",c.pos().asLong());action.putString("station",c.kind());action.putString("recipe",c.recipe().toString());action.putInt("width",c.width());action.putInt("height",c.height());action.putInt("crafts",crafts);action.put("result",c.result().save(p.registryAccess()));
        ListTag grid=new ListTag();for(int i=0;i<c.grid().size();i++){ItemStack st=c.grid().get(i);if(st.isEmpty())continue;CompoundTag t=new CompoundTag();t.putInt("slot",i);t.put("item",st.save(p.registryAccess()));grid.add(t);}action.put("grid",grid);r.add(action);
    }
    private static boolean same(ItemStack a,ItemStack b){return a.getCount()==b.getCount()&&ItemStack.isSameItemSameComponents(a,b);}
    private static boolean insert(List<ItemStack> inventory,ItemStack st){
        ItemStack left=st.copy();if(left.isEmpty())return true;
        for(ItemStack into:inventory){if(left.isEmpty())return true;if(!into.isEmpty()&&ItemStack.isSameItemSameComponents(into,left)){int n=Math.min(left.getCount(),into.getMaxStackSize()-into.getCount());if(n>0){into.grow(n);left.shrink(n);}}}
        for(int i=0;i<inventory.size()&&!left.isEmpty();i++)if(inventory.get(i).isEmpty()){int n=Math.min(left.getCount(),left.getMaxStackSize());inventory.set(i,left.copyWithCount(n));left.shrink(n);}
        return left.isEmpty();
    }
    static Outcome perform(ServerLevel l,FakePlayer f,CompoundTag job,CompoundTag a,BlockPos pos){
        String station=a.getString("station");boolean stone=station.equals("stonecutter");
        if(stone&&!l.getBlockState(pos).is(Blocks.STONECUTTER))return new Outcome(false,false,"Waiting for stonecutter");
        if(station.equals("table")&&!l.getBlockState(pos).is(Blocks.CRAFTING_TABLE))return new Outcome(false,false,"Waiting for crafting table");
        int w=a.getInt("width"),h=a.getInt("height");if(w<1||w>3||h<1||h>3)return new Outcome(false,false,"Invalid crafting grid");
        ResourceLocation id=ResourceLocation.tryParse(a.getString("recipe"));
        if(id==null)return new Outcome(false,false,"Invalid recipe");
        var found=l.getRecipeManager().byKey(id);if(found.isEmpty())return new Outcome(false,false,"Recipe missing: "+id);
        Inventory inv=f.getInventory();List<ItemStack> staged=new ArrayList<>();for(int i=0;i<36;i++)staged.add(inv.getItem(i).copy());
        List<ItemStack> g=new ArrayList<>(Collections.nCopies(w*h,ItemStack.EMPTY));
        for(Tag x:a.getList("grid",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)x;int slot=t.getInt("slot");if(slot<0||slot>=g.size()||!g.get(slot).isEmpty())return new Outcome(false,false,"Invalid recorded ingredient");
            ItemStack wanted=ItemStack.parseOptional(l.registryAccess(),t.getCompound("item"));if(wanted.isEmpty()||ChronoFactory.watch(wanted))return new Outcome(false,false,"Invalid ingredient");
            boolean got=false;
            for(ItemStack st:staged){if(!st.isEmpty()&&ItemStack.isSameItemSameComponents(st,wanted)){g.set(slot,st.copyWithCount(1));st.shrink(1);got=true;break;}}
            if(!got)return new Outcome(false,false,"Missing crafting ingredient: "+ChronoFactory.itemName(wanted));
        }
        ItemStack result;List<ItemStack> remainders;
        if(stone&&found.get().value() instanceof StonecutterRecipe recipe&&g.size()==1){
            SingleRecipeInput input=new SingleRecipeInput(g.get(0));if(!recipe.matches(input,l))return new Outcome(false,false,"Stonecutting ingredients no longer match");
            result=recipe.assemble(input,l.registryAccess());remainders=recipe.getRemainingItems(input);
        }else if(!stone&&found.get().value() instanceof CraftingRecipe recipe){
            CraftingInput input=CraftingInput.of(w,h,g);if(!recipe.matches(input,l))return new Outcome(false,false,"Crafting ingredients no longer match");
            result=recipe.assemble(input,l.registryAccess());remainders=recipe.getRemainingItems(input);
        }else return new Outcome(false,false,"Unsupported recipe type");
        ItemStack expected=ItemStack.parseOptional(l.registryAccess(),a.getCompound("result"));
        if(result.isEmpty()||ChronoFactory.watch(result)||!same(result,expected))return new Outcome(false,false,"Recipe output changed: "+id);
        // Stage all changes first. Never consume a partial recipe or materialize
        // output if there is nowhere to put the result and its remainders.
        for(ItemStack st:remainders)if(!insert(staged,st))return new Outcome(false,false,"No space for crafting remainder");
        if(!insert(staged,result))return new Outcome(false,false,"No space for crafting output");
        for(int i=0;i<36;i++)inv.setItem(i,staged.get(i));
        int completed=job.getInt("crafted")+1;job.putInt("crafted",completed);
        int total=Math.max(1,a.getInt("crafts"));return new Outcome(completed>=total,true,"Crafted "+completed+"/"+total+" — "+ChronoFactory.itemName(result));
    }
}
