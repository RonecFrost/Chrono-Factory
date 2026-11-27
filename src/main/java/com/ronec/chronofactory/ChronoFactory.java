package com.ronec.chronofactory;

import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
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
    static final Map<UUID,Recording> RECORDINGS=new HashMap<>();
    public ChronoFactory(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(ChronoFactory::registerNetwork);
        bus.addListener(ChronoFactory::capabilities);
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){e.accept(PERSONAL.get());e.accept(CREATIVE.get());}});
        IEventBus game=NeoForge.EVENT_BUS;
        game.addListener(ChronoFactory::playerTick);
        game.addListener(ChronoFactory::levelTick);
        game.addListener(ChronoFactory::onUse);
        game.addListener(ChronoFactory::onBreak);
        game.addListener(ChronoFactory::logout);
        game.addListener(ChronoFactory::login);
        game.addListener(ChronoFactory::death);
        game.addListener(ChronoFactory::toss);
    }
    public record Click() implements CustomPacketPayload {
        public static final Click INSTANCE=new Click();
        public static final Type<Click> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"click"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Click> CODEC=StreamCodec.unit(INSTANCE);
        public Type<Click> type(){return TYPE;}
    }
    public record UiCommand(UUID key,String action,int index) implements CustomPacketPayload {
        public static final Type<UiCommand> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"ui_command"));
        public static final StreamCodec<RegistryFriendlyByteBuf,UiCommand> CODEC=new StreamCodec<>() {
            public UiCommand decode(RegistryFriendlyByteBuf b){return new UiCommand(b.readUUID(),b.readUtf(32),b.readVarInt());}
            public void encode(RegistryFriendlyByteBuf b,UiCommand p){b.writeUUID(p.key());b.writeUtf(p.action(),32);b.writeVarInt(p.index());}
        };
        public Type<UiCommand> type(){return TYPE;}
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
        var registrar=e.registrar("2");
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
        boolean menuSeen;
        int tick;
        Recording(ServerPlayer p,UUID w){watch=w;dimension=p.level().dimension().location().toString();last=point(p,"move");actions.add(last);}
        void add(CompoundTag a){if(actions.size()<MAX_ACTIONS)actions.add(a);}
        void move(ServerPlayer p){CompoundTag a=point(p,"move");if(Math.abs(a.getDouble("x")-last.getDouble("x"))+Math.abs(a.getDouble("y")-last.getDouble("y"))+Math.abs(a.getDouble("z")-last.getDouble("z"))>.025||Math.abs(a.getFloat("yaw")-last.getFloat("yaw"))>8||Math.abs(a.getFloat("pitch")-last.getFloat("pitch"))>8){add(a);last=a;}}
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
        RECORDINGS.put(p.getUUID(),new Recording(p,key));
        p.inventoryMenu.broadcastChanges();p.inventoryMenu.sendAllDataToRemote();say(p,"RECORDING STARTED - walk or interact, then left-click again to finish. Your original inventory is protected.");
    }
    static void finish(ServerPlayer p,boolean save){
        Recording r=RECORDINGS.remove(p.getUUID());if(r==null){restore(p);return;}
        if(r.menuSeen&&r.container!=null)captureTransfers(p,r);
        if(p.containerMenu!=p.inventoryMenu)p.closeContainer();
        r.move(p);
        ItemStack s=find(p,r.watch);
        if(save&&!s.isEmpty()){
            if(r.actions.size()==1){CompoundTag idle=point(p,"wait");idle.putInt("ticks",Math.max(1,Math.min(1200,r.tick)));r.add(idle);}
            CompoundTag t=data(s),tl=new CompoundTag();tl.putUUID("id",UUID.randomUUID());tl.putString("name","Timeline "+(t.getList("saved",Tag.TAG_COMPOUND).size()+1));tl.putString("dimension",r.dimension);tl.put("actions",r.actions);t.put("current",tl);put(s,t);
        }
        restore(p);say(p,save&&!s.isEmpty()?"RECORDING SAVED ("+r.actions.size()+" steps). Left-click to summon the Echo.":"Recording cancelled; inventory restored.");
    }
    static void restore(ServerPlayer p){
        CompoundTag persistent=p.getPersistentData();if(!persistent.contains(BACKUP))return;
        CompoundTag b=persistent.getCompound(BACKUP);UUID key=b.getUUID("watch");ItemStack timekeeper=find(p,key);
        List<ItemStack> extras=new ArrayList<>();
        for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(!s.isEmpty()&&s!=timekeeper)extras.add(s.copy());}
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
    static void toss(ItemTossEvent e){if(e.getPlayer() instanceof ServerPlayer p&&RECORDINGS.containsKey(p.getUUID())&&watch(e.getEntity().getItem())){e.setCanceled(true);p.getInventory().add(e.getEntity().getItem());}}
    static String itemKey(ItemStack s){ItemStack copy=s.copyWithCount(1);if(copy.isDamageableItem())copy.setDamageValue(0);return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(copy.getItem()).toString()+":"+copy.getComponentsPatch();}
    static Map<String,ItemStack> snapshot(ServerPlayer p){
        Map<String,ItemStack> map=new HashMap<>();
        for(int i=0;i<36;i++){ItemStack s=p.getInventory().getItem(i);if(s.isEmpty()||watch(s))continue;String k=itemKey(s);if(map.containsKey(k))map.get(k).grow(s.getCount());else map.put(k,s.copy());}
        return map;
    }
    static void captureTransfers(ServerPlayer p,Recording r){
        Map<String,ItemStack> now=snapshot(p);Set<String> keys=new TreeSet<>(now.keySet());keys.addAll(r.previous.keySet());
        for(String k:keys){ItemStack before=r.previous.getOrDefault(k,ItemStack.EMPTY),after=now.getOrDefault(k,ItemStack.EMPTY);int delta=after.getCount()-before.getCount();if(delta==0)continue;
            CompoundTag a=point(p,"transfer");a.putLong("pos",r.container.asLong());a.putBoolean("take",delta>0);a.putInt("count",Math.abs(delta));a.put("item",(delta>0?after:before).copyWithCount(1).save(p.registryAccess()));r.add(a);}
        r.previous=now;
    }
    static IItemHandler handler(ServerLevel l,BlockPos pos){return l.getCapability(Capabilities.ItemHandler.BLOCK,pos,null);}
    static void onUse(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer||watch(e.getItemStack()))return;
        Recording r=RECORDINGS.get(p.getUUID());if(r==null||e.getHand()!=InteractionHand.MAIN_HAND)return;
        r.move(p);BlockPos pos=e.getPos();
        if(handler(p.serverLevel(),pos)!=null&&!p.isShiftKeyDown()&&e.getItemStack().isEmpty()){r.container=pos.immutable();r.previous=snapshot(p);r.menuSeen=false;return;}
        CompoundTag a=point(p,"use");a.putLong("pos",pos.asLong());a.putString("block",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(pos).getBlock()).toString());
        BlockHitResult h=e.getHitVec();a.putInt("face",h.getDirection().get3DDataValue());a.putDouble("hx",h.getLocation().x);a.putDouble("hy",h.getLocation().y);a.putDouble("hz",h.getLocation().z);if(!e.getItemStack().isEmpty())a.put("item",e.getItemStack().copyWithCount(1).save(p.registryAccess()));r.add(a);
    }
    static void onBreak(BlockEvent.BreakEvent e){
        if(!(e.getPlayer() instanceof ServerPlayer p)||p instanceof FakePlayer||e.isCanceled())return;
        Recording r=RECORDINGS.get(p.getUUID());if(r==null)return;r.move(p);
        CompoundTag a=point(p,"break");a.putLong("pos",e.getPos().asLong());a.putString("block",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getState().getBlock()).toString());if(!p.getMainHandItem().isEmpty())a.put("item",p.getMainHandItem().copyWithCount(1).save(p.registryAccess()));r.add(a);
    }
    static void playerTick(PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer)return;
        Recording r=RECORDINGS.get(p.getUUID());
        if(r!=null){
            if(!r.dimension.equals(p.level().dimension().location().toString())||find(p,r.watch).isEmpty()){finish(p,false);return;}
            if(r.container!=null){if(p.containerMenu!=p.inventoryMenu){r.menuSeen=true;captureTransfers(p,r);}else if(r.menuSeen){captureTransfers(p,r);r.container=null;r.menuSeen=false;}}
            if(++r.tick%3==0&&p.containerMenu==p.inventoryMenu)r.move(p);
            if(r.tick==2){p.inventoryMenu.sendAllDataToRemote();}
            if(r.actions.size()>=MAX_ACTIONS)finish(p,true);
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
        ServerLevel l=null;for(ServerLevel candidate:p.server.getAllLevels())if(candidate.dimension().location().toString().equals(tl.getString("dimension")))l=candidate;
        if(l==null||!l.hasChunkAt(BlockPos.containing(vec(actions.getCompound(0))))){say(p,"Recording start is not loaded.");return;}
        UUID key=id(s);if(active(p,key,tl.getUUID("id"))||(!creative(s)&&active(p,key,null)))return;
        if(!creative(s)&&energy(s)<=0){say(p,"Timekeeper needs energy.");return;}
        CompoundTag a=new CompoundTag();a.putUUID("id",UUID.randomUUID());a.putUUID("owner",p.getUUID());a.putUUID("watch",key);a.putString("name",p.getGameProfile().getName());a.put("timeline",tl.copy());a.putBoolean("creative",creative(s));a.put("position",actions.getCompound(0).copy());a.put("inventory",new ListTag());
        WorldData d=world(l);d.echoes.add(a);d.setDirty();say(p,"Echo manifested.");
    }
    static Vec3 vec(CompoundTag a){return new Vec3(a.getDouble("x"),a.getDouble("y"),a.getDouble("z"));}
    static ItemStack template(CompoundTag a,ServerLevel l){return a.contains("item")?ItemStack.parseOptional(l.registryAccess(),a.getCompound("item")):ItemStack.EMPTY;}
    static boolean matches(ItemStack a,ItemStack b){if(a.isEmpty()||b.isEmpty()||a.getItem()!=b.getItem())return false;ItemStack x=a.copyWithCount(1),y=b.copyWithCount(1);if(x.isDamageableItem())x.setDamageValue(0);if(y.isDamageableItem())y.setDamageValue(0);return ItemStack.isSameItemSameComponents(x,y);}
    static boolean equip(FakePlayer f,ItemStack wanted){
        if(wanted.isEmpty()){for(int i=0;i<36;i++)if(f.getInventory().getItem(i).isEmpty()){if(i!=0){ItemStack old=f.getInventory().getItem(0);f.getInventory().setItem(i,old);f.getInventory().setItem(0,ItemStack.EMPTY);}f.getInventory().selected=0;return true;}return false;}
        for(int i=0;i<36;i++)if(matches(f.getInventory().getItem(i),wanted)){ItemStack old=f.getInventory().getItem(0);f.getInventory().setItem(0,f.getInventory().getItem(i));f.getInventory().setItem(i,old);f.getInventory().selected=0;return true;}return false;
    }
    static int transfer(FakePlayer f,IItemHandler h,ItemStack wanted,int n,boolean take){
        int initial=n;
        if(h==null||wanted.isEmpty())return 0;
        Inventory inv=f.getInventory();
        if(take){
            int available=0,space=0;
            for(int i=0;i<h.getSlots();i++){ItemStack s=h.getStackInSlot(i);if(matches(s,wanted))available+=h.extractItem(i,n,true).getCount();}
            for(int i=0;i<36;i++){ItemStack s=inv.getItem(i);if(s.isEmpty())space+=wanted.getMaxStackSize();else if(ItemStack.isSameItemSameComponents(s,wanted))space+=Math.max(0,s.getMaxStackSize()-s.getCount());}
            if(available<n||space<n)return 0;
            for(int i=0;i<h.getSlots()&&n>0;i++){if(!matches(h.getStackInSlot(i),wanted))continue;ItemStack s=h.extractItem(i,n,false);int got=s.getCount();inv.add(s);if(!s.isEmpty())f.drop(s,false);n-=got;}return initial-n;
        }
        int available=0;for(int i=0;i<36;i++)if(matches(inv.getItem(i),wanted))available+=inv.getItem(i).getCount();if(available<n)return 0;
        // Insert only actual stacks, preserving durability and components. Partial transfers track remaining count.
        for(int i=0;i<36&&n>0;i++){ItemStack s=inv.getItem(i);if(!matches(s,wanted))continue;int want=Math.min(n,s.getCount());ItemStack pending=s.copyWithCount(want);for(int j=0;j<h.getSlots()&&!pending.isEmpty();j++)pending=h.insertItem(j,pending,false);int moved=want-pending.getCount();s.shrink(moved);n-=moved;}
        return initial-n;
    }
    static void collect(FakePlayer f,ServerLevel l,BlockPos pos){for(ItemEntity e:l.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.2))){ItemStack s=e.getItem();if(f.getInventory().add(s)&&s.isEmpty())e.discard();else e.setItem(s);}}
    static void levelTick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel l))return;WorldData d=world(l);
        for(int i=d.echoes.size()-1;i>=0;i--){CompoundTag a=d.echoes.getCompound(i);try{if(step(l,a)){d.echoes.remove(i);}d.setDirty();}catch(Exception ex){LogUtils.getLogger().error("Chrono Factory Echo replay failed",ex);a.putBoolean("dismiss",true);d.setDirty();}}
    }
    static boolean step(ServerLevel l,CompoundTag a){
        CompoundTag position=a.getCompound("position");Vec3 here=vec(position);if(!l.hasChunkAt(BlockPos.containing(here)))return false;
        ServerPlayer owner=l.getServer().getPlayerList().getPlayer(a.getUUID("owner"));
        ItemStack watch=owner==null?ItemStack.EMPTY:find(owner,a.getUUID("watch"));
        ArmorStand stand=a.hasUUID("stand")&&l.getEntity(a.getUUID("stand")) instanceof ArmorStand st?st:null;
        FakePlayer f=FakePlayerFactory.get(l,new GameProfile(a.getUUID("id"),"ChronoEcho"));f.getInventory().clearContent();f.getInventory().load(a.getList("inventory",Tag.TAG_COMPOUND));f.setPos(here.x,here.y,here.z);f.setOnGround(true);f.setYRot(position.getFloat("yaw"));f.setXRot(position.getFloat("pitch"));f.setShiftKeyDown(position.getBoolean("sneak"));
        if(a.getBoolean("dismiss")||(!watch.isEmpty()&&!a.getBoolean("creative")&&energy(watch)<=0)){
            for(int i=0;i<f.getInventory().getContainerSize();i++){ItemStack s=f.getInventory().getItem(i);if(!s.isEmpty())l.addFreshEntity(new ItemEntity(l,here.x,here.y,here.z,s.copy()));}
            f.getInventory().clearContent();if(stand!=null)stand.discard();return true;
        }
        if(owner==null||watch.isEmpty()){f.getInventory().clearContent();return false;}
        if(stand==null){stand=new ArmorStand(l,here.x,here.y,here.z);stand.setNoGravity(true);stand.setInvulnerable(true);CompoundTag marker=new CompoundTag();marker.putBoolean("Marker",true);stand.readAdditionalSaveData(marker);stand.setNoGravity(true);stand.setInvulnerable(true);stand.setShowArms(true);stand.setCustomName(Component.literal(a.getString("name")+" [Echo]"));stand.setCustomNameVisible(true);stand.addTag("chronofactory_echo");l.addFreshEntity(stand);a.putUUID("stand",stand.getUUID());}
        ListTag actions=timeline(a.getCompound("timeline"));if(actions.isEmpty())return true;
        int index=a.getInt("index")%actions.size();CompoundTag action=actions.getCompound(index);String type=action.getString("type");Vec3 target=vec(action);boolean done=false;
        if(!l.hasChunkAt(BlockPos.containing(target))){f.getInventory().clearContent();return false;}
        if("wait".equals(type)){
            int waited=a.getInt("waited")+1;a.putInt("waited",waited);done=waited>=action.getInt("ticks");
        }else if("move".equals(type)){
            Vec3 diff=target.subtract(here);double distance=diff.length();Vec3 next=distance<=.3?target:here.add(diff.scale(.3/distance));
            if(!l.getBlockCollisions(f,f.getBoundingBox().move(next.subtract(here))).iterator().hasNext()){position=action.copy();position.putDouble("x",next.x);position.putDouble("y",next.y);position.putDouble("z",next.z);a.put("position",position);here=next;done=distance<=.3;}
        }else{
            BlockPos pos=BlockPos.of(action.getLong("pos"));ItemStack wanted=template(action,l);
            if(l.hasChunkAt(pos)&&here.distanceToSqr(Vec3.atCenterOf(pos))<=36){
                if("transfer".equals(type)){
                    IItemHandler h=handler(l,pos);
                    // Simulate the entire output before committing to avoid repeating partial transfers.
                    int n=action.getInt("count")-a.getInt("transferred");
                    int moved=transfer(f,h,wanted,n,action.getBoolean("take"));
                    a.putInt("transferred",a.getInt("transferred")+moved);
                    done=a.getInt("transferred")>=action.getInt("count");
                }else if(equip(f,wanted)){
                    f.setYRot(action.getFloat("yaw"));f.setXRot(action.getFloat("pitch"));f.setShiftKeyDown(action.getBoolean("sneak"));
                    BlockState state=l.getBlockState(pos);String block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if(block.equals(action.getString("block"))){
                        if("break".equals(type)){
                            float progress=a.getFloat("progress")+state.getDestroyProgress(f,l,pos);a.putFloat("progress",progress);
                            if(progress>=1){done=f.gameMode.destroyBlock(pos);if(done)collect(f,l,pos);}
                        }else if("use".equals(type)){
                            InteractionResult result=f.gameMode.useItemOn(f,l,f.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(new Vec3(action.getDouble("hx"),action.getDouble("hy"),action.getDouble("hz")),Direction.from3DDataValue(action.getInt("face")),pos,false));done=result.consumesAction();
                            if(f.containerMenu!=f.inventoryMenu)f.closeContainer();
                        }
                    }
                }
            }
        }
        if(done){a.putInt("index",(index+1)%actions.size());a.putFloat("progress",0);a.putInt("transferred",0);a.putInt("waited",0);}
        double moved=stand.position().distanceTo(here);
        stand.setPos(here.x,here.y,here.z);stand.setYRot(position.getFloat("yaw"));
        if(moved>.01){
            float phase=a.getFloat("walkPhase")+(float)moved*6; a.putFloat("walkPhase",phase);
            float swing=(float)Math.sin(phase)*38;
            stand.setLeftLegPose(new Rotations(swing,0,0));stand.setRightLegPose(new Rotations(-swing,0,0));
            stand.setLeftArmPose(new Rotations(-swing,0,-5));stand.setRightArmPose(new Rotations(swing,0,5));
        }else if("wait".equals(type)||done){
            stand.setLeftLegPose(new Rotations(0,0,0));stand.setRightLegPose(new Rotations(0,0,0));
            stand.setLeftArmPose(new Rotations(0,0,-5));stand.setRightArmPose(new Rotations(0,0,5));
        }
        // Do not equip the visual proxy with real stacks: it must never drop duplicate tools.
        stand.setCustomName(Component.literal(a.getString("name")+(done||"move".equals(type)?" [Echo]":" [Stasis]")));
        a.put("inventory",f.getInventory().save(new ListTag()));f.getInventory().clearContent();
        if(!a.getBoolean("creative"))energy(watch,energy(watch)-(done||"move".equals(type)?8:2));
        return false;
    }
    static void sendUi(ServerPlayer p,ItemStack s){
        if(!watch(s))return;
        UUID key=id(s);CompoundTag t=data(s), summary=new CompoundTag();
        Recording rec=RECORDINGS.get(p.getUUID());boolean recording=rec!=null&&rec.watch.equals(key);
        summary.putBoolean("creative",creative(s));summary.putBoolean("retain",t.getBoolean("retain"));summary.putInt("energy",energy(s));
        summary.putBoolean("recording",recording);summary.putBoolean("otherRecording",rec!=null&&!recording);
        summary.putInt("steps",recording?rec.actions.size():t.contains("current")?timeline(t.getCompound("current")).size():0);
        summary.putBoolean("current",t.contains("current"));
        summary.putBoolean("currentActive",t.contains("current")&&active(p,key,t.getCompound("current").getUUID("id")));
        ListTag entries=new ListTag(), saved=t.getList("saved",Tag.TAG_COMPOUND);
        int count=0;
        for(Tag x:saved){CompoundTag tl=(CompoundTag)x,entry=new CompoundTag();boolean running=active(p,key,tl.getUUID("id"));
            entry.putString("name",tl.getString("name"));entry.putInt("steps",timeline(tl).size());entry.putBoolean("active",running);if(running)count++;entries.add(entry);}
        if(summary.getBoolean("currentActive"))count++;
        summary.putInt("activeCount",count);summary.put("saved",entries);
        PacketDistributor.sendToPlayer(p,new UiState(key,summary));
    }
    static void uiCommand(ServerPlayer p,UiCommand cmd){
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
