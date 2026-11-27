package com.ronec.chronofactory;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ChronoFactory.ID)
public final class ChronoFactory {
    public static final String ID="chronofactory";
    static final String BACKUP="chronofactory_inventory";
    static final int CAPACITY=100000, MAX_ACTIONS=2400, MAX_TIMELINES=20;
    static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    public static final net.neoforged.neoforge.registries.DeferredItem<Watch> PERSONAL=ITEMS.register("personal_timekeeper",()->new Watch(false));
    public static final net.neoforged.neoforge.registries.DeferredItem<Watch> CREATIVE=ITEMS.register("creative_timekeeper",()->new Watch(true));
    static final int[] TINTS={0xFFFFFF,0xB085FF,0x77DFFF,0xFF7979,0x83EF9B,0xFFD078,0xFF9CD6,0x86A3FF};
    static final String[] TINT_NAMES={"Natural","Violet","Cyan","Red","Green","Amber","Pink","Blue"};
    static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,ID);
    static final net.neoforged.neoforge.registries.DeferredHolder<EntityType<?>,EntityType<EchoEntity>> ECHO=ENTITIES.register("echo",()->EntityType.Builder.<EchoEntity>of(EchoEntity::new,MobCategory.MISC).sized(.6f,1.8f).clientTrackingRange(8).updateInterval(1).build(ID+":echo"));
    static int tint(CompoundTag t){return Math.floorMod(t.getInt("tint"),TINTS.length);}
    static final Map<UUID,Recording> RECORDINGS=new HashMap<>();
    public ChronoFactory(IEventBus bus) {
        ITEMS.register(bus);ENTITIES.register(bus);
        bus.addListener((EntityAttributeCreationEvent e)->e.put(ECHO.get(),Mob.createMobAttributes().build()));
        bus.addListener(ChronoFactory::registerNetwork);
        bus.addListener(ChronoFactory::capabilities);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){e.accept(PERSONAL.get());e.accept(CREATIVE.get());}});
        IEventBus game=NeoForge.EVENT_BUS;
        game.addListener(ChronoFactory::playerTick);
        game.addListener(ChronoFactory::levelTick);
        game.addListener(ChronoFactory::onUse);
        game.addListener(ChronoFactory::onBreak);
        game.addListener(ChronoFactory::onAttackBlock);
        game.addListener(ChronoFactory::logout);
        game.addListener(ChronoFactory::login);
        game.addListener(ChronoFactory::death);
        game.addListener(ChronoFactory::toss);
        game.addListener(ChronoFactory::pickup);
    }
    public record Click() implements CustomPacketPayload {
        public static final Click INSTANCE=new Click();
        public static final Type<Click> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"click"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Click> CODEC=StreamCodec.unit(INSTANCE);
        public Type<Click> type(){return TYPE;}
    }
    public record AirSwing() implements CustomPacketPayload {
        public static final AirSwing INSTANCE=new AirSwing();
        public static final Type<AirSwing> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"air_swing"));
        public static final StreamCodec<RegistryFriendlyByteBuf,AirSwing> CODEC=StreamCodec.unit(INSTANCE);
        public Type<AirSwing> type(){return TYPE;}
    }
    public record HeldInput(boolean use,boolean down) implements CustomPacketPayload {
        public static final Type<HeldInput> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"held_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,HeldInput> CODEC=new StreamCodec<>(){
            public HeldInput decode(RegistryFriendlyByteBuf b){return new HeldInput(b.readBoolean(),b.readBoolean());}
            public void encode(RegistryFriendlyByteBuf b,HeldInput p){b.writeBoolean(p.use());b.writeBoolean(p.down());}
        };
        public Type<HeldInput> type(){return TYPE;}
    }
    public record InputFrame(int flags,int slot,float yaw,float pitch) implements CustomPacketPayload {
        public static final Type<InputFrame> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"input_frame"));
        public static final StreamCodec<RegistryFriendlyByteBuf,InputFrame> CODEC=new StreamCodec<>(){
            public InputFrame decode(RegistryFriendlyByteBuf b){return new InputFrame(b.readVarInt(),b.readVarInt(),b.readFloat(),b.readFloat());}
            public void encode(RegistryFriendlyByteBuf b,InputFrame f){b.writeVarInt(f.flags());b.writeVarInt(f.slot());b.writeFloat(f.yaw());b.writeFloat(f.pitch());}
        };
        public Type<InputFrame> type(){return TYPE;}
    }
    public record RenameTimeline(UUID watch,UUID timeline,String name) implements CustomPacketPayload {
        public static final Type<RenameTimeline> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"rename_timeline"));
        public static final StreamCodec<RegistryFriendlyByteBuf,RenameTimeline> CODEC=new StreamCodec<>(){
            public RenameTimeline decode(RegistryFriendlyByteBuf b){return new RenameTimeline(b.readUUID(),b.readUUID(),b.readUtf(48));}
            public void encode(RegistryFriendlyByteBuf b,RenameTimeline p){b.writeUUID(p.watch());b.writeUUID(p.timeline());b.writeUtf(p.name(),48);}
        };
        public Type<RenameTimeline> type(){return TYPE;}
    }
    public record UiCommand(UUID key,String action,int index) implements CustomPacketPayload {
        public static final Type<UiCommand> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"ui_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf,UiCommand> CODEC=new StreamCodec<>() {
            public UiCommand decode(RegistryFriendlyByteBuf b){return new UiCommand(b.readUUID(),b.readUtf(32),b.readVarInt());}
            public void encode(RegistryFriendlyByteBuf b,UiCommand p){b.writeUUID(p.key());b.writeUtf(p.action(),32);b.writeVarInt(p.index());}
        };
        public Type<UiCommand> type(){return TYPE;}
    }
    public record InspectState(UUID key,CompoundTag state,boolean open) implements CustomPacketPayload {
        public static final Type<InspectState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"inspect_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,InspectState> CODEC=new StreamCodec<>(){
            public InspectState decode(RegistryFriendlyByteBuf b){UUID key=b.readUUID();CompoundTag tag=b.readNbt();return new InspectState(key,tag==null?new CompoundTag():tag,b.readBoolean());}
            public void encode(RegistryFriendlyByteBuf b,InspectState p){b.writeUUID(p.key());b.writeNbt(p.state());b.writeBoolean(p.open());}
        };
        public Type<InspectState> type(){return TYPE;}
    }
    public record UiState(UUID key,CompoundTag state) implements CustomPacketPayload {
        public static final Type<UiState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"ui_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,UiState> CODEC=new StreamCodec<>() {
            public UiState decode(RegistryFriendlyByteBuf b){CompoundTag t=b.readNbt();return new UiState(b.readUUID(),t==null?new CompoundTag():t);}
            public void encode(RegistryFriendlyByteBuf b,UiState p){b.writeNbt(p.state());b.writeUUID(p.key());}
        };
        public Type<UiState> type(){return TYPE;}
    }
    static void registerNetwork(RegisterPayloadHandlersEvent e){
        var registrar=e.registrar("8");
        registrar.playToServer(RenameTimeline.TYPE,RenameTimeline.CODEC,(msg,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p)renameTimeline(p,msg);}));
        registrar.playToClient(InspectState.TYPE,InspectState.CODEC,(msg,ctx)->ctx.enqueueWork(()->ClientControls.inspect(msg)));
        registrar.playToServer(InputFrame.TYPE,InputFrame.CODEC,(msg,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p)rawInput(p,msg);}));
        registrar.playToServer(HeldInput.TYPE,HeldInput.CODEC,(msg,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p)inputEdge(p,msg);}));
        registrar.playToServer(AirSwing.TYPE,AirSwing.CODEC,(msg,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p)recordAirSwing(p);}));
        registrar.playToServer(Click.TYPE,Click.CODEC,(msg,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p) cycle(p);}));
        registrar.playToServer(UiCommand.TYPE,UiCommand.CODEC,(msg,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p) uiCommand(p,msg);}));
        registrar.playToClient(UiState.TYPE,UiState.CODEC,(msg,ctx)->ctx.enqueueWork(()->ClientControls.show(msg)));
    }
    static CompoundTag data(ItemStack s){return s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    static void put(ItemStack s,CompoundTag t){s.set(DataComponents.CUSTOM_DATA,CustomData.of(t));}
    static UUID id(ItemStack s){CompoundTag t=data(s);if(!t.hasUUID("id")){t.putUUID("id",UUID.randomUUID());put(s,t);}return t.getUUID("id");}
    static boolean creative(ItemStack s){return s.getItem()==CREATIVE.get();}
    static boolean watch(ItemStack s){return s.getItem() instanceof Watch;}
    static int energy(ItemStack s){CompoundTag t=data(s);return t.contains("energy")?t.getInt("energy"):CAPACITY;}
    static void energy(ItemStack s,int n){CompoundTag t=data(s);t.putInt("energy",Math.max(0,Math.min(CAPACITY,n)));put(s,t);}
    static void capabilities(RegisterCapabilitiesEvent e){
        e.registerItem(Capabilities.EnergyStorage.ITEM,(s,c)->new IEnergyStorage(){
            public int receiveEnergy(int n,boolean simulate){int v=Math.max(0,Math.min(n,CAPACITY-energy(s)));if(!simulate)energy(s,energy(s)+v);return v;}
            public int extractEnergy(int n,boolean simulate){return 0;}
            public int getEnergyStored(){return energy(s);}
            public int getMaxEnergyStored(){return CAPACITY;}
            public boolean canExtract(){return false;}
            public boolean canReceive(){return true;}
        },PERSONAL.get());
    }
    public static final class Watch extends Item {
        final boolean unlimited;
        Watch(boolean creative){super(new Item.Properties().stacksTo(1));unlimited=creative;}
        @Override public boolean isBarVisible(ItemStack s){return !unlimited;}
        @Override public int getBarWidth(ItemStack s){return Math.round(13f*energy(s)/CAPACITY);}
        @Override public int getBarColor(ItemStack s){return 0xA84DF5;}
        @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){
            ItemStack s=p.getItemInHand(h);
            if(p instanceof ServerPlayer sp&&h==InteractionHand.MAIN_HAND){sendUi(sp,s);}
            return InteractionResultHolder.sidedSuccess(s,l.isClientSide());
        }
    }
    static void say(ServerPlayer p,String s){p.displayClientMessage(Component.literal("[Chrono] "+s),true);}
    static ItemStack find(ServerPlayer p,UUID key){
        for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(watch(s)&&data(s).hasUUID("id")&&data(s).getUUID("id").equals(key))return s;}
        return ItemStack.EMPTY;
    }
    static WorldData world(ServerLevel l){return l.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WorldData::new,WorldData::load,null),"chrono_factory");}
    static final class WorldData extends SavedData{
        final ListTag echoes=new ListTag();
        static WorldData load(CompoundTag t,HolderLookup.Provider r){WorldData d=new WorldData();d.echoes.addAll(t.getList("echoes",Tag.TAG_COMPOUND));return d;}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){t.put("echoes",echoes.copy());return t;}
    }
    static ListTag timeline(CompoundTag t){return t.getList("actions",Tag.TAG_COMPOUND);}
    static CompoundTag point(Player p,String type){
        CompoundTag t=new CompoundTag();t.putString("type",type);t.putDouble("x",p.getX());t.putDouble("y",p.getY());t.putDouble("z",p.getZ());t.putFloat("yaw",p.getYRot());t.putFloat("pitch",p.getXRot());t.putBoolean("sneak",p.isShiftKeyDown());return t;
    }
    static final class Recording{
        final UUID watch;
        final String dimension;
        final ListTag actions=new ListTag();
        CompoundTag last;
        BlockPos container;
        Map<String,ItemStack> previous=new HashMap<>();
        List<ItemStack> containerBefore=new ArrayList<>();
        CompoundTag pendingUse;
        int bulkStart=-1;
        boolean menuSeen;
        int tick,lastMoveTick,lastAirTick=-20;
        boolean useDown,attackDown;
        int useEpoch,attackTargetStart;
        BlockPos attackTarget;
        CompoundTag heldUse;
        final List<Integer> raw=new ArrayList<>(),motion=new ArrayList<>();
        Vec3 lastRawPos;
        final ListTag gui=new ListTag();
        int rawFlags,rawSlot,rawLast=-100;
        float rawYaw,rawPitch;
        Recording(ServerPlayer p,UUID w){watch=w;dimension=p.level().dimension().location().toString();last=point(p,"move");actions.add(last);rawSlot=p.getInventory().selected;rawYaw=p.getYRot();rawPitch=p.getXRot();lastRawPos=p.position();}
        void closeUse(int end){
            if(heldUse==null)return;
            int start=heldUse.getInt("recordTick");int[] offsets=heldUse.getIntArray("useOffsets");
            int minimum=offsets.length==0?1:offsets[offsets.length-1]+1;
            int lastClick=heldUse.getInt("lastClick");int finish=useDown?end:Math.min(end,lastClick+4);
            if(heldUse.contains("releaseTick"))finish=heldUse.getInt("releaseTick");
            heldUse.putInt("duration",Math.max(minimum,Math.max(1,finish-start)));
            heldUse=null;
        }
        boolean sameUse(CompoundTag a,CompoundTag b){
            return a.getLong("pos")==b.getLong("pos")&&a.getInt("face")==b.getInt("face")&&a.getString("block").equals(b.getString("block"))
                &&a.getBoolean("sneak")==b.getBoolean("sneak")&&Objects.equals(a.get("item"),b.get("item"))
                &&a.getInt("holdId")==b.getInt("holdId");
        }
        void add(CompoundTag a){
            if(!a.contains("recordTick"))a.putInt("recordTick",tick);
            if("use".equals(a.getString("type"))){
                int at=a.getInt("recordTick");
                if(heldUse!=null&&sameUse(heldUse,a)&&(a.getInt("holdId")!=0||at-heldUse.getInt("lastClick")<=8)){
                    int[] old=heldUse.getIntArray("useOffsets");int offset=Math.max(0,at-heldUse.getInt("recordTick"));
                    if(old.length<2400&&offset<=24000){
                        int[] offsets=Arrays.copyOf(old,old.length+1);offsets[old.length]=Math.max(offset,old[old.length-1]+1);
                        heldUse.putIntArray("useOffsets",offsets);heldUse.putInt("duration",offsets[old.length]+1);heldUse.putInt("lastClick",at);return;
                    }
                }
                closeUse(at);
                if(actions.size()>=MAX_ACTIONS)return;
                a.putIntArray("useOffsets",new int[]{0});a.putInt("duration",1);a.putInt("lastClick",at);actions.add(a);
                heldUse=a;
                if(a.contains("releaseTick")){if(heldUse==a)closeUse(a.getInt("releaseTick"));else a.putInt("duration",Math.max(1,a.getInt("releaseTick")-at));}
            }else{
                closeUse(tick);
                if(actions.size()<MAX_ACTIONS)actions.add(a);
            }
        }
        void move(ServerPlayer p){CompoundTag a=point(p,"move");if(Math.abs(a.getDouble("x")-last.getDouble("x"))+Math.abs(a.getDouble("y")-last.getDouble("y"))+Math.abs(a.getDouble("z")-last.getDouble("z"))>.025||Math.abs(a.getFloat("yaw")-last.getFloat("yaw"))>8||Math.abs(a.getFloat("pitch")-last.getFloat("pitch"))>8){a.putInt("moveTicks",Math.max(1,Math.min(4,tick-lastMoveTick)));add(a);last=a;lastMoveTick=tick;}}
    }
    static void cycle(ServerPlayer p){
        ItemStack s=p.getMainHandItem();if(!watch(s))return;
        UUID key=id(s);Recording r=RECORDINGS.get(p.getUUID());
        if(r!=null){if(r.watch.equals(key))finish(p,true);else say(p,"Another Timekeeper is recording.");return;}
        if(p.getPersistentData().contains(BACKUP)){restore(p);s=p.getMainHandItem();if(!watch(s)){say(p,"Recovered your previous inventory. Select your Timekeeper to record.");return;}key=id(s);}
        CompoundTag t=data(s);
        if((!creative(s)&&active(p,key,null))||(creative(s)&&t.contains("current")&&active(p,key,t.getCompound("current").getUUID("id")))){dismiss(p,key,creative(s)?t.getCompound("current").getUUID("id"):null);if(!t.getBoolean("retain")){t.remove("current");put(s,t);}say(p,"Timeline dismissed.");return;}
        if(t.contains("current")){manifest(p,s,t.getCompound("current"));return;}
        if(p.isSpectator()){say(p,"Spectators cannot record.");return;}
        if(p.containerMenu!=p.inventoryMenu)p.closeContainer();
        int slot=p.getInventory().selected;
        CompoundTag backup=new CompoundTag();backup.putInt("slot",slot);backup.putUUID("watch",key);backup.putString("mode",p.gameMode.getGameModeForPlayer().getName());
        p.getInventory().setItem(slot,ItemStack.EMPTY);
        backup.put("inventory",p.getInventory().save(new ListTag()));
        p.getPersistentData().put(BACKUP,backup);
        if(p.isCreative())p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();p.getInventory().setItem(slot,s);
        p.getInventory().selected=slot;p.getInventory().setChanged();
        CompoundTag recordingTag=data(s);recordingTag.putBoolean("recording",true);put(s,recordingTag);
        RECORDINGS.put(p.getUUID(),new Recording(p,key));
        p.inventoryMenu.broadcastChanges();p.inventoryMenu.sendAllDataToRemote();say(p,"RECORDING STARTED - walk or interact, then left-click again to finish. Your original inventory is protected.");
    }
    static void finish(ServerPlayer p,boolean save){
        Recording r=RECORDINGS.remove(p.getUUID());if(r==null){restore(p);return;}
        if(r.menuSeen&&r.container!=null)captureTransfers(p,r);
        else if(r.pendingUse!=null)r.add(r.pendingUse);
        r.closeUse(r.tick);
        if(p.containerMenu!=p.inventoryMenu)p.closeContainer();
        r.move(p);
        ItemStack s=find(p,r.watch);
        if(save&&!s.isEmpty()){
            if(r.actions.size()==1){CompoundTag idle=point(p,"wait");idle.putInt("ticks",Math.max(1,Math.min(1200,r.tick)));r.add(idle);}
            CompoundTag t=data(s),tl=new CompoundTag();tl.putUUID("id",UUID.randomUUID());tl.putString("name","Timeline "+(t.getList("saved",Tag.TAG_COMPOUND).size()+1));tl.putString("dimension",r.dimension);tl.put("actions",r.actions);tl.putIntArray("raw",r.raw.stream().mapToInt(Integer::intValue).toArray());tl.putIntArray("motion",r.motion.stream().mapToInt(Integer::intValue).toArray());tl.put("gui",r.gui);tl.putInt("rawTicks",r.raw.size()/3);tl.putBoolean("explicitPickup",true);tl.putBoolean("strict",t.getBoolean("strict"));tl.putInt("tint",tint(t));t.put("current",tl);put(s,t);
        }
        restore(p);say(p,save&&!s.isEmpty()?"RECORDING SAVED ("+r.actions.size()+" steps). Left-click to summon the Echo.":"Recording cancelled; inventory restored.");
    }
    static void restore(ServerPlayer p){
        CompoundTag persistent=p.getPersistentData();if(!persistent.contains(BACKUP))return;
        CompoundTag b=persistent.getCompound(BACKUP);UUID key=b.getUUID("watch");ItemStack timekeeper=find(p,key);
        List<ItemStack> extras=new ArrayList<>();
        for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(!s.isEmpty()&&s!=timekeeper)extras.add(s.copy());}
        if(!timekeeper.isEmpty()){CompoundTag tag=data(timekeeper);tag.remove("recording");put(timekeeper,tag);}
        p.getInventory().clearContent();p.getInventory().load(b.getList("inventory",Tag.TAG_COMPOUND));
        p.getInventory().setItem(b.getInt("slot"),timekeeper);
        for(ItemStack s:extras){p.getInventory().add(s);if(!s.isEmpty())p.drop(s,false);}
        persistent.remove(BACKUP);
        if("creative".equals(b.getString("mode")))p.setGameMode(GameType.CREATIVE);
        p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();p.inventoryMenu.sendAllDataToRemote();
    }
    static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)finish(p,false);}
    static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){RECORDINGS.remove(p.getUUID());restore(p);}}
    static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.getPersistentData().contains(BACKUP))finish(p,false);}
    static void toss(ItemTossEvent e){
        if(!(e.getPlayer() instanceof ServerPlayer p)||p instanceof FakePlayer||e.isCanceled())return;
        Recording r=RECORDINGS.get(p.getUUID());if(r==null)return;
        ItemStack stack=e.getEntity().getItem();
        if(watch(stack)){e.setCanceled(true);p.getInventory().add(stack);return;}
        r.move(p);CompoundTag a=point(p,"drop");a.putInt("count",stack.getCount());a.put("item",stack.copyWithCount(1).save(p.registryAccess()));
        Vec3 v=e.getEntity().getDeltaMovement();a.putDouble("vx",v.x);a.putDouble("vy",v.y);a.putDouble("vz",v.z);r.add(a);
        if(r.menuSeen)adjustBaseline(r,stack,-stack.getCount());
    }
    static void pickup(ItemEntityPickupEvent.Post e){
        if(!(e.getPlayer() instanceof ServerPlayer p)||p instanceof FakePlayer)return;
        Recording r=RECORDINGS.get(p.getUUID());if(r==null)return;
        ItemStack stack=e.getOriginalStack();int count=stack.getCount()-e.getCurrentStack().getCount();if(count<=0||watch(stack))return;
        r.move(p);CompoundTag a=point(p,"pickup");a.putInt("count",count);a.put("item",stack.copyWithCount(1).save(p.registryAccess()));
        Vec3 v=e.getItemEntity().position();a.putDouble("ix",v.x);a.putDouble("iy",v.y);a.putDouble("iz",v.z);r.add(a);
        if(r.menuSeen)adjustBaseline(r,stack,count);
    }
    static void rawInput(ServerPlayer p,InputFrame frame){
        Recording r=RECORDINGS.get(p.getUUID());if(r==null||!Float.isFinite(frame.yaw())||!Float.isFinite(frame.pitch()))return;
        r.rawFlags=frame.flags()&2047;r.rawSlot=Math.max(0,Math.min(8,frame.slot()));r.rawYaw=frame.yaw()%360;r.rawPitch=Math.max(-90,Math.min(90,frame.pitch()));r.rawLast=r.tick;
        boolean use=(r.rawFlags&StrictReplay.USE)!=0,attack=(r.rawFlags&StrictReplay.ATTACK)!=0;
        if(use!=r.useDown)inputEdge(p,new HeldInput(true,use));
        if(attack!=r.attackDown)inputEdge(p,new HeldInput(false,attack));
    }
    public static void rawGui(ServerPlayer p,int slot,int button,int click,boolean close){
        if(p instanceof FakePlayer)return;Recording r=RECORDINGS.get(p.getUUID());
        if(r==null||r.gui.size()>=4096)return;
        CompoundTag event=new CompoundTag();event.putString("kind",close?"close":"click");event.putInt("tick",r.raw.size()/3);event.putInt("slot",slot);event.putInt("button",button);event.putInt("click",click);event.putBoolean("close",close);event.putBoolean("inventory",p.containerMenu==p.inventoryMenu);event.putInt("slots",p.containerMenu.slots.size());r.gui.add(event);
    }
    public static void afterGui(ServerPlayer p){
        if(p instanceof FakePlayer)return;
        Recording r=RECORDINGS.get(p.getUUID());
        if(r==null||r.container==null)return;
        if(p.containerMenu!=p.inventoryMenu){r.menuSeen=true;r.pendingUse=null;}
    }
    static void inputEdge(ServerPlayer p,HeldInput edge){
        Recording r=RECORDINGS.get(p.getUUID());if(r==null)return;
        if(edge.use()){
            if(edge.down()&&!r.useDown){r.useDown=true;r.useEpoch++;if(r.useEpoch==0)r.useEpoch=1;}
            else if(!edge.down()&&r.useDown){
                if(r.heldUse!=null)r.heldUse.putInt("releaseTick",r.tick);
                r.useDown=false;
                if(r.pendingUse!=null&&r.pendingUse.getInt("holdId")==r.useEpoch)r.pendingUse.putInt("releaseTick",r.tick);
                r.closeUse(r.tick);
            }
        }else{
            if(edge.down()&&!r.attackDown){r.attackDown=true;r.attackTarget=null;r.attackTargetStart=r.tick;}
            else if(!edge.down()&&r.attackDown){r.attackDown=false;r.attackTarget=null;
                if(!r.actions.isEmpty()){
                    CompoundTag last=r.actions.getCompound(r.actions.size()-1);
                    if("air_swing".equals(last.getString("type"))&&last.contains("recordTick"))
                        last.putInt("ticks",Math.max(1,Math.min(last.getInt("ticks"),r.tick-last.getInt("recordTick"))));
                }
            }
        }
    }
    static void onAttackBlock(PlayerInteractEvent.LeftClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer||e.isCanceled())return;
        Recording r=RECORDINGS.get(p.getUUID());if(r==null||watch(p.getMainHandItem()))return;
        String action=e.getAction().name();
        if("START".equals(action)){
            if(r.attackTarget==null||!r.attackTarget.equals(e.getPos())){
                r.attackTarget=e.getPos().immutable();r.attackTargetStart=r.tick;
            }
        }else if("ABORT".equals(action))r.attackTarget=null;
    }
    static BlockHitResult aimedBlock(Player p){
        Vec3 eye=p.getEyePosition();return p.level().clip(new ClipContext(eye,eye.add(p.getLookAngle().scale(Math.min(5,p.blockInteractionRange()))),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
    }
    static void recordAirSwing(ServerPlayer p){
        Recording r=RECORDINGS.get(p.getUUID());
        if(r==null||p.containerMenu!=p.inventoryMenu||r.tick-r.lastAirTick<3||watch(p.getMainHandItem())||aimedBlock(p).getType()!=HitResult.Type.MISS)return;
        r.lastAirTick=r.tick;r.move(p);
        CompoundTag a=point(p,"air_swing");a.putInt("ticks",4);a.putInt("recordTick",r.tick);
        ItemStack held=p.getMainHandItem();if(!held.isEmpty())a.put("item",held.copyWithCount(1).save(p.registryAccess()));r.add(a);
    }
    static String itemKey(ItemStack s){ItemStack copy=s.copyWithCount(1);if(copy.isDamageableItem())copy.setDamageValue(0);return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(copy.getItem()).toString()+":"+copy.getComponentsPatch();}
    static Map<String,ItemStack> snapshot(ServerPlayer p){
        Map<String,ItemStack> map=new HashMap<>();
        for(int i=0;i<36;i++){ItemStack s=p.getInventory().getItem(i);if(s.isEmpty()||watch(s))continue;String k=itemKey(s);if(map.containsKey(k))map.get(k).grow(s.getCount());else map.put(k,s.copy());}
        // Cursor-held stacks still belong to the recording inventory until actually
        // deposited. This avoids false transfers during ordinary click/drag operations.
        if(p.containerMenu!=p.inventoryMenu){ItemStack cursor=p.containerMenu.getCarried();if(!cursor.isEmpty()&&!watch(cursor)){
            String key=itemKey(cursor);if(map.containsKey(key))map.get(key).grow(cursor.getCount());else map.put(key,cursor.copy());
        }}
        return map;
    }
    static void adjustBaseline(Recording r,ItemStack stack,int delta){
        if(stack.isEmpty()||watch(stack))return;
        String key=itemKey(stack);ItemStack before=r.previous.getOrDefault(key,ItemStack.EMPTY);
        int n=Math.max(0,before.getCount()+delta);
        if(n==0)r.previous.remove(key);else r.previous.put(key,stack.copyWithCount(n));
    }
    static List<ItemStack> slotSnapshot(IItemHandler h){
        List<ItemStack> slots=new ArrayList<>();if(h!=null)for(int i=0;i<h.getSlots();i++)slots.add(h.getStackInSlot(i).copy());return slots;
    }
    static int matchingCount(ItemStack stack,ItemStack wanted){return matches(stack,wanted)?stack.getCount():0;}
    static void recordTransfer(ServerPlayer p,Recording r,ItemStack item,int count,boolean take,int slot){
        CompoundTag a=point(p,"transfer");a.putLong("pos",r.container.asLong());a.putBoolean("take",take);a.putInt("count",count);a.putInt("slot",slot);a.put("item",item.copyWithCount(1).save(p.registryAccess()));
        if(take)r.bulkStart=-1;else if(r.bulkStart<0)r.bulkStart=r.actions.size();
        r.add(a);
    }
    static void captureTransfers(ServerPlayer p,Recording r){
        if(r.container==null)return;
        Map<String,ItemStack> now=snapshot(p);Set<String> keys=new TreeSet<>(now.keySet());keys.addAll(r.previous.keySet());
        IItemHandler h=handler(p.serverLevel(),r.container);
        List<ItemStack> current=slotSnapshot(h);
        int firstAction=r.actions.size();boolean deposited=false,withdrew=false;
        for(boolean take:new boolean[]{false,true})for(String k:keys){
            ItemStack before=r.previous.getOrDefault(k,ItemStack.EMPTY),after=now.getOrDefault(k,ItemStack.EMPTY);
            int delta=after.getCount()-before.getCount();
            if(delta==0||(delta>0)!=take)continue;
            ItemStack item=take?after:before;
            int slots=Math.min(current.size(),r.containerBefore.size());
            int[] changes=new int[slots];
            for(int slot=0;slot<slots;slot++){
                int old=matchingCount(r.containerBefore.get(slot),item),cur=matchingCount(current.get(slot),item);
                changes[slot]=take?old-cur:cur-old;
            }
            int requested=Math.abs(delta);
            int[] allocation=TransferAmounts.allocate(requested,changes);
            int recorded=0;
            for(int slot=0;slot<slots;slot++){
                int n=allocation[slot];if(n<=0)continue;
                recordTransfer(p,r,item,n,take,slot);recorded+=n;
            }
            // Some modded inventories expose slots differently from their menu.
            // Only infer an unslotted transfer if the handler cannot supply a
            // slot snapshot. With a real snapshot, require physical evidence.
            if(slots==0&&requested>recorded){recordTransfer(p,r,item,requested-recorded,take,-1);recorded=requested;}
            if(recorded>0){if(take)withdrew=true;else deposited=true;}
        }
        if(deposited&&!withdrew&&now.isEmpty()&&firstAction<r.actions.size()){
            // Keep a complete dump adaptive, while preserving the net recorded
            // quantities and preferred destination slots for the layout.
            r.actions.getCompound(firstAction).putBoolean("bulkStart",true);
            for(int i=firstAction+1;i<r.actions.size();i++)r.actions.getCompound(i).putBoolean("bulkSkip",true);
        }
        r.previous=now;r.containerBefore=current;r.bulkStart=-1;
    }
    static IItemHandler handler(ServerLevel l,BlockPos pos){return l.getCapability(Capabilities.ItemHandler.BLOCK,pos,null);}
    static void onUse(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer||watch(e.getItemStack())||e.isCanceled())return;
        Recording r=RECORDINGS.get(p.getUUID());if(r==null||e.getHand()!=InteractionHand.MAIN_HAND)return;
        if(r.container!=null&&r.menuSeen&&p.containerMenu==p.inventoryMenu){captureTransfers(p,r);r.container=null;r.menuSeen=false;r.pendingUse=null;}
        r.move(p);BlockPos pos=e.getPos();
        CompoundTag a=point(p,"use");a.putInt("recordTick",r.tick);a.putInt("holdId",r.useDown?r.useEpoch:0);a.putLong("pos",pos.asLong());a.putString("block",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(pos).getBlock()).toString());
        BlockHitResult h=e.getHitVec();a.putInt("face",h.getDirection().get3DDataValue());a.putDouble("hx",h.getLocation().x);a.putDouble("hy",h.getLocation().y);a.putDouble("hz",h.getLocation().z);if(!e.getItemStack().isEmpty())a.put("item",e.getItemStack().copyWithCount(1).save(p.registryAccess()));
        if(r.gui.size()<4096){CompoundTag input=a.copy();input.putString("kind","use");input.putInt("tick",r.raw.size()/3);input.putInt("selected",p.getInventory().selected);r.gui.add(input);}
        IItemHandler inventory=handler(p.serverLevel(),pos);
        if(inventory!=null&&!p.isShiftKeyDown()){
            // A chest can be opened while holding an item. Wait until we know whether
            // the interaction opened a menu or was a normal right-click on the machine.
            r.container=pos.immutable();r.previous=snapshot(p);r.containerBefore=slotSnapshot(inventory);r.menuSeen=false;r.pendingUse=a;r.bulkStart=-1;return;
        }
        r.add(a);
    }
    static void onBreak(BlockEvent.BreakEvent e){
        if(!(e.getPlayer() instanceof ServerPlayer p)||p instanceof FakePlayer||e.isCanceled())return;
        Recording r=RECORDINGS.get(p.getUUID());if(r==null)return;r.move(p);
        CompoundTag a=point(p,"break");
        if(r.attackTarget!=null&&r.attackTarget.equals(e.getPos()))a.putInt("duration",Math.max(1,r.tick-r.attackTargetStart+1));
        else a.putInt("duration",1);
        r.attackTarget=null;a.putLong("pos",e.getPos().asLong());a.putString("block",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getState().getBlock()).toString());if(!p.getMainHandItem().isEmpty())a.put("item",p.getMainHandItem().copyWithCount(1).save(p.registryAccess()));r.add(a);
    }
    static void playerTick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer)return;
        Recording r=RECORDINGS.get(p.getUUID());
        if(r!=null){
            if(!r.dimension.equals(p.level().dimension().location().toString())||find(p,r.watch).isEmpty()){finish(p,false);return;}
            int flags=r.tick-r.rawLast<=10?r.rawFlags:0;
            // Record the key state at every server tick, not only successful world actions.
            r.raw.add(flags|(p.getInventory().selected<<12));r.raw.add(Float.floatToIntBits(p.getYRot()));r.raw.add(Float.floatToIntBits(p.getXRot()));
            Vec3 delta=p.position().subtract(r.lastRawPos);r.lastRawPos=p.position();
            r.motion.add(Float.floatToIntBits((float)delta.x));r.motion.add(Float.floatToIntBits((float)delta.y));r.motion.add(Float.floatToIntBits((float)delta.z));
            if(r.container!=null){
                if(p.containerMenu!=p.inventoryMenu){r.menuSeen=true;r.pendingUse=null;}
                else if(r.menuSeen){captureTransfers(p,r);r.container=null;r.menuSeen=false;r.bulkStart=-1;}
                else if(r.pendingUse!=null){r.add(r.pendingUse);r.pendingUse=null;r.container=null;r.bulkStart=-1;}
            }
            // Sample every tick in the air, retaining the recorded jump arc and ledge descent.
            if(++r.tick%3==0||!p.onGround()||Math.abs(p.getY()-r.last.getDouble("y"))>.015){if(p.containerMenu==p.inventoryMenu)r.move(p);}
            if(r.tick==2){p.inventoryMenu.sendAllDataToRemote();}
            if(r.actions.size()>=MAX_ACTIONS||r.raw.size()/3>=12000)finish(p,true);
        }
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            ItemStack s=p.getInventory().getItem(i);if(s.getItem()==PERSONAL.get()&&energy(s)<CAPACITY)energy(s,energy(s)+10);
        }
    }
    static boolean active(ServerPlayer p,UUID key,UUID timeline){
        for(ServerLevel l:p.server.getAllLevels())for(Tag x:world(l).echoes){CompoundTag a=(CompoundTag)x;if(!a.getBoolean("dismiss")&&a.getUUID("owner").equals(p.getUUID())&&a.getUUID("watch").equals(key)&&(timeline==null||a.getCompound("timeline").getUUID("id").equals(timeline)))return true;}return false;
    }
    static void dismiss(ServerPlayer p,UUID key,UUID timeline){
        for(ServerLevel l:p.server.getAllLevels()){WorldData d=world(l);for(Tag x:d.echoes){CompoundTag a=(CompoundTag)x;if(a.getUUID("owner").equals(p.getUUID())&&a.getUUID("watch").equals(key)&&(timeline==null||a.getCompound("timeline").getUUID("id").equals(timeline))){a.putBoolean("dismiss",true);d.setDirty();}}}
    }
    static void manifest(ServerPlayer p,ItemStack s,CompoundTag tl){
        ListTag actions=timeline(tl);if(actions.isEmpty()){say(p,"No recording available. Left-click to start recording.");return;}
        if(tl.getBoolean("strict")&&(tl.getIntArray("raw").length<3||tl.getIntArray("motion").length!=tl.getIntArray("raw").length)){say(p,"Input mode requires a new recording from version 0.4.1 or later for corrected movement.");return;}
        ServerLevel l=null;for(ServerLevel candidate:p.server.getAllLevels())if(candidate.dimension().location().toString().equals(tl.getString("dimension")))l=candidate;
        if(l==null||!l.hasChunkAt(BlockPos.containing(vec(actions.getCompound(0))))){say(p,"Recording start is not loaded.");return;}
        UUID key=id(s);if(active(p,key,tl.getUUID("id"))||(!creative(s)&&active(p,key,null)))return;
        if(!creative(s)&&energy(s)<=0){say(p,"Timekeeper needs energy.");return;}
        CompoundTag a=new CompoundTag();a.putUUID("id",UUID.randomUUID());a.putUUID("owner",p.getUUID());a.putUUID("watch",key);a.putString("name",p.getGameProfile().getName());a.put("timeline",tl.copy());a.putBoolean("creative",creative(s));a.put("position",actions.getCompound(0).copy());a.put("inventory",new ListTag());a.putString("reason","Manifesting");
        WorldData d=world(l);d.echoes.add(a);d.setDirty();say(p,"Echo manifested.");
    }
    static Vec3 vec(CompoundTag a){return new Vec3(a.getDouble("x"),a.getDouble("y"),a.getDouble("z"));}
    static ItemStack template(CompoundTag a,ServerLevel l){return a.contains("item")?ItemStack.parseOptional(l.registryAccess(),a.getCompound("item")):ItemStack.EMPTY;}
    static boolean matches(ItemStack a,ItemStack b){if(a.isEmpty()||b.isEmpty()||a.getItem()!=b.getItem())return false;ItemStack x=a.copyWithCount(1),y=b.copyWithCount(1);if(x.isDamageableItem())x.setDamageValue(0);if(y.isDamageableItem())y.setDamageValue(0);return ItemStack.isSameItemSameComponents(x,y);}
    static boolean equip(FakePlayer f,ItemStack wanted){
        if(wanted.isEmpty()){for(int i=0;i<36;i++)if(f.getInventory().getItem(i).isEmpty()){if(i!=0){ItemStack old=f.getInventory().getItem(0);f.getInventory().setItem(i,old);f.getInventory().setItem(0,ItemStack.EMPTY);}f.getInventory().selected=0;return true;}return false;}
        for(int i=0;i<36;i++)if(matches(f.getInventory().getItem(i),wanted)){ItemStack old=f.getInventory().getItem(0);f.getInventory().setItem(0,f.getInventory().getItem(i));f.getInventory().setItem(i,old);f.getInventory().selected=0;return true;}return false;
    }
    static int transfer(FakePlayer f,IItemHandler h,ItemStack wanted,int n,boolean take){return transfer(f,h,wanted,n,take,-1);}
    static int transfer(FakePlayer f,IItemHandler h,ItemStack wanted,int n,boolean take,int exactSlot){
        int initial=n;
        if(h==null||wanted.isEmpty()||n<=0||exactSlot>=h.getSlots())return 0;
        int first=take?0:Math.max(0,exactSlot),last=h.getSlots();
        Inventory inv=f.getInventory();
        if(take){
            int available=availableToTake(h,wanted,n),space=0;
            for(int i=0;i<36;i++){ItemStack s=inv.getItem(i);if(s.isEmpty())space+=wanted.getMaxStackSize();else if(ItemStack.isSameItemSameComponents(s,wanted))space+=Math.max(0,s.getMaxStackSize()-s.getCount());}
            if(available<n||space<n)return 0;
            for(int i=first;i<last&&n>0;i++){if(!matches(h.getStackInSlot(i),wanted))continue;ItemStack s=h.extractItem(i,n,false);int got=s.getCount();inv.add(s);if(!s.isEmpty())f.drop(s,false);n-=got;}return initial-n;
        }
        int available=0;for(int i=0;i<36;i++)if(matches(inv.getItem(i),wanted))available+=inv.getItem(i).getCount();if(available<n)return 0;
        // Insert only actual stacks, preserving durability and components. Partial transfers track remaining count.
        for(int i=0;i<36&&n>0;i++){ItemStack s=inv.getItem(i);if(!matches(s,wanted))continue;int want=Math.min(n,s.getCount());ItemStack pending=s.copyWithCount(want);pending=insertPreferred(h,pending,exactSlot);int moved=want-pending.getCount();s.shrink(moved);n-=moved;}
        return initial-n;
    }
    static int availableToTake(IItemHandler h,ItemStack wanted,int limit){
        int available=0;
        if(h==null||wanted.isEmpty()||limit<=0)return 0;
        for(int i=0;i<h.getSlots()&&available<limit;i++){
            if(!matches(h.getStackInSlot(i),wanted))continue;
            ItemStack simulated=h.extractItem(i,limit-available,true);
            if(matches(simulated,wanted))available+=simulated.getCount();
        }return available;
    }
    static ItemStack insertPreferred(IItemHandler h,ItemStack stack,int preferred){
        if(h==null||stack.isEmpty())return stack;
        int count=h.getSlots();if(count==0)return stack;
        int start=preferred>=0&&preferred<count?preferred:0;
        for(int j=0;j<count&&!stack.isEmpty();j++)stack=h.insertItem((start+j)%count,stack,false);
        return stack;
    }
    static int insertFromInventory(FakePlayer f,IItemHandler h,ItemStack wanted,int limit,int preferred,boolean onlyPreferred){
        int left=limit;
        for(int i=0;i<36&&left>0;i++){
            ItemStack stack=f.getInventory().getItem(i);if(!matches(stack,wanted))continue;
            int n=Math.min(left,stack.getCount());ItemStack part=stack.copyWithCount(n);
            ItemStack remainder=onlyPreferred?(preferred>=0&&preferred<h.getSlots()?h.insertItem(preferred,part,false):part):insertPreferred(h,part,preferred);
            int moved=n-remainder.getCount();stack.shrink(moved);left-=moved;
        }return limit-left;
    }
    static int depositOrganized(FakePlayer f,IItemHandler h,ListTag actions,int index,ServerLevel l){
        List<CompoundTag> rules=new ArrayList<>();long pos=actions.getCompound(index).getLong("pos");
        for(int j=index;j<actions.size();j++){CompoundTag rule=actions.getCompound(j);
            if(j>index&&(!rule.getBoolean("bulkSkip")||rule.getLong("pos")!=pos))break;
            rules.add(rule);
        }
        // Reserve the demonstrated layout first. Never let overflow consume
        // another known item's preferred slot before that item has had a chance.
        for(CompoundTag rule:rules){ItemStack wanted=template(rule,l);int preferred=rule.contains("slot")?rule.getInt("slot"):-1;
            if(preferred>=0&&preferred<h.getSlots())insertFromInventory(f,h,wanted,rule.getInt("count"),preferred,true);
        }
        // Extra harvested items of a known type start at that type's last
        // demonstrated destination and spill forward, then wrap.
        Set<String> seen=new HashSet<>();
        for(int j=rules.size()-1;j>=0;j--){CompoundTag rule=rules.get(j);ItemStack wanted=template(rule,l);if(wanted.isEmpty()||!seen.add(itemKey(wanted)))continue;
            insertFromInventory(f,h,wanted,carried(f,wanted),rule.contains("slot")?rule.getInt("slot"):-1,false);
        }
        return depositAll(f,h);
    }
    static int carried(FakePlayer f,ItemStack wanted){
        int count=0;for(int i=0;i<36;i++){ItemStack st=f.getInventory().getItem(i);if(matches(st,wanted))count+=st.getCount();}return count;
    }
    static int depositAll(FakePlayer f,IItemHandler h){
        int left=0;for(int i=0;i<36;i++){ItemStack st=f.getInventory().getItem(i);if(st.isEmpty())continue;
            ItemStack remainder=st.copy();if(h!=null)for(int j=0;j<h.getSlots()&&!remainder.isEmpty();j++)remainder=h.insertItem(j,remainder,false);
            st.shrink(st.getCount()-remainder.getCount());left+=st.getCount();
        }return left;
    }
    static void sweepItems(FakePlayer f,ServerLevel l,Vec3 here){
        f.setPos(here.x,here.y,here.z);
        for(ItemEntity e:l.getEntitiesOfClass(ItemEntity.class,f.getBoundingBox().inflate(.65,.35,.65),it->!it.hasPickUpDelay()&&!watch(it.getItem()))){
            ItemStack stack=e.getItem(),part=stack.copy();int before=part.getCount();f.getInventory().add(part);int moved=before-part.getCount();
            if(moved>0){stack.shrink(moved);if(stack.isEmpty())e.discard();else e.setItem(stack.copy());}
        }
    }
    static boolean clearSegment(ServerLevel l,FakePlayer f,Vec3 from,Vec3 to){
        Vec3 delta=to.subtract(from);int steps=Math.max(1,(int)Math.ceil(delta.length()/.08));
        // Tiny shrink only removes floating-point contacts with the supporting face.
        AABB box=new AABB(from.x-.299,from.y+.002,from.z-.299,from.x+.299,from.y+(f.isShiftKeyDown()?1.49:1.798),from.z+.299);
        for(int i=1;i<=steps;i++){
            AABB moved=box.move(delta.scale((double)i/steps));
            if(l.getBlockCollisions(f,moved).iterator().hasNext())return false;
        }return true;
    }
    static boolean routeClear(ServerLevel l,FakePlayer f,Vec3 here,Vec3 next){
        if(clearSegment(l,f,here,next))return true;
        // A recorded jump can cut the corner of a ledge between samples.
        // Validate both legs of a small axis-ordered path; never teleport through a solid block.
        Vec3 vertical=new Vec3(here.x,next.y,here.z),horizontal=new Vec3(next.x,here.y,next.z);
        if(Math.abs(next.y-here.y)>.001){
            if(next.y>here.y&&clearSegment(l,f,here,vertical)&&clearSegment(l,f,vertical,next))return true;
            if(next.y<here.y&&clearSegment(l,f,here,horizontal)&&clearSegment(l,f,horizontal,next))return true;
        }
        Vec3 xFirst=new Vec3(next.x,here.y,here.z),zFirst=new Vec3(here.x,here.y,next.z);
        return (clearSegment(l,f,here,xFirst)&&clearSegment(l,f,xFirst,next))||(clearSegment(l,f,here,zFirst)&&clearSegment(l,f,zFirst,next));
    }
        static Vec3 ledgeStep(ServerLevel l,FakePlayer f,Vec3 here,Vec3 goal,double travel){
        double dy=goal.y-here.y;
        if(Math.abs(dy)<.001||Math.abs(dy)>1.25||here.distanceTo(goal)>1.65)return null;
        // If a straight interpolation cuts the lip, first clear the lip vertically
        // when ascending, or move horizontally off it before descending.
        Vec3 corner=dy>0?new Vec3(here.x,goal.y,here.z):new Vec3(goal.x,here.y,goal.z);
        if(!clearSegment(l,f,here,corner)||!clearSegment(l,f,corner,goal))return null;
        Vec3 leg=corner.subtract(here);if(leg.length()<1e-6)leg=goal.subtract(here);
        double length=leg.length();if(length<1e-6)return goal;
        return here.add(leg.scale(Math.min(1,travel/length)));
    }
    static boolean saplingNear(ServerLevel l,BlockPos pos){
        for(int dy=0;dy<=3;dy++)if(l.getBlockState(pos.below(dy)).is(BlockTags.SAPLINGS))return true;return false;
    }
    static boolean firstLog(ListTag actions,int index,ServerLevel l){
        for(int i=0;i<index;i++){CompoundTag prev=actions.getCompound(i);if(!"break".equals(prev.getString("type")))continue;
            var id=ResourceLocation.tryParse(prev.getString("block"));if(id!=null&&net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id).defaultBlockState().is(BlockTags.LOGS))return false;
        }return true;
    }
    static void collect(FakePlayer f,ServerLevel l,BlockPos pos){for(ItemEntity e:l.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.2))){ItemStack s=e.getItem();if(f.getInventory().add(s)&&s.isEmpty())e.discard();else e.setItem(s);}}
    static void describe(ServerPlayer p,EchoEntity entity){inspect(p,entity.loopId(),true);}
    static void inspect(ServerPlayer p,UUID key,boolean open){
        CompoundTag state=new CompoundTag();state.putBoolean("available",false);
        WorldData d=world(p.serverLevel());
        for(Tag x:d.echoes){CompoundTag a=(CompoundTag)x;if(!a.getUUID("id").equals(key))continue;
            if(p.position().distanceToSqr(vec(a.getCompound("position")))>64)break;
            CompoundTag tl=a.getCompound("timeline");ListTag actions=timeline(tl);int index=a.getInt("index");
            boolean strict=tl.getBoolean("strict");
            state.putBoolean("available",true);state.putBoolean("strict",strict);state.putString("name",tl.getString("name"));
            state.putInt("cycle",a.getInt("cycles")+1);state.putInt("tint",tint(tl));
            state.putString("reason",a.getString("reason").isEmpty()?"Running":a.getString("reason"));
            state.putInt("step",strict?a.getInt("rawIndex"):Math.min(index+1,actions.size()));state.putInt("total",strict?tl.getInt("rawTicks"):actions.size());
            String task=index<actions.size()?actions.getCompound(index).getString("type"):"Remanifesting";
            if(strict)task=a.getString("strictTask");
            else if(index<actions.size()&&actions.getCompound(index).getBoolean("bulkStart"))task="Organized deposit";
            else if(index<actions.size()&&"transfer".equals(task)){CompoundTag action=actions.getCompound(index);task=(action.getBoolean("take")?"Withdraw ":"Deposit ")+action.getInt("count")+" x "+itemName(template(action,p.serverLevel()));}
            state.putString("task",task.isEmpty()?"Waiting":task);
            state.put("inventory",a.getList("inventory",Tag.TAG_COMPOUND).copy());
            if(a.contains("strictCursor"))state.put("cursor",a.getCompound("strictCursor").copy());
            break;
        }
        PacketDistributor.sendToPlayer(p,new InspectState(key,state,open));
    }
    static String itemName(ItemStack s){return s.isEmpty()?"empty hand":s.getHoverName().getString();}
    static String coords(BlockPos p){return p.getX()+", "+p.getY()+", "+p.getZ();}
    static int groundDrop(FakePlayer f,ItemStack wanted,int count,CompoundTag action){
        int left=count;
        for(int i=0;i<36&&left>0;i++){ItemStack st=f.getInventory().getItem(i);if(!matches(st,wanted))continue;
            int n=Math.min(left,st.getCount());ItemStack removed=st.copyWithCount(n);
            ItemEntity drop=f.drop(removed,false);if(drop==null)continue;
            drop.setDeltaMovement(action.getDouble("vx"),action.getDouble("vy"),action.getDouble("vz"));st.shrink(n);left-=n;
        }
        return count-left;
    }
    static int groundPickup(FakePlayer f,ServerLevel l,ItemStack wanted,int count,CompoundTag action){
        int left=count;
        // Pick only nearby matching physical stacks; preserve their exact durability and properties.
        Vec3 recorded=new Vec3(action.getDouble("ix"),action.getDouble("iy"),action.getDouble("iz"));
        List<ItemEntity> items=l.getEntitiesOfClass(ItemEntity.class,f.getBoundingBox().inflate(1.25),e->!e.hasPickUpDelay()&&matches(e.getItem(),wanted));
        items.sort(Comparator.comparingDouble(e->e.position().distanceToSqr(recorded)));
        for(ItemEntity e:items){if(left<=0)break;ItemStack st=e.getItem();int n=Math.min(left,st.getCount());ItemStack portion=st.copyWithCount(n);f.getInventory().add(portion);int moved=n-portion.getCount();if(moved<=0)continue;st.shrink(moved);left-=moved;if(st.isEmpty())e.discard();else e.setItem(st.copy());}
        return count-left;
    }
    static void particles(ServerLevel l,Vec3 pos,int phase,int tint,float yaw){
        int rgb=TINTS[Math.floorMod(tint,TINTS.length)];
        if(tint==0)rgb=0xB085FF;
        DustParticleOptions dust=new DustParticleOptions(new Vector3f(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f),.85f);
        double angle=Math.toRadians(yaw);double c=Math.cos(angle),sn=Math.sin(angle);
        // Outline six body regions from feet to head; particles converge on the silhouette.
        for(int i=0;i<12;i++){int part=(i+phase/2)%6;double x,y,z=(l.random.nextDouble()-.5)*.22;
            if(part<2){x=(part==0?-.13:.13)+(l.random.nextDouble()-.5)*.16;y=l.random.nextDouble()*.7;}
            else if(part<4){x=(part==2?-.36:.36)+(l.random.nextDouble()-.5)*.14;y=.7+l.random.nextDouble()*.65;}
            else if(part==4){x=(l.random.nextDouble()-.5)*.44;y=.72+l.random.nextDouble()*.6;}
            else{x=(l.random.nextDouble()-.5)*.43;y=1.32+l.random.nextDouble()*.46;}
            l.sendParticles(dust,pos.x+x*c-z*sn,pos.y+y,pos.z+x*sn+z*c,1,.015,.015,.015,0);
        }
    }
    static void levelTick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel l))return;WorldData d=world(l);
        for(int i=d.echoes.size()-1;i>=0;i--){CompoundTag a=d.echoes.getCompound(i);try{if(step(l,a)){d.echoes.remove(i);}d.setDirty();}catch(Exception ex){LogUtils.getLogger().error("Chrono Factory Echo replay failed",ex);a.putBoolean("dismiss",true);d.setDirty();}}
    }
    static boolean step(ServerLevel l,CompoundTag a){
        CompoundTag position=a.getCompound("position");Vec3 here=vec(position);if(!l.hasChunkAt(BlockPos.containing(here)))return false;
        ServerPlayer owner=l.getServer().getPlayerList().getPlayer(a.getUUID("owner"));
        ItemStack watch=owner==null?ItemStack.EMPTY:find(owner,a.getUUID("watch"));
        // Migrate old alpha worlds: remove the previous armor-stand proxy.
        if(a.hasUUID("stand")){Entity old=l.getEntity(a.getUUID("stand"));if(old instanceof ArmorStand)old.discard();a.remove("stand");}
        EchoEntity echo=a.hasUUID("entity")&&l.getEntity(a.getUUID("entity")) instanceof EchoEntity en?en:null;
        FakePlayer f=a.getCompound("timeline").getBoolean("strict")?StrictReplay.player(l,a.getUUID("id")):FakePlayerFactory.get(l,new GameProfile(a.getUUID("id"),"ChronoEcho"));f.getInventory().clearContent();f.getInventory().load(a.getList("inventory",Tag.TAG_COMPOUND));f.setPos(here.x,here.y,here.z);f.setOnGround(true);f.setYRot(position.getFloat("yaw"));f.setXRot(position.getFloat("pitch"));f.setShiftKeyDown(position.getBoolean("sneak"));
        if(a.getBoolean("dismiss")||(!watch.isEmpty()&&!a.getBoolean("creative")&&energy(watch)<=0)){
            if(f instanceof StrictReplay.MacroPlayer macro){StrictReplay.restoreCursor(macro,a);StrictReplay.forget(macro,a);}
            for(int i=0;i<f.getInventory().getContainerSize();i++){ItemStack st=f.getInventory().getItem(i);if(!st.isEmpty())l.addFreshEntity(new ItemEntity(l,here.x,here.y,here.z,st.copy()));}
            f.getInventory().clearContent();if(echo!=null)echo.discard();particles(l,here,0,tint(a.getCompound("timeline")),position.getFloat("yaw"));return true;
        }
        if(owner==null||watch.isEmpty()){a.putString("reason","Owner or Timekeeper unavailable");f.getInventory().clearContent();return false;}
        CompoundTag tl=a.getCompound("timeline");boolean strict=tl.getBoolean("strict");ListTag actions=timeline(tl);if(actions.isEmpty()||(strict&&(tl.getIntArray("raw").length<3||tl.getIntArray("motion").length!=tl.getIntArray("raw").length))){a.putBoolean("dismiss",true);f.getInventory().clearContent();return false;}
        int color=tint(tl);
        // At the end of a timeline, fade out and reappear at the initial position.
        // Logical inventory belongs to the loop and is never regenerated by visual remanifestation.
        if(a.getInt("index")>=actions.size()||(strict&&a.getInt("rawIndex")>=tl.getIntArray("raw").length/3)){
            int fade=a.getInt("fade")+1;a.putInt("fade",fade);a.putString("reason","Loop complete - remanifesting");
            if(echo!=null)echo.visual(color,Math.max(0,24-fade*3),false,a.getFloat("walkPhase"),ItemStack.EMPTY,false,position.getFloat("pitch"));
            if(fade%2==0)particles(l,here,fade,color,position.getFloat("yaw"));
            if(fade>=8){
                CompoundTag first=actions.getCompound(0);if(!l.hasChunkAt(BlockPos.containing(vec(first)))){a.putString("reason","Waiting for starting chunk to load");f.getInventory().clearContent();return false;}
                if(f instanceof StrictReplay.MacroPlayer macro){StrictReplay.restoreCursor(macro,a);StrictReplay.finishCycle(macro,a);a.put("inventory",f.getInventory().save(new ListTag()));}
                if(echo!=null)echo.discard();a.remove("entity");a.put("position",first.copy());a.putInt("index",0);a.putInt("rawIndex",0);a.putInt("guiIndex",0);a.putInt("fade",0);a.putInt("phase",0);a.putInt("cycles",a.getInt("cycles")+1);a.putFloat("walkPhase",0);a.remove("segmentSpeed");a.remove("sweepPos");a.remove("sweepProgress");a.remove("heldElapsed");a.remove("useCursor");a.remove("brokenForHold");
            }
            f.getInventory().clearContent();if(!a.getBoolean("creative"))energy(watch,energy(watch)-2);return false;
        }
        if(echo==null){
            echo=new EchoEntity(ECHO.get(),l);echo.setPos(here.x,here.y,here.z);echo.setYRot(position.getFloat("yaw"));echo.setXRot(position.getFloat("pitch"));echo.setIdentity(a.getUUID("owner"),a.getUUID("id"));echo.setCustomName(Component.literal(tl.getString("name")));echo.setCustomNameVisible(false);echo.visual(color,0,false,0,ItemStack.EMPTY,false,position.getFloat("pitch"));
            if(!l.addFreshEntity(echo)){a.putString("reason","Unable to manifest Echo entity");f.getInventory().clearContent();return false;}
            a.putUUID("entity",echo.getUUID());a.putInt("phase",0);
        }
        int phase=a.getInt("phase");
        if(phase<24){
            phase++;a.putInt("phase",phase);a.putString("reason","Manifesting");
            echo.visual(color,phase,false,a.getFloat("walkPhase"),ItemStack.EMPTY,false,position.getFloat("pitch"));
            if(phase%2==0)particles(l,here,phase,color,position.getFloat("yaw"));
            f.getInventory().clearContent();if(!a.getBoolean("creative"))energy(watch,energy(watch)-8);return false;
        }
        if(strict){StrictReplay.MacroPlayer macro=(StrictReplay.MacroPlayer)f;StrictReplay.restoreCursor(macro,a);StrictReplay.step(l,a,tl,macro,echo,watch);return false;}
        int index=a.getInt("index");CompoundTag action=actions.getCompound(index);String type=action.getString("type");Vec3 target=vec(action);boolean done=false,working=false,walking=false,swung=false;
        String reason="Waiting: "+type;
        ItemStack wanted=template(action,l);
        if(!l.hasChunkAt(BlockPos.containing(target))){reason="Waiting for recorded chunk to load";}
        else if("wait".equals(type)){
            int waited=a.getInt("waited")+1;a.putInt("waited",waited);done=waited>=action.getInt("ticks");working=true;reason="Recorded pause ("+waited+"/"+action.getInt("ticks")+" ticks)";
        }else if("move".equals(type)){
            // Spend a full tick of movement across consecutive waypoints, instead of
            // slowing down to a tiny last step at every recorded sample.
            double time=1.0;
            for(int segments=0;segments<12;segments++){
                CompoundTag waypoint=actions.getCompound(index);Vec3 goal=vec(waypoint);
                if(!l.hasChunkAt(BlockPos.containing(goal))){done=false;reason="Waiting for recorded chunk to load";break;}
                Vec3 diff=goal.subtract(here);double distance=diff.length();
                if(!a.contains("segmentSpeed")||a.getInt("segmentIndex")!=index){
                    double speed=.23;
                    if(waypoint.contains("moveTicks")&&index>0){
                        double full=goal.distanceTo(vec(actions.getCompound(index-1)));
                        if(full>.025)speed=Math.max(.08,Math.min(.36,full/Math.max(1,waypoint.getInt("moveTicks"))));
                    }
                    a.putDouble("segmentSpeed",speed);a.putInt("segmentIndex",index);
                }
                double speed=a.getDouble("segmentSpeed");double travel=Math.min(distance,speed*time);
                Vec3 next=distance<1e-6?goal:here.add(diff.scale(travel/distance));
                f.setPos(here.x,here.y,here.z);
                if(!routeClear(l,f,here,next)){
                    Vec3 detour=ledgeStep(l,f,here,goal,travel);
                    if(detour==null||!routeClear(l,f,here,detour)){done=false;reason="Route obstructed near "+coords(BlockPos.containing(next));break;}
                    next=detour;travel=here.distanceTo(next);
                }
                float fraction=distance<1e-6?1f:(float)(travel/distance);
                float yaw=net.minecraft.util.Mth.rotLerp(fraction,position.getFloat("yaw"),waypoint.getFloat("yaw"));
                float pitch=net.minecraft.util.Mth.lerp(fraction,position.getFloat("pitch"),waypoint.getFloat("pitch"));
                position=waypoint.copy();position.putDouble("x",next.x);position.putDouble("y",next.y);position.putDouble("z",next.z);
                position.putFloat("yaw",yaw);position.putFloat("pitch",pitch);a.put("position",position);
                walking|=next.distanceTo(here)>.002;here=next;working=true;reason="Following recorded route";
                if(!strict)sweepItems(f,l,here);
                done=here.distanceToSqr(goal)<1e-10;
                time-=travel/speed;
                if(!done||time<1e-5||index+1>=actions.size()||!"move".equals(actions.getCompound(index+1).getString("type")))break;
                index++;a.putInt("index",index);a.remove("segmentSpeed");done=false;
            }
        }else if("air_swing".equals(type)){
            if(!equip(f,wanted))reason="Missing required item: "+itemName(wanted);
            else{
                f.setYRot(action.getFloat("yaw"));f.setXRot(action.getFloat("pitch"));f.setShiftKeyDown(action.getBoolean("sneak"));
                BlockHitResult hit=aimedBlock(f);boolean miss=hit.getType()==HitResult.Type.MISS;
                if(strict&&!miss)reason="Expected empty space, found block at "+coords(hit.getBlockPos());
                else{
                    swung=true;working=true;int waited=a.getInt("waited")+1;a.putInt("waited",waited);
                    if(!miss){
                        BlockPos pos=hit.getBlockPos();BlockState state=l.getBlockState(pos);
                        if(!state.is(BlockTags.SAPLINGS)){
                            if(!a.contains("sweepPos")||a.getLong("sweepPos")!=pos.asLong()){a.putLong("sweepPos",pos.asLong());a.putFloat("sweepProgress",0);}
                            float progress=a.getFloat("sweepProgress")+state.getDestroyProgress(f,l,pos);
                            a.putFloat("sweepProgress",progress);
                            if(progress>=1){if(f.gameMode.destroyBlock(pos)){a.remove("sweepPos");a.remove("sweepProgress");}}
                        }
                    }else{a.remove("sweepPos");a.remove("sweepProgress");}
                    done=waited>=Math.max(1,action.getInt("ticks"));reason="Recorded swing";
                }
            }
        }else if("drop".equals(type)||"pickup".equals(type)){
            int count=action.getInt("count"),remaining=count-a.getInt("transferred");
            if(!strict&&"pickup".equals(type)){sweepItems(f,l,here);done=true;working=true;reason="Collected available nearby drops";}
            else{
            int moved="drop".equals(type)?groundDrop(f,wanted,remaining,action):groundPickup(f,l,wanted,remaining,action);
            a.putInt("transferred",a.getInt("transferred")+moved);done=a.getInt("transferred")>=count;working=moved>0;
            reason="drop".equals(type)?"Missing "+(count-a.getInt("transferred"))+" x "+itemName(wanted)+" to drop":"Waiting for "+(count-a.getInt("transferred"))+" x "+itemName(wanted)+" on the ground, or free inventory space";
            swung=moved>0;
            }
        }else{
            BlockPos pos=BlockPos.of(action.getLong("pos"));
            if(!l.hasChunkAt(pos))reason="Target chunk is not loaded";
            else if(here.distanceToSqr(Vec3.atCenterOf(pos))>36)reason="Target out of reach at "+coords(pos);
            else if("transfer".equals(type)){
                IItemHandler h=handler(l,pos);
                if(h==null)reason="No accessible inventory at "+coords(pos);
                else if(!strict&&action.getBoolean("bulkSkip")){done=true;reason="Already deposited inventory";}
                else if(!strict&&action.getBoolean("bulkStart")){
                    int before=0;for(int slot=0;slot<36;slot++)before+=f.getInventory().getItem(slot).getCount();
                    int left=depositOrganized(f,h,actions,index,l);done=left==0;working=left<before;reason="Destination full or rejects "+left+" carried items at "+coords(pos);
                }else{
                    boolean take=action.getBoolean("take");int expected=action.getInt("count");
                    int slot=!take&&action.contains("slot")?action.getInt("slot"):-1;
                    if(slot>=h.getSlots())slot=-1;
                    int n=expected-a.getInt("transferred");
                    if(!strict&&!take)n=Math.min(n,carried(f,wanted));
                    int moved=transfer(f,h,wanted,n,take,slot);a.putInt("transferred",a.getInt("transferred")+moved);
                    done=a.getInt("transferred")>=expected||(!strict&&!take&&carried(f,wanted)==0);working=moved>0;
                    String where=coords(pos)+(slot>=0?" preferred slot "+(slot+1):"");
                    int remaining=Math.max(0,expected-a.getInt("transferred"));
                    reason=take?"Withdraw "+expected+" x "+itemName(wanted)+"; "+remaining+" remaining, "+availableToTake(h,wanted,remaining)+" available (up to required) at "+where:"Waiting to deposit "+itemName(wanted)+" at "+where+" (missing items or destination full)";
                }
            }else if("use".equals(type)){
                int elapsed=a.getInt("heldElapsed"),cursor=a.getInt("useCursor");
                int duration=Math.max(1,action.getInt("duration"));
                // Create cranks need recurring use calls even if an input event was lost.
                // Use the measured duration, never invent a longer hold.
                boolean crank=action.getString("block").equals("create:hand_crank")||action.getString("block").equals("create:copper_valve_handle");
                int[] offsets=action.getIntArray("useOffsets");
                if(crank&&duration>4){offsets=new int[(duration+3)/4];for(int c=0;c<offsets.length;c++)offsets[c]=c*4;}if(offsets.length==0)offsets=new int[]{0};
                duration=Math.max(duration,offsets[offsets.length-1]+1);
                boolean advance=true;
                if(cursor<offsets.length&&elapsed>=offsets[cursor]){
                    String actual=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(pos).getBlock()).toString();
                    if(!actual.equals(action.getString("block"))){advance=false;reason="Expected "+action.getString("block")+" at "+coords(pos)+", found "+actual;}
                    else if(!equip(f,wanted)){advance=false;reason="Missing required item: "+itemName(wanted);}
                    else{
                        f.setYRot(action.getFloat("yaw"));f.setXRot(action.getFloat("pitch"));f.setShiftKeyDown(action.getBoolean("sneak"));
                        InteractionResult result=f.gameMode.useItemOn(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(action.getDouble("hx"),action.getDouble("hy"),action.getDouble("hz")),Direction.from3DDataValue(action.getInt("face")),pos,false));
                        if(f.containerMenu!=f.inventoryMenu)f.closeContainer();
                        if(result.consumesAction()){a.putInt("useCursor",++cursor);swung=true;}
                        else{advance=false;reason="Interaction rejected at "+coords(pos)+" using "+itemName(wanted);}
                    }
                }
                if(advance){
                    elapsed++;a.putInt("heldElapsed",elapsed);working=true;
                    done=elapsed>=duration&&cursor>=offsets.length;
                    reason="Holding use: "+String.format(java.util.Locale.ROOT,"%.1f / %.1f s",elapsed/20.0,duration/20.0)+" ("+cursor+"/"+offsets.length+" interactions)";
                }
            }else if("break".equals(type)&&a.getBoolean("brokenForHold")){
                int elapsed=a.getInt("heldElapsed")+1;a.putInt("heldElapsed",elapsed);working=true;swung=true;
                done=elapsed>=Math.max(1,action.getInt("duration"));reason="Finishing recorded mining hold";
            }else if(!strict&&"break".equals(type)&&l.getBlockState(pos).isAir()){
                if(firstLog(actions,index,l)&&saplingNear(l,pos))reason="Waiting for sapling to grow at "+coords(pos);
                else{int elapsed=a.getInt("heldElapsed")+1;a.putInt("heldElapsed",elapsed);done=elapsed>=Math.max(1,action.getInt("duration"));working=true;swung=true;reason="Swing at empty space";}
            }else if(!strict&&"break".equals(type)&&l.getBlockState(pos).is(BlockTags.SAPLINGS)){
                if(firstLog(actions,index,l))reason="Waiting for sapling to grow at "+coords(pos);
                else{done=true;reason="Preserving sapling";}
            }else if(!equip(f,wanted))reason="Missing required item: "+itemName(wanted);
            else{
                f.setYRot(action.getFloat("yaw"));f.setXRot(action.getFloat("pitch"));f.setShiftKeyDown(action.getBoolean("sneak"));
                BlockState state=l.getBlockState(pos);String block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if(!block.equals(action.getString("block"))&&(strict||!"break".equals(type)))reason="Expected "+action.getString("block")+" at "+coords(pos)+", found "+block;
                else if("break".equals(type)){
                    float progress=a.getFloat("progress")+state.getDestroyProgress(f,l,pos);a.putFloat("progress",progress);working=progress>0;swung=working;
                    reason="Mining "+block+" at "+coords(pos)+" ("+Math.min(100,(int)(progress*100))+"%)";
                    if(working)a.putInt("heldElapsed",a.getInt("heldElapsed")+1);
                    if(progress>=1){boolean broken=f.gameMode.destroyBlock(pos);if(broken){
                        if(!tl.getBoolean("explicitPickup"))collect(f,l,pos);
                        a.putBoolean("brokenForHold",true);done=a.getInt("heldElapsed")>=Math.max(1,action.getInt("duration"));
                    }else{working=false;reason="Block break denied at "+coords(pos);}}
                }else reason="Unsupported action: "+type;
            }
        }
        if(!strict&&"wait".equals(type))sweepItems(f,l,here);
        if(!"air_swing".equals(type)&&!"move".equals(type)){a.remove("sweepPos");a.remove("sweepProgress");}
        if(done){a.putInt("index",index+1);a.putFloat("progress",0);a.putInt("transferred",0);a.putInt("waited",0);a.remove("heldElapsed");a.remove("useCursor");a.remove("brokenForHold");a.remove("segmentSpeed");reason="Completed "+type;}
        a.putString("reason",reason);
        double moved=echo.position().distanceTo(here);
        echo.setPos(here.x,here.y,here.z);echo.setYRot(position.getFloat("yaw"));echo.setXRot(position.getFloat("pitch"));echo.setShiftKeyDown(position.getBoolean("sneak"));
        if(walking)a.putFloat("walkPhase",a.getFloat("walkPhase")+(float)moved*5);
        echo.visual(color,24,walking,a.getFloat("walkPhase"),f.getMainHandItem(),swung,position.getFloat("pitch"));
        echo.setCustomName(Component.literal(tl.getString("name")));
        a.put("inventory",f.getInventory().save(new ListTag()));f.getInventory().clearContent();
        if(!a.getBoolean("creative"))energy(watch,energy(watch)-(working||done?8:2));
        return false;
    }
    static void changeTint(ServerPlayer p,UUID key,CompoundTag tl,int value){
        tl.putInt("tint",value);UUID tid=tl.getUUID("id");
        for(ServerLevel l:p.server.getAllLevels()){WorldData d=world(l);for(Tag x:d.echoes){CompoundTag a=(CompoundTag)x;if(a.getUUID("owner").equals(p.getUUID())&&a.getUUID("watch").equals(key)&&a.getCompound("timeline").getUUID("id").equals(tid)){a.getCompound("timeline").putInt("tint",value);d.setDirty();}}}
    }
    static void renameTimeline(ServerPlayer p,RenameTimeline msg){
        ItemStack s=find(p,msg.watch());if(s.isEmpty()||RECORDINGS.containsKey(p.getUUID()))return;
        String name=msg.name().strip();if(name.isEmpty()||name.length()>48||name.chars().anyMatch(Character::isISOControl)){sendUi(p,s);return;}
        CompoundTag t=data(s);boolean changed=false;
        if(t.contains("current")){CompoundTag tl=t.getCompound("current");if(tl.getUUID("id").equals(msg.timeline())){tl.putString("name",name);t.put("current",tl);changed=true;}}
        if(creative(s)){
            ListTag saved=t.getList("saved",Tag.TAG_COMPOUND);
            for(Tag x:saved){CompoundTag tl=(CompoundTag)x;if(tl.getUUID("id").equals(msg.timeline())){tl.putString("name",name);changed=true;break;}}
            if(changed)t.put("saved",saved);
        }
        if(changed){
            put(s,t);p.getInventory().setChanged();
            for(ServerLevel l:p.server.getAllLevels()){
                WorldData d=world(l);for(Tag x:d.echoes){CompoundTag a=(CompoundTag)x;
                    if(a.getUUID("owner").equals(p.getUUID())&&a.getUUID("watch").equals(msg.watch())&&a.getCompound("timeline").getUUID("id").equals(msg.timeline())){
                        a.getCompound("timeline").putString("name",name);
                        if(a.hasUUID("entity")&&l.getEntity(a.getUUID("entity")) instanceof EchoEntity e)e.setCustomName(Component.literal(name));
                        d.setDirty();
                    }
                }
            }
        }
        sendUi(p,s);
    }
    static void sendUi(ServerPlayer p,ItemStack s){
        if(!watch(s))return;
        UUID key=id(s);CompoundTag t=data(s), summary=new CompoundTag();
        Recording rec=RECORDINGS.get(p.getUUID());boolean recording=rec!=null&&rec.watch.equals(key);
        summary.putBoolean("creative",creative(s));summary.putBoolean("retain",t.getBoolean("retain"));summary.putInt("energy",energy(s));
        summary.putBoolean("recording",recording);summary.putBoolean("otherRecording",rec!=null&&!recording);
        summary.putInt("steps",recording?rec.actions.size():t.contains("current")?timeline(t.getCompound("current")).size():0);
        summary.putBoolean("current",t.contains("current"));if(t.contains("current")){CompoundTag cur=t.getCompound("current");summary.putUUID("currentId",cur.getUUID("id"));summary.putString("currentName",cur.getString("name"));}summary.putBoolean("strict",t.contains("current")?t.getCompound("current").getBoolean("strict"):t.getBoolean("strict"));summary.putInt("tint",t.contains("current")?tint(t.getCompound("current")):tint(t));
        summary.putBoolean("currentActive",t.contains("current")&&active(p,key,t.getCompound("current").getUUID("id")));
        ListTag entries=new ListTag(), saved=t.getList("saved",Tag.TAG_COMPOUND);
        int count=0;
        for(Tag x:saved){CompoundTag tl=(CompoundTag)x,entry=new CompoundTag();boolean running=active(p,key,tl.getUUID("id"));
            entry.putString("name",tl.getString("name"));entry.putUUID("id",tl.getUUID("id"));entry.putBoolean("strict",tl.getBoolean("strict"));entry.putInt("tint",tint(tl));entry.putInt("steps",timeline(tl).size());entry.putBoolean("active",running);if(running)count++;entries.add(entry);}
        if(summary.getBoolean("currentActive"))count++;
        summary.putInt("activeCount",count);summary.put("saved",entries);
        PacketDistributor.sendToPlayer(p,new UiState(key,summary));
    }
    static void uiCommand(ServerPlayer p,UiCommand cmd){
        if("inspect".equals(cmd.action())){inspect(p,cmd.key(),false);return;}
        if("open".equals(cmd.action())){ItemStack held=p.getMainHandItem();if(watch(held))sendUi(p,held);return;}
        ItemStack s=find(p,cmd.key());if(s.isEmpty())return;
        CompoundTag t=data(s);Recording r=RECORDINGS.get(p.getUUID());
        if("cycle".equals(cmd.action())){
            if(p.getMainHandItem()==s){boolean starting=r==null&&!t.contains("current");cycle(p);if(starting&&RECORDINGS.containsKey(p.getUUID()))return;}
        }else if("cancel".equals(cmd.action())){
            if(r!=null&&r.watch.equals(cmd.key()))finish(p,false);
        }else if(r==null){
            ListTag saved=t.getList("saved",Tag.TAG_COMPOUND);int i=cmd.index();
            switch(cmd.action()){
                case "mode" -> {
                    if(i==-1){
                        if(t.contains("current")){
                            CompoundTag tl=t.getCompound("current");if(!active(p,cmd.key(),tl.getUUID("id"))){tl.putBoolean("strict",!tl.getBoolean("strict"));t.put("current",tl);put(s,t);}
                        }else{t.putBoolean("strict",!t.getBoolean("strict"));put(s,t);}
                    }else if(creative(s)&&i>=0&&i<saved.size()){
                        CompoundTag tl=saved.getCompound(i);if(!active(p,cmd.key(),tl.getUUID("id"))){tl.putBoolean("strict",!tl.getBoolean("strict"));t.put("saved",saved);put(s,t);}
                    }
                }
                case "set_tint" -> {
                    int selected=Math.floorMod(i,TINTS.length), targetIndex=Math.floorDiv(i,TINTS.length)-1;
                    if(i>=0&&targetIndex==-1){
                        if(t.contains("current")){CompoundTag tl=t.getCompound("current");changeTint(p,cmd.key(),tl,selected);t.put("current",tl);}
                        else t.putInt("tint",selected);put(s,t);
                    }else if(i>=0&&creative(s)&&targetIndex>=0&&targetIndex<saved.size()){
                        CompoundTag tl=saved.getCompound(targetIndex);changeTint(p,cmd.key(),tl,selected);t.put("saved",saved);put(s,t);
                    }
                }
                case "tint" -> {if(i==-1){if(t.contains("current")){CompoundTag tl=t.getCompound("current");changeTint(p,cmd.key(),tl,(tint(tl)+1)%TINTS.length);t.put("current",tl);}else t.putInt("tint",(tint(t)+1)%TINTS.length);put(s,t);}else if(creative(s)&&i>=0&&i<saved.size()){CompoundTag tl=saved.getCompound(i);changeTint(p,cmd.key(),tl,(tint(tl)+1)%TINTS.length);t.put("saved",saved);put(s,t);}}
                case "retain" -> {t.putBoolean("retain",!t.getBoolean("retain"));put(s,t);}
                case "manifest" -> {if(t.contains("current"))manifest(p,s,t.getCompound("current"));}
                case "dismiss_current" -> {if(t.contains("current")){dismiss(p,cmd.key(),t.getCompound("current").getUUID("id"));if(!t.getBoolean("retain")){t.remove("current");put(s,t);}}}
                case "clear" -> {if(t.contains("current"))dismiss(p,cmd.key(),t.getCompound("current").getUUID("id"));t.remove("current");put(s,t);}
                case "save" -> {if(creative(s)&&t.contains("current")&&saved.size()<MAX_TIMELINES){saved.add(t.getCompound("current").copy());t.put("saved",saved);t.remove("current");put(s,t);}}
                case "toggle" -> {if(creative(s)&&i>=0&&i<saved.size()){CompoundTag tl=saved.getCompound(i);if(active(p,cmd.key(),tl.getUUID("id")))dismiss(p,cmd.key(),tl.getUUID("id"));else manifest(p,s,tl);}}
                case "delete" -> {if(creative(s)&&i>=0&&i<saved.size()){dismiss(p,cmd.key(),saved.getCompound(i).getUUID("id"));saved.remove(i);t.put("saved",saved);put(s,t);}}
                case "dismiss_all" -> dismiss(p,cmd.key(),null);
                case "manifest_all" -> {if(creative(s)){for(Tag x:saved)manifest(p,s,(CompoundTag)x);}}
                default -> {}
            }
        }
        ItemStack found=find(p,cmd.key());if(!found.isEmpty())sendUi(p,found);
    }
}
