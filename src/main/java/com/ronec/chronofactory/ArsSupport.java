package com.ronec.chronofactory;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Optional bridge, with no class linkage to Ars when it is not installed. */
final class ArsSupport{
    private static final String ROOT="com.hollingsworth.arsnouveau.";
    static boolean book(ItemStack st){if(st.isEmpty())return false;var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem());return "ars_nouveau".equals(id.getNamespace())&&id.getPath().contains("spell_book");}
    static Object invoke(Object target,String name,Object... args)throws ReflectiveOperationException{
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        for(Method m:type.getMethods()){if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue;Class<?>[] p=m.getParameterTypes();boolean compatible=true;
            for(int i=0;i<p.length;i++){Class<?> t=p[i];if(t==int.class)t=Integer.class;else if(t==double.class)t=Double.class;else if(t==float.class)t=Float.class;else if(t==boolean.class)t=Boolean.class;if(args[i]!=null&&!t.isInstance(args[i])){compatible=false;break;}}
            if(compatible)return m.invoke(target instanceof Class<?>?null:target,args);
        }throw new NoSuchMethodException(name);
    }
    static Object caster(ItemStack s)throws ReflectiveOperationException{return invoke(Class.forName(ROOT+"api.registry.SpellCasterRegistry"),"from",s);}
    static int slot(ItemStack s){if(!book(s))return -1;try{return (Integer)invoke(caster(s),"getCurrentSlot");}catch(Exception e){return -1;}}
    static void select(ItemStack s,int slot){if(!book(s)||slot<0)return;try{Object c=caster(s);if(slot<(Integer)invoke(c,"getMaxSlots"))invoke(invoke(c,"setCurrentSlot",slot),"saveToStack",s);}catch(Exception ignored){}}
    static void observe(ServerPlayer p,ChronoFactory.Recording r){
        for(int i=0;i<36;i++){
            ItemStack st=p.getInventory().getItem(i);
            if(!book(st)){r.books.remove(i);continue;}
            ItemStack old=r.books.put(i,st.copy());if(old==null||old.getItem()!=st.getItem()||ItemStack.isSameItemSameComponents(old,st))continue;
            // A chest transfer or hotbar movement is not a spell creation.
            // Only edits to a continuously held book outside a normal container.
            if(i!=p.getInventory().selected||p.containerMenu!=p.inventoryMenu)continue;
            try{
                Object before=caster(old),after=caster(st);int count=(Integer)invoke(after,"getMaxSlots");
                for(int n=0;n<count;n++){
                    Object prior=invoke(before,"getSpell",n),now=invoke(after,"getSpell",n);String oldName=(String)invoke(before,"getSpellName",n),newName=(String)invoke(after,"getSpellName",n);
                    if(Objects.equals(prior,now)&&Objects.equals(oldName,newName))continue;
                    CompoundTag a=ChronoFactory.point(p,"ars_edit");a.put("item",old.copyWithCount(1).save(p.registryAccess()));a.put("edited",st.copyWithCount(1).save(p.registryAccess()));a.putInt("spellSlot",n);r.add(a);
                    if(r.gui.size()<4096){CompoundTag event=a.copy();event.putString("kind","ars_edit");event.putInt("tick",r.raw.size()/3);event.putInt("selected",i);r.gui.add(event);}
                }
            }catch(Exception ignored){}
        }
    }
    static boolean edit(ItemStack actual,CompoundTag action,ServerLevel l){
        if(!book(actual))return false;
        try{
            ItemStack recorded=ItemStack.parseOptional(l.registryAccess(),action.getCompound("edited"));if(recorded.getItem()!=actual.getItem())return false;
            Object target=caster(actual),source=caster(recorded);int slot=action.getInt("spellSlot");
            if(slot<0||slot>=(Integer)invoke(target,"getMaxSlots"))return false;
            target=invoke(target,"setSpell",invoke(source,"getSpell",slot),slot);
            target=invoke(target,"setSpellName",invoke(source,"getSpellName",slot),slot);
            target=invoke(target,"setCurrentSlot",slot);invoke(target,"saveToStack",actual);return true;
        }catch(Exception e){return false;}
    }
    static Object mana(FakePlayer f)throws ReflectiveOperationException{return invoke(Class.forName(ROOT+"setup.registry.CapabilityRegistry"),"getMana",f);}
    static void manaTick(FakePlayer f,CompoundTag a){
        boolean has=false;for(int i=0;i<41;i++)if(book(f.getInventory().getItem(i))){has=true;break;}if(!has)return;
        try{
            Object cap=mana(f);if(cap==null)return;
            int tier=1;for(int i=0;i<41;i++){ItemStack st=f.getInventory().getItem(i);if(book(st)){Object t=invoke(st.getItem(),"getTier");tier=Math.max(tier,t.getClass().getField("value").getInt(t));}}
            invoke(cap,"setBookTier",tier);
            Class<?> util=Class.forName(ROOT+"api.util.ManaUtil");int max=(Integer)invoke(util,"getMaxMana",f);invoke(cap,"setMaxMana",max);
            double regen=((Number)invoke(util,"getManaRegen",f)).doubleValue()/20.0;
            double current=a.contains("arsMana")?a.getDouble("arsMana"):max;
            invoke(cap,"setMana",Math.min(max,current+regen));saveMana(f,a);
        }catch(Exception ignored){}
    }
    static ItemActions.Outcome cast(ServerLevel l,FakePlayer f,CompoundTag job,ItemStack stack,int slot){return cast(l,f,job,stack,slot,net.minecraft.world.InteractionHand.MAIN_HAND);}
    static ItemActions.Outcome cast(ServerLevel l,FakePlayer f,CompoundTag job,ItemStack stack,int slot,net.minecraft.world.InteractionHand hand){
        try{
            if(!book(stack))return new ItemActions.Outcome(false,false,"No matching Ars spellbook");
            select(stack,slot);Object c=caster(stack);int chosen=(Integer)invoke(c,"getCurrentSlot");Object spell=invoke(c,"getSpell",chosen);
            if(!(Boolean)invoke(spell,"isValid"))return new ItemActions.Outcome(false,false,"Spell slot "+(chosen+1)+" is empty or invalid");
            if(!job.contains("arsMana"))manaTick(f,job);Object cap=mana(f);if(cap==null)return new ItemActions.Outcome(false,false,"Ars mana capability unavailable");
            int cost=((Number)invoke(spell,"getCost")).intValue();int discount=((Number)invoke(Class.forName(ROOT+"api.util.ManaUtil"),"getPlayerDiscounts",f,spell,stack)).intValue();cost=Math.max(0,cost-discount);
            double before=((Number)invoke(cap,"getCurrentMana")).doubleValue();
            if(before<cost)return new ItemActions.Outcome(false,false,"Waiting for mana: "+(int)before+"/"+cost);
            Object result=invoke(c,"castSpell",l,f,hand,net.minecraft.network.chat.Component.literal("Invalid spell"));
            saveMana(f,job);boolean accepted=result instanceof net.minecraft.world.InteractionResultHolder<?> holder&&holder.getResult().consumesAction();
            return new ItemActions.Outcome(accepted,accepted,accepted?"Cast spell slot "+(chosen+1):"Ars cast rejected");
        }catch(Exception e){return new ItemActions.Outcome(false,false,"Ars casting API failed: "+e.getClass().getSimpleName());}
    }
    static void saveMana(FakePlayer f,CompoundTag a){try{Object cap=mana(f);if(cap!=null)a.putDouble("arsMana",((Number)invoke(cap,"getCurrentMana")).doubleValue());}catch(Exception ignored){}}
}
