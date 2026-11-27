package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class EchoInventoryMenu extends AbstractContainerMenu{
    static final DeferredRegister<MenuType<?>> TYPES=DeferredRegister.create(Registries.MENU,ChronoFactory.ID);
    static final DeferredHolder<MenuType<?>,MenuType<EchoInventoryMenu>> TYPE=TYPES.register("echo_inventory",()->IMenuTypeExtension.create((id,inv,buf)->new EchoInventoryMenu(id,inv,buf.readUUID(),new SimpleContainer(41),null)));
    final UUID key;
    private final Inventory player;
    private final SimpleContainer echo;
    private final ServerLevel level;
    private final CompoundTag job;
    private boolean ready=false;
    private EchoInventoryMenu(int id,Inventory player,UUID key,SimpleContainer echo,CompoundTag job){
        super(TYPE.get(),id);this.player=player;this.key=key;this.echo=echo;this.job=job;this.level=player.player.level() instanceof ServerLevel l?l:null;
        if(job!=null){for(Tag entry:job.getList("inventory",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)entry;int i=t.getByte("Slot")&255;if(i>=100&&i<=103)i=36+i-100;else if(i==150)i=40;
            if(i>=0&&i<41)echo.setItem(i,ItemStack.parseOptional(player.player.registryAccess(),t));
        }}
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new EchoSlot(echo,9+row*9+col,9+col*18,61+row*18));
        for(int col=0;col<9;col++)addSlot(new EchoSlot(echo,col,9+col*18,119));
        for(int row=0;row<4;row++)addSlot(new EchoSlot(echo,39-row,181+row*18,61));
        addSlot(new EchoSlot(echo,40,181,84));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,9+col*18,160+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,9+col*18,218));
        ready=true;echo.addListener(c->persist());
    }
    static final class EchoSlot extends Slot{
        EchoSlot(Container c,int index,int x,int y){super(c,index,x,y);}
        @Override public boolean mayPlace(ItemStack st){return !ChronoFactory.watch(st);}
    }
    private void persist(){
        if(!ready||job==null||level==null||ChronoFactory.echoData(level,key)!=job)return;
        ListTag items=new ListTag();for(int i=0;i<41;i++){
            ItemStack st=echo.getItem(i);if(st.isEmpty())continue;CompoundTag t=(CompoundTag)st.save(level.registryAccess());
            t.putByte("Slot",(byte)(i<36?i:i<40?100+i-36:150));items.add(t);
        }
        job.put("inventory",items);ChronoFactory.world(level).setDirty();
    }
    @Override public void clicked(int slot,int button,ClickType type,Player p){
        if(!stillValid(p))return;
        super.clicked(slot,button,type,p);persist();
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size()||!stillValid(p))return ItemStack.EMPTY;
        Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem(),copy=stack.copy();
        if(index<41){if(!moveItemStackTo(stack,41,77,true))return ItemStack.EMPTY;}
        else{if(ChronoFactory.watch(stack)||!moveItemStackTo(stack,0,36,false))return ItemStack.EMPTY;}
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);persist();return copy;
    }
    @Override public boolean stillValid(Player p){
        if(job==null)return true;
        return !job.getBoolean("dismiss")&&ChronoFactory.echoData(level,key)==job&&p.level()==level&&p.position().distanceToSqr(ChronoFactory.vec(job.getCompound("position")))<=64;
    }
    static boolean isOpen(ServerLevel l,UUID id){
        for(ServerPlayer p:l.getServer().getPlayerList().getPlayers())if(p.containerMenu instanceof EchoInventoryMenu m&&m.key.equals(id)&&m.level==l&&m.stillValid(p))return true;return false;
    }
    static void closeFor(ServerLevel l,UUID id){
        for(ServerPlayer p:l.getServer().getPlayerList().getPlayers())if(p.containerMenu instanceof EchoInventoryMenu m&&m.key.equals(id)&&m.level==l)p.closeContainer();
    }
    static void open(ServerPlayer p,EchoEntity entity){
        ServerLevel l=p.serverLevel();UUID id=entity.loopId();CompoundTag a=ChronoFactory.echoData(l,id);
        if(a==null||a.getBoolean("dismiss")||a.getInt("phase")<24||a.getInt("fade")>0){ChronoFactory.say(p,"Echo is not currently available.");return;}
        if(ChronoFactory.RECORDINGS.containsKey(p.getUUID())){ChronoFactory.say(p,"Finish recording before opening an Echo inventory.");return;}
        if(!a.getUUID("owner").equals(p.getUUID())&&!p.hasPermissions(2)){ChronoFactory.say(p,"Only the Echo owner can edit its inventory.");return;}
        if(isOpen(l,id)){ChronoFactory.say(p,"This Echo inventory is already open.");return;}
        p.openMenu(new SimpleMenuProvider((n,inv,player)->new EchoInventoryMenu(n,inv,id,new SimpleContainer(41),a),Component.literal("Echo inventory")),buf->buf.writeUUID(id));
        ChronoFactory.inspect(p,id,false);
    }
}
