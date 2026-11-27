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
        f.closeContainer();ItemStack cursor=f.inventoryMenu.getCarried();f.inventoryMenu.setCarried(ItemStack.EMPTY);
        if(!cursor.isEmpty()){f.getInventory().add(cursor);if(!cursor.isEmpty())f.drop(cursor,false);}a.remove("strictCursor");
        a.remove("strictVx");a.remove("strictVy");a.remove("strictVz");a.remove("strictUseDelay");a.remove("strictMinePos");a.remove("strictMineProgress");a.remove("strictAttackDelay");a.remove("strictFlags");
    }
    static void forget(MacroPlayer f,CompoundTag a){finishCycle(f,a);PLAYERS.remove(f.getUUID());}
    static void clickGui(MacroPlayer f,CompoundTag event){
        if(event.getBoolean("close")){f.closeContainer();return;}
        AbstractContainerMenu menu=f.containerMenu;
        if(event.getBoolean("inventory")){if(menu!=f.inventoryMenu)return;}
        else if(menu==f.inventoryMenu||!menu.stillValid(f))return;
        int slot=event.getInt("slot"),button=event.getInt("button"),click=event.getInt("click");
        if(click<0||click>=ClickType.values().length||slot< -999||slot>=menu.slots.size()||(slot<0&&slot!=-999))return;
        // Never replay the recorded item stack. The real slot and actual cursor
        // contents determine the result, including empty or different stacks.
        menu.clicked(slot,button,ClickType.values()[click],f);
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
        boolean ground=l.getBlockCollisions(f,f.getBoundingBox().move(0,-.06,0)).iterator().hasNext();f.setOnGround(ground);
        double vx=a.getDouble("strictVx"),vy=a.getDouble("strictVy"),vz=a.getDouble("strictVz");
        if(ground&&vy<0)vy=0;
        if(ground&&(flags&JUMP)!=0)vy=.42;
        double forward=((flags&FORWARD)!=0?1:0)-((flags&BACK)!=0?1:0);
        double strafe=((flags&LEFT)!=0?1:0)-((flags&RIGHT)!=0?1:0);
        Vec3 input=new Vec3(strafe,0,forward);if(input.lengthSqr()>1)input=input.normalize();
        double acceleration=ground?((flags&SNEAK)!=0?.031:(flags&SPRINT)!=0?.13:.10):.026;
        input=input.scale(acceleration).yRot(-yaw*Mth.DEG_TO_RAD);
        vx+=input.x;vz+=input.z;
        Vec3 desired=new Vec3(vx,vy,vz);if(l.hasChunkAt(BlockPos.containing(here.add(desired))))f.move(MoverType.SELF,desired);
        Vec3 actual=f.position().subtract(here);
        if(Math.abs(actual.x-vx)>1e-5)vx=0;if(Math.abs(actual.z-vz)>1e-5)vz=0;if(Math.abs(actual.y-vy)>1e-5)vy=0;
        double drag=f.onGround()?.546:.91;
        a.putDouble("strictVx",vx*drag);a.putDouble("strictVz",vz*drag);a.putDouble("strictVy",(vy-.08)*.98);
        boolean swung=false;
        // Normal vanilla-like use repeat interval; failures never stop the macro.
        int delay=Math.max(0,a.getInt("strictUseDelay")-1);
        if((flags&USE)!=0&&f.containerMenu==f.inventoryMenu&&delay==0){
            BlockHitResult hit=ChronoFactory.aimedBlock(f);
            InteractionResult result=InteractionResult.PASS;
            if(hit.getType()!=HitResult.Type.MISS&&l.hasChunkAt(hit.getBlockPos()))
                result=f.gameMode.useItemOn(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
            if(!result.consumesAction()&&!f.getMainHandItem().isEmpty()&&f.containerMenu==f.inventoryMenu)
                result=f.gameMode.useItem(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND);
            delay=4;swung=true;
        }
        if((flags&USE)==0){delay=0;if(f.isUsingItem())f.releaseUsingItem();}
        a.putInt("strictUseDelay",delay);
        int attackDelay=Math.max(0,a.getInt("strictAttackDelay")-1);
        if((flags&ATTACK)!=0&&f.containerMenu==f.inventoryMenu){
            BlockHitResult hit=ChronoFactory.aimedBlock(f);swung=true;
            if(hit.getType()!=HitResult.Type.MISS&&l.hasChunkAt(hit.getBlockPos())&&attackDelay==0){
                BlockPos pos=hit.getBlockPos();BlockState state=l.getBlockState(pos);
                if(!a.contains("strictMinePos")||a.getLong("strictMinePos")!=pos.asLong()){a.putLong("strictMinePos",pos.asLong());a.putFloat("strictMineProgress",0);}
                float progress=a.getFloat("strictMineProgress")+state.getDestroyProgress(f,l,pos);a.putFloat("strictMineProgress",progress);
                if(progress>=1){f.gameMode.destroyBlock(pos);a.remove("strictMinePos");a.remove("strictMineProgress");attackDelay=5;}
            }else if(hit.getType()==HitResult.Type.MISS){a.remove("strictMinePos");a.remove("strictMineProgress");}
        }else{a.remove("strictMinePos");a.remove("strictMineProgress");}
        a.putInt("strictAttackDelay",attackDelay);
        if((flags&DROP)!=0&&(previous&DROP)==0&&!f.getMainHandItem().isEmpty())f.drop(f.getMainHandItem().split(1),false);
        ListTag gui=tl.getList("gui",Tag.TAG_COMPOUND);int cursor=a.getInt("guiIndex");
        while(cursor<gui.size()&&gui.getCompound(cursor).getInt("tick")<=tick){clickGui(f,gui.getCompound(cursor++));}
        a.putInt("guiIndex",cursor);
        ChronoFactory.sweepItems(f,l,f.position());
        Vec3 now=f.position();CompoundTag position=ChronoFactory.point(f,"move");a.put("position",position);
        boolean moving=actual.horizontalDistanceSqr()>.00001;
        if(moving)a.putFloat("walkPhase",a.getFloat("walkPhase")+(float)actual.horizontalDistance()*5);
        echo.setPos(now.x,now.y,now.z);echo.setYRot(yaw);echo.setXRot(pitch);echo.setShiftKeyDown((flags&SNEAK)!=0);
        echo.visual(ChronoFactory.tint(tl),24,moving,a.getFloat("walkPhase"),f.getMainHandItem(),swung,pitch);
        a.putInt("rawIndex",tick+1);a.putString("reason",String.format(Locale.ROOT,"Strict input playback %.2f / %.2f s", (tick+1)/20.0,frames.length/60.0));
        saveCursor(f,a);a.put("inventory",f.getInventory().save(new ListTag()));f.getInventory().clearContent();
        if(!a.getBoolean("creative"))ChronoFactory.energy(watch,ChronoFactory.energy(watch)-8);
    }
}
