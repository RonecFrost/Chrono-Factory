package com.ronec.chronofactory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid=ChronoFactory.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class EchoInventoryScreen extends AbstractContainerScreen<EchoInventoryMenu>{
    private CompoundTag info=new CompoundTag();private int poll;
    public EchoInventoryScreen(EchoInventoryMenu menu,Inventory inv,Component title){super(menu,inv,title);imageWidth=262;imageHeight=242;}
    @SubscribeEvent public static void register(RegisterMenuScreensEvent e){e.register(EchoInventoryMenu.TYPE.get(),EchoInventoryScreen::new);}
    void update(CompoundTag data){info=data;}
    @Override protected void containerTick(){super.containerTick();if(++poll%10==0)PacketDistributor.sendToServer(new ChronoFactory.UiCommand(menu.key,"inspect",0));}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        g.fill(leftPos-1,topPos-1,leftPos+imageWidth+1,topPos+imageHeight+1,0xFF503067);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF17131D);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+3,0xFFAB4DE3);
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;g.fill(x-1,y-1,x+17,y+17,0xFF5A4569);g.fill(x,y,x+16,y+16,0xFF2C2335);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        String name=info.getString("name");g.drawString(font,font.plainSubstrByWidth(name.isEmpty()?"TEMPORAL ECHO":name,imageWidth-18),9,9,0xFFBB86FC,false);
        g.drawString(font,(info.getBoolean("strict")?"Input":"Adaptive")+" | Cycle "+info.getInt("cycle")+(info.getInt("repeats")>0?"/"+info.getInt("repeats"):""),9,20,0xFFBDB1C8,false);
        g.drawString(font,font.plainSubstrByWidth("Task: "+info.getString("task"),imageWidth-18),9,31,0xFFF0E5F5,false);
        g.drawString(font,font.plainSubstrByWidth(info.getString("reason"),imageWidth-18),9,42,0xFFE5B975,false);
        g.drawString(font,"Echo inventory (paused)",9,51,0xFFBDB1C8,false);
        g.drawString(font,"Your inventory",9,148,0xFFBDB1C8,false);
        g.drawString(font,"Armor",181,51,0xFFBDB1C8,false);g.drawString(font,"Offhand",181,105,0xFFBDB1C8,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){renderBackground(g,mx,my,partial);super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xB0000000);}
}
