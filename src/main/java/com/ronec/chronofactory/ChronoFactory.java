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
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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
    static void registerNetwork(RegisterPayloadHandlersEvent e){
        e.registrar("1").playToServer(Click.TYPE,Click.CODEC,(msg,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p) cycle(p);}));
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
        @Override public int getBarColor(ItemStack s){return 0xB83055;}
        @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand h){
            ItemStack s=p.getItemInHand(h);
            if(p instanceof ServerPlayer sp){
                if(RECORDINGS.containsKey(sp.getUUID())){say(sp,"Finish recording with left click first.");}
                else {UUID key=id(s); sp.openMenu(new SimpleMenuProvider((n,inv,player)->new WatchMenu(n,inv,sp,key),Component.literal(unlimited?"Creative Timekeeper":"Personal Timekeeper")));}
            }
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
        if(p.getPersistentData().contains(BACKUP)){restore(p);return;}
        CompoundTag t=data(s);
        if(active(p,key,null)){dismiss(p,key,null);if(!creative(s)&&!t.getBoolean("retain")){t.remove("current");put(s,t);}say(p,"Timeline dismissed.");return;}
        if(t.contains("current")){manifest(p,s,t.getCompound("current"));return;}
        if(p.isSpectator()){say(p,"Spectators cannot record.");return;}
        int slot=p.getInventory().selected;
        CompoundTag backup=new CompoundTag();backup.putInt("slot",slot);backup.putUUID("watch",key);backup.putString("mode",p.gameMode.getGameModeForPlayer().getName());
        p.getInventory().setItem(slot,ItemStack.EMPTY);
        backup.put("inventory",p.getInventory().save(new ListTag()));
        p.getPersistentData().put(BACKUP,backup);
        p.getInventory().clearContent();p.getInventory().setItem(slot,s);
        if(p.isCreative())p.setGameMode(GameType.SURVIVAL);
        RECORDINGS.put(p.getUUID(),new Recording(p,key));
        p.inventoryMenu.broadcastChanges();say(p,"Recording with empty temporal inventory.");
    }
    static void finish(ServerPlayer p,boolean save){
        Recording r=RECORDINGS.remove(p.getUUID());if(r==null){restore(p);return;}
        if(r.menuSeen)captureTransfers(p,r);
        r.move(p);
        ItemStack s=find(p,r.watch);
        if(save&&!s.isEmpty()&&r.actions.size()>1){
            CompoundTag t=data(s),tl=new CompoundTag();tl.putUUID("id",UUID.randomUUID());tl.putString("name","Timeline "+(t.getList("saved",Tag.TAG_COMPOUND).size()+1));tl.putString("dimension",r.dimension);tl.put("actions",r.actions);t.put("current",tl);put(s,t);
        }
        restore(p);say(p,save?"Recording finished. Left click to manifest.":"Recording cancelled; inventory restored.");
    }
    static void restore(ServerPlayer p){
        CompoundTag persistent=p.getPersistentData();if(!persistent.contains(BACKUP))return;
        CompoundTag b=persistent.getCompound(BACKUP);UUID key=b.getUUID("watch");ItemStack timekeeper=find(p,key);
        List<ItemStack> extras=new ArrayList<>();
        for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(!s.isEmpty()&&s!=timekeeper)extras.add(s.copy());}
        p.getInventory().clearContent();p.getInventory().load(b.getList("inventory",Tag.TAG_COMPOUND));
        p.getInventory().setItem(b.getInt("slot"),timekeeper);
        for(ItemStack s:extras)if(!p.getInventory().add(s)&&!s.isEmpty())p.drop(s,false);
        persistent.remove(BACKUP);
        if("creative".equals(b.getString("mode")))p.setGameMode(GameType.CREATIVE);
        p.inventoryMenu.broadcastChanges();
    }
    static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)finish(p,false);}
    static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){RECORDINGS.remove(p.getUUID());restore(p);}}
    static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.getPersistentData().contains(BACKUP))finish(p,false);}
    static void toss(ItemTossEvent e){if(e.getPlayer() instanceof ServerPlayer p&&RECORDINGS.containsKey(p.getUUID())&&watch(e.getEntity().getItem())){e.setCanceled(true);p.getInventory().add(e.getEntity().getItem());}}
    static String itemKey(ItemStack s){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();}
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
        if(handler(p.serverLevel(),pos)!=null&&!p.isShiftKeyDown()){r.container=pos.immutable();r.previous=snapshot(p);r.menuSeen=false;return;}
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
        ListTag actions=timeline(tl);if(actions.isEmpty())return;
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
    static boolean transfer(FakePlayer f,IItemHandler h,ItemStack wanted,int n,boolean take){
        if(h==null||wanted.isEmpty())return false;
        Inventory inv=f.getInventory();
        if(take){
            int available=0,space=0;
            for(int i=0;i<h.getSlots();i++){ItemStack s=h.getStackInSlot(i);if(matches(s,wanted))available+=h.extractItem(i,n,true).getCount();}
            for(int i=0;i<36;i++){ItemStack s=inv.getItem(i);if(s.isEmpty())space+=wanted.getMaxStackSize();else if(ItemStack.isSameItemSameComponents(s,wanted))space+=Math.max(0,s.getMaxStackSize()-s.getCount());}
            if(available<n||space<n)return false;
            for(int i=0;i<h.getSlots()&&n>0;i++){if(!matches(h.getStackInSlot(i),wanted))continue;ItemStack s=h.extractItem(i,n,false);int got=s.getCount();inv.add(s);if(!s.isEmpty())f.drop(s,false);n-=got;}return n==0;
        }
        int available=0;for(int i=0;i<36;i++)if(matches(inv.getItem(i),wanted))available+=inv.getItem(i).getCount();if(available<n)return false;
        // Insert only actual stacks, preserving durability and components. Partial transfers track remaining count.
        for(int i=0;i<36&&n>0;i++){ItemStack s=inv.getItem(i);if(!matches(s,wanted))continue;int want=Math.min(n,s.getCount());ItemStack pending=s.copyWithCount(want);for(int j=0;j<h.getSlots()&&!pending.isEmpty();j++)pending=h.insertItem(j,pending,false);int moved=want-pending.getCount();s.shrink(moved);n-=moved;}
        return n==0;
    }
    static void collect(FakePlayer f,ServerLevel l,BlockPos pos){for(ItemEntity e:l.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(1.2))){ItemStack s=e.getItem();if(f.getInventory().add(s)&&s.isEmpty())e.discard();else e.setItem(s);}}
    static void levelTick(LevelTickEvent.Post e){
        if(!(e.getLevel() instanceof ServerLevel l))return;WorldData d=world(l);
        for(int i=d.echoes.size()-1;i>=0;i--){CompoundTag a=d.echoes.getCompound(i);try{if(step(l,a)){d.echoes.remove(i);}d.setDirty();}catch(Exception ex){a.putBoolean("dismiss",true);d.setDirty();}}
    }
    static boolean step(ServerLevel l,CompoundTag a){
        CompoundTag position=a.getCompound("position");Vec3 here=vec(position);if(!l.hasChunkAt(BlockPos.containing(here)))return false;
        ServerPlayer owner=l.getServer().getPlayerList().getPlayer(a.getUUID("owner"));
        ItemStack watch=owner==null?ItemStack.EMPTY:find(owner,a.getUUID("watch"));
        ArmorStand stand=a.hasUUID("stand")&&l.getEntity(a.getUUID("stand")) instanceof ArmorStand st?st:null;
        FakePlayer f=FakePlayerFactory.get(l,new GameProfile(a.getUUID("id"),"ChronoEcho"));f.getInventory().clearContent();f.getInventory().load(a.getList("inventory",Tag.TAG_COMPOUND));f.setPos(here.x,here.y,here.z);f.setYRot(position.getFloat("yaw"));f.setXRot(position.getFloat("pitch"));f.setShiftKeyDown(position.getBoolean("sneak"));
        if(a.getBoolean("dismiss")||(!watch.isEmpty()&&!a.getBoolean("creative")&&energy(watch)<=0)){
            for(int i=0;i<f.getInventory().getContainerSize();i++){ItemStack s=f.getInventory().getItem(i);if(!s.isEmpty())l.addFreshEntity(new ItemEntity(l,here.x,here.y,here.z,s.copy()));}
            f.getInventory().clearContent();if(stand!=null)stand.discard();return true;
        }
        if(owner==null||watch.isEmpty())return false;
        if(stand==null){stand=new ArmorStand(l,here.x,here.y,here.z);stand.setNoGravity(true);stand.setInvulnerable(true);stand.setMarker(true);stand.setShowArms(true);stand.setCustomName(Component.literal(a.getString("name")+" [Echo]"));stand.setCustomNameVisible(true);stand.addTag("chronofactory_echo");l.addFreshEntity(stand);a.putUUID("stand",stand.getUUID());}
        ListTag actions=timeline(a.getCompound("timeline"));if(actions.isEmpty())return true;
        int index=a.getInt("index")%actions.size();CompoundTag action=actions.getCompound(index);String type=action.getString("type");Vec3 target=vec(action);boolean done=false;
        if(!l.hasChunkAt(BlockPos.containing(target)))return false;
        if("move".equals(type)){
            Vec3 diff=target.subtract(here);double distance=diff.length();Vec3 next=distance<=.3?target:here.add(diff.scale(.3/distance));
            if(l.noCollision(f,f.getBoundingBox().move(next.subtract(here)))){position=action.copy();position.putDouble("x",next.x);position.putDouble("y",next.y);position.putDouble("z",next.z);a.put("position",position);here=next;done=distance<=.3;}
        }else{
            BlockPos pos=BlockPos.of(action.getLong("pos"));ItemStack wanted=template(action,l);
            if(l.hasChunkAt(pos)&&here.distanceToSqr(Vec3.atCenterOf(pos))<=36){
                if("transfer".equals(type)){
                    IItemHandler h=handler(l,pos);
                    // Simulate the entire output before committing to avoid repeating partial transfers.
                    int n=action.getInt("count");if(!action.getBoolean("take")&&h!=null){
                        int capacity=0;for(int j=0;j<h.getSlots();j++){ItemStack rest=h.insertItem(j,wanted.copyWithCount(Math.min(n,wanted.getMaxStackSize())),true);capacity+=Math.min(n,wanted.getMaxStackSize())-rest.getCount();}
                        if(capacity>=n)done=transfer(f,h,wanted,n,false);
                    }else done=transfer(f,h,wanted,n,true);
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
        if(done){a.putInt("index",(index+1)%actions.size());a.putFloat("progress",0);}
        stand.setPos(here.x,here.y,here.z);stand.setYRot(position.getFloat("yaw"));stand.setItemSlot(EquipmentSlot.MAINHAND,f.getMainHandItem().copy());stand.setCustomName(Component.literal(a.getString("name")+(done||"move".equals(type)?" [Echo]":" [Stasis]")));
        a.put("inventory",f.getInventory().save(new ListTag()));f.getInventory().clearContent();
        if(!a.getBoolean("creative"))energy(watch,energy(watch)-(done||"move".equals(type)?8:2));
        return false;
    }
    static final class WatchMenu extends ChestMenu{
        final ServerPlayer owner;final UUID key;final SimpleContainer display;
        WatchMenu(int n,Inventory inv,ServerPlayer p,UUID key){this(n,inv,p,key,new SimpleContainer(54));}
        private WatchMenu(int n,Inventory inv,ServerPlayer p,UUID key,SimpleContainer c){super(MenuType.GENERIC_9x6,n,inv,c,6);owner=p;this.key=key;display=c;refresh();}
        void icon(int slot,Item item,String name){ItemStack s=new ItemStack(item);s.set(DataComponents.CUSTOM_NAME,Component.literal(name));display.setItem(slot,s);}
        void refresh(){display.clearContent();ItemStack s=find(owner,key);if(s.isEmpty())return;CompoundTag t=data(s);icon(0,Items.REDSTONE,"Energy: "+(creative(s)?"Unlimited":energy(s)+" / "+CAPACITY+" FE"));icon(1,Items.PAPER,"Retain recording: "+(t.getBoolean("retain")?"ON":"OFF"));icon(2,Items.LIME_DYE,"Manifest current recording");icon(3,Items.RED_DYE,"Dismiss all");icon(4,Items.BARRIER,"Clear current recording");if(creative(s))icon(5,Items.BOOK,"Save current recording");ListTag saved=t.getList("saved",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(MAX_TIMELINES,saved.size());i++){CompoundTag tl=saved.getCompound(i);icon(9+i,active(owner,key,tl.getUUID("id"))?Items.LIME_DYE:Items.PAPER,tl.getString("name")+" | click to toggle");icon(30+i,Items.BARRIER,"Delete "+tl.getString("name"));}broadcastChanges();}
        @Override public void clicked(int slot,int button,ClickType type,Player p){if(p!=owner||type!=ClickType.PICKUP)return;ItemStack s=find(owner,key);if(s.isEmpty())return;CompoundTag t=data(s);ListTag saved=t.getList("saved",Tag.TAG_COMPOUND);
            if(slot==1){t.putBoolean("retain",!t.getBoolean("retain"));put(s,t);}
            else if(slot==2&&t.contains("current"))manifest(owner,s,t.getCompound("current"));
            else if(slot==3)dismiss(owner,key,null);
            else if(slot==4){if(t.contains("current"))dismiss(owner,key,t.getCompound("current").getUUID("id"));t.remove("current");put(s,t);}
            else if(slot==5&&creative(s)&&t.contains("current")&&saved.size()<MAX_TIMELINES){saved.add(t.getCompound("current").copy());t.put("saved",saved);t.remove("current");put(s,t);}
            else if(slot>=9&&slot<9+saved.size()){CompoundTag tl=saved.getCompound(slot-9);if(active(owner,key,tl.getUUID("id")))dismiss(owner,key,tl.getUUID("id"));else manifest(owner,s,tl);}
            else if(slot>=30&&slot<30+saved.size()){CompoundTag tl=saved.getCompound(slot-30);dismiss(owner,key,tl.getUUID("id"));saved.remove(slot-30);t.put("saved",saved);put(s,t);}
            refresh();
        }
        @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
        @Override public boolean stillValid(Player p){return p==owner&&!find(owner,key).isEmpty();}
    }
}
