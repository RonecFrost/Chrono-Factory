package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;

final class ItemActions{
    record Outcome(boolean done,boolean working,String reason){}
    static void close(ChronoFactory.Recording r){
        if(r.itemHold!=null){r.itemHold.putInt("duration",Math.max(1,r.tick-r.itemHold.getInt("recordTick")));r.itemHold=null;}
    }
    static void record(ServerPlayer p,ChronoFactory.Recording r,ItemStack stack){
        if(stack.isEmpty())return;
        // A block-use event may precede the item fallback in the same tick.
        // Retain only the actual item-use path when the menu did not open.
        MenuTransactions.cancelUnopened(r);
        if(r.pendingUse!=null&&r.pendingUse.getInt("recordTick")==r.tick){r.pendingUse=null;r.container=null;}
        if(!r.actions.isEmpty()){
            CompoundTag last=r.actions.getCompound(r.actions.size()-1);
            if("use".equals(last.getString("type"))&&last.getInt("recordTick")==r.tick){r.actions.remove(r.actions.size()-1);r.heldUse=null;}
        }
        close(r);r.move(p);CompoundTag a=ChronoFactory.point(p,"item_use");
        a.put("item",stack.copyWithCount(1).save(p.registryAccess()));a.putInt("duration",1);a.putBoolean("held",stack.getUseDuration(p)>0);a.putInt("spellSlot",ArsSupport.slot(stack));r.add(a);
        if(a.getBoolean("held"))r.itemHold=a;
        if(r.gui.size()<4096){CompoundTag raw=a.copy();raw.putString("kind","item_use");raw.putInt("tick",r.raw.size()/3);raw.putInt("selected",p.getInventory().selected);r.gui.add(raw);}
    }
    static void loadXp(FakePlayer f,CompoundTag job){f.experienceLevel=job.getInt("xpLevel");f.experienceProgress=job.getFloat("xpProgress");f.totalExperience=job.getInt("xpTotal");}
    static void saveXp(FakePlayer f,CompoundTag job){job.putInt("xpLevel",f.experienceLevel);job.putFloat("xpProgress",f.experienceProgress);job.putInt("xpTotal",f.totalExperience);}
    static void collectXp(FakePlayer f,ServerLevel l){if(f.takeXpDelay>0){f.takeXpDelay--;return;}for(ExperienceOrb orb:l.getEntitiesOfClass(ExperienceOrb.class,f.getBoundingBox().inflate(1.5,.75,1.5))){orb.playerTouch(f);break;}}
    static boolean attack(FakePlayer f){
        Vec3 eye=f.getEyePosition(),end=eye.add(f.getLookAngle().scale(f.entityInteractionRange()));double range=eye.distanceToSqr(end);
        var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(f,eye,end,f.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1),e->e.isPickable()&&!(e instanceof EchoEntity)&&e!=f,range);
        if(hit==null)return false;
        var block=ChronoFactory.aimedBlock(f);if(block.getType()!=HitResult.Type.MISS&&eye.distanceToSqr(block.getLocation())<eye.distanceToSqr(hit.getLocation()))return false;
        f.attack(hit.getEntity());return true;
    }
    static Outcome perform(ServerLevel l,FakePlayer f,CompoundTag job,CompoundTag action){
        ItemStack wanted=ChronoFactory.template(action,l);
        if(!ChronoFactory.equip(f,wanted))return new Outcome(false,false,"Missing required item: "+ChronoFactory.itemName(wanted));
        f.setYRot(action.getFloat("yaw"));f.setXRot(action.getFloat("pitch"));f.setShiftKeyDown(action.getBoolean("sneak"));
        if("ars_edit".equals(action.getString("type"))){boolean ok=ArsSupport.edit(f.getMainHandItem(),action,l);return new Outcome(ok,ok,ok?"Spell configured":"Cannot configure Ars spell");}
        if("attack_entity".equals(action.getString("type"))){attack(f);return new Outcome(true,true,"Attacked in recorded direction");}
        ItemStack current=f.getMainHandItem();if(ArsSupport.book(current))ArsSupport.select(current,action.getInt("spellSlot"));
        int elapsed=job.getInt("heldElapsed"),duration=Math.max(1,action.getInt("duration"));boolean held=action.getBoolean("held");
        if(!held){
            InteractionResult result=f.gameMode.useItem(f,l,current,InteractionHand.MAIN_HAND);
            ArsSupport.saveMana(f,job);return new Outcome(result.consumesAction(),result.consumesAction(),result.consumesAction()?"Used item":"Item use unavailable");
        }
        // ItemStack is reconstructed from persisted inventory each tick, so do
        // not depend on a transient LivingEntity reference to an old stack.
        int full=current.getUseDuration(f);
        if(full<=0)return new Outcome(false,false,"Item no longer supports held use");
        if(!job.getBoolean("itemStarted")){
            InteractionResult result=f.gameMode.useItem(f,l,current,InteractionHand.MAIN_HAND);
            if(!result.consumesAction())return new Outcome(false,false,"Cannot begin held item");
            job.putBoolean("itemStarted",true);
        }
        elapsed++;job.putInt("heldElapsed",elapsed);
        int remain=Math.max(0,full-elapsed);current.onUseTick(l,f,remain);
        if(elapsed>=duration||remain==0){
            if(remain==0&&!current.useOnRelease())f.setItemInHand(InteractionHand.MAIN_HAND,current.finishUsingItem(l,f));
            else current.releaseUsing(l,f,remain);
            f.stopUsingItem();ArsSupport.saveMana(f,job);return new Outcome(true,true,"Released held item");
        }
        f.stopUsingItem();return new Outcome(false,true,"Holding item "+elapsed+"/"+duration+" ticks");
    }
}
