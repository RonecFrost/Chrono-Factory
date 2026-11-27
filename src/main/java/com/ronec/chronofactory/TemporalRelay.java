package com.ronec.chronofactory;

import java.util.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TemporalRelay {
    static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ChronoFactory.ID);
    static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,ChronoFactory.ID);
    static final DeferredBlock<RelayBlock> BLOCK=BLOCKS.register("temporal_relay",()->new RelayBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.5f).requiresCorrectToolForDrops().lightLevel(s->s.getValue(RelayBlock.POWERED)?8:0)));
    static final DeferredItem<BlockItem> ITEM=ChronoFactory.ITEMS.register("temporal_relay",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    static final DeferredHolder<BlockEntityType<?>,BlockEntityType<Dock>> BE=TYPES.register("temporal_relay",()->BlockEntityType.Builder.of(Dock::new,BLOCK.get()).build(null));
    static void register(IEventBus bus){BLOCKS.register(bus);TYPES.register(bus);}

    public static final class RelayBlock extends BaseEntityBlock{
        static final BooleanProperty POWERED=BooleanProperty.create("powered");
        static final MapCodec<RelayBlock> CODEC=simpleCodec(RelayBlock::new);
        RelayBlock(BlockBehaviour.Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(POWERED,false));}
        @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(POWERED);}
        @Override protected RenderShape getRenderShape(BlockState s){return RenderShape.MODEL;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new Dock(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState state,BlockEntityType<T> type){return l.isClientSide?null:createTickerHelper(type,BE.get(),Dock::tick);}
        @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level l,BlockPos pos,Player p,InteractionHand hand,BlockHitResult hit){
            if(hand!=InteractionHand.MAIN_HAND)return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if(l instanceof ServerLevel sl&&p instanceof ServerPlayer sp&&sl.getBlockEntity(pos) instanceof Dock dock){
                if(ChronoFactory.watch(stack)&&dock.stack.isEmpty())dock.insert(sp,stack);
                dock.send(sp,true);
            }
            return ItemInteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override protected InteractionResult useWithoutItem(BlockState state,Level l,BlockPos pos,Player p,BlockHitResult hit){
            if(l instanceof ServerLevel sl&&p instanceof ServerPlayer sp&&sl.getBlockEntity(pos) instanceof Dock dock)dock.send(sp,true);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override protected void neighborChanged(BlockState state,Level l,BlockPos pos,Block from,BlockPos neighbor,boolean moving){
            super.neighborChanged(state,l,pos,from,neighbor,moving);
            if(l instanceof ServerLevel sl&&sl.getBlockEntity(pos) instanceof Dock dock)dock.sampleSignal(sl);
        }
        @Override protected void onRemove(BlockState state,Level l,BlockPos pos,BlockState next,boolean moving){
            if(!state.is(next.getBlock())&&l instanceof ServerLevel sl&&sl.getBlockEntity(pos) instanceof Dock dock){
                dock.cancel(sl);if(!dock.stack.isEmpty()){Containers.dropItemStack(l,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,dock.stack);dock.stack=ItemStack.EMPTY;}
            }
            super.onRemove(state,l,pos,next,moving);
        }
    }
    public static final class Dock extends BlockEntity{
        ItemStack stack=ItemStack.EMPTY;
        UUID relayId=UUID.randomUUID(),owner=new UUID(0,0),selected;
        String ownerName="ChronoEcho";
        boolean powered=false,pending=false;
        Dock(BlockPos pos,BlockState state){super(BE.get(),pos,state);}
        @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){
            super.saveAdditional(t,r);if(!stack.isEmpty())t.put("watch",stack.save(r));t.putUUID("relayId",relayId);t.putUUID("owner",owner);
            t.putString("ownerName",ownerName);if(selected!=null)t.putUUID("selected",selected);t.putBoolean("powered",powered);t.putBoolean("pending",pending);
        }
        @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){
            super.loadAdditional(t,r);stack=t.contains("watch")?ItemStack.parseOptional(r,t.getCompound("watch")):ItemStack.EMPTY;
            if(t.hasUUID("relayId"))relayId=t.getUUID("relayId");if(t.hasUUID("owner"))owner=t.getUUID("owner");ownerName=t.getString("ownerName");
            selected=t.hasUUID("selected")?t.getUUID("selected"):null;powered=t.getBoolean("powered");pending=t.getBoolean("pending");
        }
        boolean allowed(ServerPlayer p){return stack.isEmpty()||owner.equals(p.getUUID())||p.hasPermissions(2);}
        List<CompoundTag> choices(){
            List<CompoundTag> list=new ArrayList<>();if(!ChronoFactory.watch(stack))return list;
            CompoundTag data=ChronoFactory.data(stack);if(data.contains("current"))list.add(data.getCompound("current"));
            if(ChronoFactory.creative(stack))for(Tag x:data.getList("saved",Tag.TAG_COMPOUND))list.add((CompoundTag)x);
            return list;
        }
        CompoundTag choice(){
            List<CompoundTag> list=choices();for(CompoundTag t:list)if(selected!=null&&selected.equals(t.getUUID("id")))return t;
            if(list.isEmpty())return null;selected=list.get(0).getUUID("id");setChanged();return list.get(0);
        }
        CompoundTag job(ServerLevel l){
            for(Tag x:ChronoFactory.world(l).echoes){CompoundTag a=(CompoundTag)x;if(a.hasUUID("relayId")&&relayId.equals(a.getUUID("relayId")))return a;}return null;
        }
        void cancel(ServerLevel l){
            pending=false;CompoundTag a=job(l);if(a!=null){a.putBoolean("dismiss",true);ChronoFactory.world(l).setDirty();}setChanged();
        }
        void insert(ServerPlayer p,ItemStack source){
            if(!(level instanceof ServerLevel l)||!stack.isEmpty()||!ChronoFactory.watch(source)||ChronoFactory.RECORDINGS.containsKey(p.getUUID()))return;
            if(ChronoFactory.watchActive(l.getServer(),ChronoFactory.id(source))){ChronoFactory.say(p,"Dismiss this watch's Echoes before inserting it.");return;}
            stack=source.split(1);owner=p.getUUID();ownerName=p.getGameProfile().getName();selected=null;choice();
            powered=l.hasNeighborSignal(worldPosition);pending=powered;setChanged();
        }
        void eject(ServerPlayer p){
            if(!(level instanceof ServerLevel l)||!allowed(p)||stack.isEmpty())return;
            cancel(l);ItemStack returned=stack;stack=ItemStack.EMPTY;selected=null;
            p.getInventory().add(returned);if(!returned.isEmpty())p.drop(returned,false);setChanged();
        }
        void sampleSignal(ServerLevel l){
            boolean signal=l.hasNeighborSignal(worldPosition);
            if(signal!=powered){
                powered=signal;CompoundTag current=job(l),tl=choice();
                if(signal){if(tl!=null&&(current==null||(current.getBoolean("dismiss")&&ChronoFactory.repeats(tl)==0)))pending=true;}
                else if(tl==null||ChronoFactory.repeats(tl)==0){pending=false;if(current!=null&&ChronoFactory.repeats(current.getCompound("timeline"))==0)cancel(l);}
                setChanged();
            }
            BlockState state=getBlockState();if(state.getValue(RelayBlock.POWERED)!=signal)l.setBlock(worldPosition,state.setValue(RelayBlock.POWERED,signal),3);
        }
        static void tick(Level l,BlockPos pos,BlockState state,Dock dock){
            if(!(l instanceof ServerLevel sl))return;dock.sampleSignal(sl);
            CompoundTag tl=dock.choice();if(tl==null){dock.pending=false;return;}
            CompoundTag current=dock.job(sl);
            if(current!=null)return;
            boolean endless=ChronoFactory.repeats(tl)==0;
            if(endless&&!dock.powered){if(dock.pending){dock.pending=false;dock.setChanged();}return;}
            // Only a rising edge starts a finite run; a sustained high signal
            // never starts it again. A pending pulse survives energy shortages.
            if(dock.pending&&ChronoFactory.manifestRelay(sl,dock,tl)){dock.pending=false;dock.setChanged();}
        }
        IEnergyStorage energyPort(){return new IEnergyStorage(){
            public int receiveEnergy(int n,boolean simulate){
                if(!ChronoFactory.watch(stack)||ChronoFactory.creative(stack))return 0;
                int accepted=Math.max(0,Math.min(n,ChronoFactory.CAPACITY-ChronoFactory.energy(stack)));
                if(!simulate&&accepted>0){ChronoFactory.energy(stack,ChronoFactory.energy(stack)+accepted);setChanged();}return accepted;
            }
            public int extractEnergy(int n,boolean simulate){return 0;}
            public int getEnergyStored(){return ChronoFactory.watch(stack)&&!ChronoFactory.creative(stack)?ChronoFactory.energy(stack):0;}
            public int getMaxEnergyStored(){return ChronoFactory.watch(stack)&&!ChronoFactory.creative(stack)?ChronoFactory.CAPACITY:0;}
            public boolean canExtract(){return false;}
            public boolean canReceive(){return ChronoFactory.watch(stack)&&!ChronoFactory.creative(stack);}
        };}
        void send(ServerPlayer p,boolean open){
            CompoundTag state=new CompoundTag();state.putBoolean("available",true);state.putBoolean("allowed",allowed(p));state.putBoolean("powered",powered);state.putBoolean("pending",pending);
            state.putBoolean("creative",ChronoFactory.creative(stack));state.putInt("energy",stack.isEmpty()?0:ChronoFactory.energy(stack));
            if(!stack.isEmpty())state.put("watch",stack.copyWithCount(1).getItem()==ChronoFactory.CREATIVE.get()?new ItemStack(ChronoFactory.CREATIVE.get()).save(p.registryAccess()):new ItemStack(ChronoFactory.PERSONAL.get()).save(p.registryAccess()));
            CompoundTag tl=choice();if(tl!=null)state.putUUID("selected",tl.getUUID("id"));
            ListTag entries=new ListTag();for(CompoundTag entry:choices()){CompoundTag row=new CompoundTag();row.putUUID("id",entry.getUUID("id"));row.putString("name",entry.getString("name"));row.putInt("repeats",ChronoFactory.repeats(entry));entries.add(row);}state.put("choices",entries);
            CompoundTag a=level instanceof ServerLevel l?job(l):null;state.putBoolean("active",a!=null);
            if(a!=null){state.putInt("cycle",a.getInt("cycles")+1);state.putString("reason",a.getString("reason"));}
            else if(tl==null)state.putString("reason",stack.isEmpty()?"Insert a recorded Timekeeper":"No saved timeline");
            else if(!ChronoFactory.validTimeline(tl))state.putString("reason","Recording requires an update");
            else if(!level.dimension().location().toString().equals(tl.getString("dimension")))state.putString("reason","Relay must be in recording dimension");
            else if(pending&&!ChronoFactory.creative(stack)&&ChronoFactory.energy(stack)<=0)state.putString("reason","Waiting for FE");
            else if(pending)state.putString("reason","Armed - waiting for start chunk / previous Echo");
            else state.putString("reason",powered?"Signal high - awaiting next rising edge":"Ready for redstone");
            PacketDistributor.sendToPlayer(p,new State(worldPosition,state,open));
        }
    }
    public record Command(BlockPos pos,String action,UUID timeline,int count) implements CustomPacketPayload{
        public static final Type<Command> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ChronoFactory.ID,"relay_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Command> CODEC=new StreamCodec<>(){
            public Command decode(RegistryFriendlyByteBuf b){return new Command(b.readBlockPos(),b.readUtf(24),b.readUUID(),b.readVarInt());}
            public void encode(RegistryFriendlyByteBuf b,Command p){b.writeBlockPos(p.pos());b.writeUtf(p.action(),24);b.writeUUID(p.timeline());b.writeVarInt(p.count());}
        };public Type<Command> type(){return TYPE;}
    }
    public record State(BlockPos pos,CompoundTag state,boolean open) implements CustomPacketPayload{
        public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ChronoFactory.ID,"relay_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=new StreamCodec<>(){
            public State decode(RegistryFriendlyByteBuf b){BlockPos pos=b.readBlockPos();CompoundTag t=b.readNbt();return new State(pos,t==null?new CompoundTag():t,b.readBoolean());}
            public void encode(RegistryFriendlyByteBuf b,State p){b.writeBlockPos(p.pos());b.writeNbt(p.state());b.writeBoolean(p.open());}
        };public Type<State> type(){return TYPE;}
    }
    static void command(ServerPlayer p,Command msg){
        ServerLevel l=p.serverLevel();BlockPos pos=msg.pos();
        if(p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!l.hasChunkAt(pos)||!(l.getBlockEntity(pos) instanceof Dock dock)){
            PacketDistributor.sendToPlayer(p,new State(pos,new CompoundTag(),false));return;
        }
        if(dock.allowed(p)&&!ChronoFactory.RECORDINGS.containsKey(p.getUUID())){
            switch(msg.action()){
                case "eject" -> dock.eject(p);
                case "stop" -> dock.cancel(l);
                case "select" -> {if(dock.job(l)==null&&!dock.pending){for(CompoundTag t:dock.choices())if(t.getUUID("id").equals(msg.timeline())){dock.selected=msg.timeline();dock.setChanged();break;}}}
                case "repeat" -> {if(dock.job(l)==null&&!dock.pending&&msg.count()>=0&&msg.count()<=ChronoFactory.MAX_REPEATS&&!dock.stack.isEmpty()){
                    CompoundTag data=ChronoFactory.data(dock.stack);if(data.contains("current")){CompoundTag tl=data.getCompound("current");if(tl.getUUID("id").equals(msg.timeline())){tl.putInt("repeats",msg.count());data.put("current",tl);}}
                    ListTag saved=data.getList("saved",Tag.TAG_COMPOUND);for(Tag x:saved){CompoundTag tl=(CompoundTag)x;if(tl.getUUID("id").equals(msg.timeline()))tl.putInt("repeats",msg.count());}data.put("saved",saved);
                    ChronoFactory.put(dock.stack,data);dock.setChanged();
                }}
                default -> {}
            }
        }
        dock.send(p,false);
    }
}
