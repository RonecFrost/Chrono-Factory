package com.ronec.chronofactory;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class EchoEntity extends PathfinderMob {
    static final EntityDataAccessor<String> OWNER=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.STRING);
    static final EntityDataAccessor<String> LOOP=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.STRING);
    static final EntityDataAccessor<Integer> TINT=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> PHASE=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.INT);
    static final EntityDataAccessor<Boolean> WALK=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Float> WALK_PHASE=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<ItemStack> HELD=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.ITEM_STACK);
    static final EntityDataAccessor<Boolean> SWING=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.BOOLEAN);
    static final EntityDataAccessor<Float> LOOK=SynchedEntityData.defineId(EchoEntity.class,EntityDataSerializers.FLOAT);
    public EchoEntity(EntityType<? extends PathfinderMob> type,Level level){super(type,level);setNoAi(true);setNoGravity(true);setInvulnerable(true);setPersistenceRequired();noPhysics=true;}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){
        super.defineSynchedData(b);b.define(OWNER,"");b.define(LOOP,"");b.define(TINT,0);b.define(PHASE,0);b.define(WALK,false);b.define(WALK_PHASE,0f);b.define(HELD,ItemStack.EMPTY);b.define(SWING,false);b.define(LOOK,0f);
    }
    void setIdentity(UUID owner,UUID loop){entityData.set(OWNER,owner.toString());entityData.set(LOOP,loop.toString());}
    private UUID readId(EntityDataAccessor<String> key){try{return UUID.fromString(entityData.get(key));}catch(IllegalArgumentException ex){return new UUID(0,0);}}
    UUID ownerId(){return readId(OWNER);}
    UUID loopId(){return readId(LOOP);}
    void visual(int tint,int phase,boolean walking,float walkPhase,ItemStack held,boolean swing,float pitch){
        entityData.set(TINT,tint);entityData.set(PHASE,phase);entityData.set(WALK,walking);entityData.set(WALK_PHASE,walkPhase);entityData.set(HELD,held.copyWithCount(held.isEmpty()?0:1));entityData.set(SWING,swing);entityData.set(LOOK,pitch);
    }
    int tint(){return entityData.get(TINT);}
    int phase(){return entityData.get(PHASE);}
    boolean walking(){return entityData.get(WALK);}
    float walkPhase(){return entityData.get(WALK_PHASE);}
    ItemStack visualHeld(){return entityData.get(HELD);}
    boolean swinging(){return entityData.get(SWING);}
    float lookPitch(){return entityData.get(LOOK);}
    @Override public boolean isPushable(){return false;}
    @Override public boolean isPickable(){return true;}
    @Override public void push(double x,double y,double z){}
    @Override public void tick(){super.tick();setDeltaMovement(Vec3.ZERO);}
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){
        if(hand==InteractionHand.MAIN_HAND&&player instanceof ServerPlayer p)ChronoFactory.describe(p,this);
        return InteractionResult.sidedSuccess(level().isClientSide());
    }
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putUUID("owner",ownerId());t.putUUID("loop",loopId());t.putInt("tint",tint());t.putInt("phase",phase());}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);if(t.hasUUID("owner")&&t.hasUUID("loop"))setIdentity(t.getUUID("owner"),t.getUUID("loop"));entityData.set(TINT,t.getInt("tint"));entityData.set(PHASE,t.getInt("phase"));setNoAi(true);setNoGravity(true);setInvulnerable(true);noPhysics=true;}
}
