package com.ronec.chronofactory;

import java.util.*;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;

/** A clock-driven macro, deliberately independent of the semantic action engine. */
public final class StrictReplay {
    static final int FORWARD=1,BACK=2,LEFT=4,RIGHT=8,JUMP=16,SNEAK=32,SPRINT=64,USE=128,ATTACK=256,SCREEN=512,DROP=1024;
    private static final Map<UUID,MacroPlayer> PLAYERS=new HashMap<>();
    static MacroPlayer player(ServerLevel l,UUID id){
        MacroPlayer p=PLAYERS.get(id);
        if(p==null||p.serverLevel()!=l){p=new MacroPlayer(l,new GameProfile(id,"ChronoEcho"));PLAYERS.put(id,p);}
        return p;
    }
    static final class MacroPlayer extends FakePlayer{
        int menuId=1;
        MacroPlayer(ServerLevel l,GameProfile profile){super(l,profile);gameMode.changeGameModeForPlayer(GameType.SURVIVAL);}
        @Override public OptionalInt openMenu(MenuProvider provider,Consumer<RegistryFriendlyByteBuf> extra){
            if(provider==null)return OptionalInt.empty();
            closeContainer();int id=menuId++%100+1;AbstractContainerMenu menu=provider.createMenu(id,getInventory(),this);
            if(menu==null)return OptionalInt.empty();containerMenu=menu;return OptionalInt.of(id);
        }
        @Override public void closeContainer(){
            if(containerMenu!=inventoryMenu){AbstractContainerMenu old=containerMenu;containerMenu=inventoryMenu;old.removed(this);}
        }
    }
    static void restoreCursor(MacroPlayer f,CompoundTag a){
        ItemStack cursor=a.contains("strictCursor")?ItemStack.parseOptional(f.registryAccess(),a.getCompound("strictCursor")):ItemStack.EMPTY;
        f.containerMenu.setCarried(cursor);
    }
    static void saveCursor(MacroPlayer f,CompoundTag a){
        ItemStack cursor=f.containerMenu.getCarried();if(cursor.isEmpty())a.remove("strictCursor");else a.put("strictCursor",cursor.save(f.registryAccess()));
    }
    static void finishCycle(MacroPlayer f,CompoundTag a){
        f.closeContainer();f.inventoryMenu.removed(f);ItemStack cursor=f.inventoryMenu.getCarried();f.inventoryMenu.setCarried(ItemStack.EMPTY);
        if(!cursor.isEmpty()){f.getInventory().add(cursor);if(!cursor.isEmpty())f.drop(cursor,false);}a.remove("strictCursor");
        f.stopUsingItem();a.remove("itemStarted");a.remove("strictVx");a.remove("strictVy");a.remove("strictVz");a.remove("strictUseDelay");a.remove("strictMinePos");a.remove("strictMineProgress");a.remove("strictAttackDelay");a.remove("strictFlags");
    }
    static void forget(MacroPlayer f,CompoundTag a){finishCycle(f,a);PLAYERS.remove(f.getUUID());}
    static void clickGui(MacroPlayer f,CompoundTag event){
        if(event.getBoolean("close")||"close".equals(event.getString("kind"))){if(f.containerMenu==f.inventoryMenu)f.inventoryMenu.removed(f);else f.closeContainer();return;}
        AbstractContainerMenu menu=f.containerMenu;
        if(event.getBoolean("inventory")){if(menu!=f.inventoryMenu)return;}
        else if(menu==f.inventoryMenu||!menu.stillValid(f))return;
        if(event.contains("slots")&&event.getInt("slots")!=menu.slots.size())return;
        int slot=event.getInt("slot"),button=event.getInt("button"),click=event.getInt("click");
        if(click<0||click>=ClickType.values().length||slot< -999||slot>=menu.slots.size()||(slot<0&&slot!=-999))return;
        // Never replay the recorded item stack. The real slot and actual cursor
        // contents determine the result, including empty or different stacks.
        menu.clicked(slot,button,ClickType.values()[click],f);
    }
    static boolean recordedUse(ServerLevel l,MacroPlayer f,CompoundTag event){
        if(f.containerMenu!=f.inventoryMenu)return false;
        f.getInventory().selected=Math.max(0,Math.min(8,event.getInt("selected")));
        f.setYRot(event.getFloat("yaw"));f.setXRot(event.getFloat("pitch"));f.setShiftKeyDown(event.getBoolean("sneak"));
        BlockHitResult hit=ChronoFactory.aimedBlock(f);
        // A tiny difference in frame timing can miss a chest edge. Only recover
        // the recorded hit when the Echo is still at the demonstrated position
        // and the real line of sight reaches that block; never reach through walls.
        BlockPos pos=BlockPos.of(event.getLong("pos"));
        if(hit.getType()==HitResult.Type.MISS&&f.position().distanceToSqr(ChronoFactory.vec(event))<.36&&l.hasChunkAt(pos)){
            Vec3 target=new Vec3(event.getDouble("hx"),event.getDouble("hy"),event.getDouble("hz"));
            Vec3 eye=f.getEyePosition();Vec3 ray=target.subtract(eye);
            if(ray.lengthSqr()<=25){
                BlockHitResult check=l.clip(new net.minecraft.world.level.ClipContext(eye,target.add(ray.normalize().scale(.035)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,f));
                if(check.getType()!=HitResult.Type.MISS&&check.getBlockPos().equals(pos))hit=check;
            }
        }
        if(hit.getType()!=HitResult.Type.MISS&&l.hasChunkAt(hit.getBlockPos())){
            InteractionResult result=f.gameMode.useItemOn(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND,hit);return result.consumesAction();
        }
        if(!f.getMainHandItem().isEmpty())return f.gameMode.useItem(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND).consumesAction();
        return false;
    }
    static void step(ServerLevel l,CompoundTag a,CompoundTag tl,MacroPlayer f,EchoEntity echo,ItemStack watch){
        int[] frames=tl.getIntArray("raw");int tick=a.getInt("rawIndex"),i=tick*3;
        if(i+2>=frames.length){a.putInt("rawIndex",frames.length/3);f.getInventory().clearContent();return;}
        int flags=frames[i]&2047,slot=(frames[i]>>>12)&15;
        int previous=a.getInt("strictFlags");a.putInt("strictFlags",flags);
        float yaw=Float.intBitsToFloat(frames[i+1]),pitch=Float.intBitsToFloat(frames[i+2]);
        f.getInventory().selected=Math.min(8,slot);f.setYRot(yaw);f.setXRot(pitch);f.setShiftKeyDown((flags&SNEAK)!=0);f.setSprinting((flags&SPRINT)!=0);
        if(f.containerMenu!=f.inventoryMenu&&!f.containerMenu.stillValid(f))f.closeContainer();
        Vec3 here=echo.position();f.setPos(here.x,here.y,here.z);
        // The same sampled path used for the adaptive engine, but replayed as
        // per-tick relative node deltas. No acceleration, no catch-up, no target
        // coordinate snapping. A blocked tick consumes its time normally.
        int[] motion=tl.getIntArray("motion");
        Vec3 desired=new Vec3(Float.intBitsToFloat(motion[i]),Float.intBitsToFloat(motion[i+1]),Float.intBitsToFloat(motion[i+2]));
        if(!Double.isFinite(desired.lengthSqr())||desired.lengthSqr()>4)desired=Vec3.ZERO;
        f.noPhysics=false;f.setDeltaMovement(Vec3.ZERO);
        f.setOnGround(l.getBlockCollisions(f,f.getBoundingBox().move(0,-.06,0)).iterator().hasNext());
        // Resolve collisions with the vanilla collision solver. The next sample
        // is always relative to the actual resulting position.
        if(l.hasChunkAt(BlockPos.containing(here.add(desired))))f.move(MoverType.SELF,desired);
        Vec3 actual=f.position().subtract(here);
        boolean swung=false,hadRecordedUse=false;String task="Idle";
        // Record actual server interaction events and GUI clicks in one ordered
        // stream. This avoids relying on a held-key frame to open a chest.
        ListTag gui=tl.getList("gui",Tag.TAG_COMPOUND);int cursor=a.getInt("guiIndex");
        int delay=Math.max(0,a.getInt("strictUseDelay")-1);
        while(cursor<gui.size()&&gui.getCompound(cursor).getInt("tick")<=tick){
            CompoundTag event=gui.getCompound(cursor++);
            if("ars_edit".equals(event.getString("kind"))){f.getInventory().selected=Math.max(0,Math.min(8,event.getInt("selected")));ArsSupport.edit(f.getMainHandItem(),event,l);task="Configure spell";}
            else if("item_use".equals(event.getString("kind"))){f.getInventory().selected=Math.max(0,Math.min(8,event.getInt("selected")));if(ArsSupport.book(f.getMainHandItem()))ArsSupport.select(f.getMainHandItem(),event.getInt("spellSlot"));f.gameMode.useItem(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND);hadRecordedUse=true;delay=4;swung=true;task="Use item";}
            else if("button".equals(event.getString("kind"))){if(f.containerMenu!=f.inventoryMenu&&f.containerMenu.stillValid(f))f.containerMenu.clickMenuButton(f,event.getInt("button"));task="Select recipe";}
            else if("use".equals(event.getString("kind"))){recordedUse(l,f,event);hadRecordedUse=true;swung=true;delay=4;task="Right-click";}
            else{clickGui(f,event);task=event.getBoolean("close")?"Close inventory":"Click slot "+event.getInt("slot");}
        }
        a.putInt("guiIndex",cursor);
        // Continue held input even when recorded events were absent, but do
        // not double-fire an actual captured click.
        if(!hadRecordedUse&&(flags&USE)!=0&&(flags&SCREEN)==0&&f.containerMenu==f.inventoryMenu&&delay==0){
            f.setYRot(yaw);f.setXRot(pitch);f.setShiftKeyDown((flags&SNEAK)!=0);
            BlockHitResult hit=ChronoFactory.aimedBlock(f);InteractionResult result=InteractionResult.PASS;
            if(hit.getType()!=HitResult.Type.MISS&&l.hasChunkAt(hit.getBlockPos()))result=f.gameMode.useItemOn(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            if(!result.consumesAction()&&!f.getMainHandItem().isEmpty()&&f.containerMenu==f.inventoryMenu)f.gameMode.useItem(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND);
            delay=4;swung=true;task="Holding right-click";
        }
        if((flags&USE)==0&&!hadRecordedUse){delay=0;if(f.isUsingItem())f.releaseUsingItem();}
        a.putInt("strictUseDelay",delay);
        f.setYRot(yaw);f.setXRot(pitch);f.setShiftKeyDown((flags&SNEAK)!=0);
        int attackDelay=Math.max(0,a.getInt("strictAttackDelay")-1);
        if((flags&ATTACK)!=0&&(flags&SCREEN)==0&&f.containerMenu==f.inventoryMenu){
            task="Holding left-click";
            BlockHitResult hit=ChronoFactory.aimedBlock(f);swung=true;
            if(hit.getType()!=HitResult.Type.MISS&&l.hasChunkAt(hit.getBlockPos())&&attackDelay==0){
                BlockPos pos=hit.getBlockPos();BlockState state=l.getBlockState(pos);
                if(!a.contains("strictMinePos")||a.getLong("strictMinePos")!=pos.asLong()){a.putLong("strictMinePos",pos.asLong());a.putFloat("strictMineProgress",0);}
                float progress=a.getFloat("strictMineProgress")+state.getDestroyProgress(f,l,pos);a.putFloat("strictMineProgress",progress);
                if(progress>=1){f.gameMode.destroyBlock(pos);a.remove("strictMinePos");a.remove("strictMineProgress");attackDelay=5;}
            }else if(hit.getType()==HitResult.Type.MISS){a.remove("strictMinePos");a.remove("strictMineProgress");if(attackDelay==0&&ItemActions.attack(f))attackDelay=10;}
        }else{a.remove("strictMinePos");a.remove("strictMineProgress");}
        a.putInt("strictAttackDelay",attackDelay);
        if((flags&DROP)!=0&&(previous&DROP)==0&&!f.getMainHandItem().isEmpty())f.drop(f.getMainHandItem().split(1),false);
        
        ChronoFactory.sweepItems(f,l,f.position());ItemActions.collectXp(f,l);ItemActions.saveXp(f,a);ArsSupport.saveMana(f,a);
        Vec3 now=f.position();CompoundTag position=ChronoFactory.point(f,"move");a.put("position",position);
        boolean moving=actual.horizontalDistanceSqr()>.00001;
        if("Idle".equals(task)&&desired.lengthSqr()>.00001)task=moving?"Following recorded movement":"Movement obstructed; continuing clock";
        if("Idle".equals(task)&&f.containerMenu!=f.inventoryMenu)task="Inventory open";
        a.putString("strictTask",task);
        if(moving)a.putFloat("walkPhase",a.getFloat("walkPhase")+(float)actual.horizontalDistance()*5);
        echo.setPos(now.x,now.y,now.z);echo.setYRot(yaw);echo.setXRot(pitch);echo.setShiftKeyDown((flags&SNEAK)!=0);
        echo.visual(ChronoFactory.tint(tl),24,moving,a.getFloat("walkPhase"),f.getMainHandItem(),swung,pitch);
        a.putInt("rawIndex",tick+1);a.putString("reason",String.format(Locale.ROOT,"Strict input playback %.2f / %.2f s", (tick+1)/20.0,frames.length/60.0));
        saveCursor(f,a);a.put("inventory",f.getInventory().save(new ListTag()));f.getInventory().clearContent();
        if(!a.getBoolean("creative"))ChronoFactory.energy(watch,ChronoFactory.energy(watch)-8);
    }
}
