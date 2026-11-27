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
        g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),leftPos-1,topPos-1,leftPos+imageWidth+1,topPos+imageHeight+1,0xFF73578D);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF30263B);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),leftPos+5,topPos+57,leftPos+imageWidth-5,topPos+141,0xFF554765);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),leftPos+5,topPos+157,leftPos+imageWidth-5,topPos+238,0xFF554765);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),leftPos,topPos,leftPos+imageWidth,topPos+3,0xFFAB4DE3);
        for(var slot:menu.slots){int x=leftPos+slot.x,y=topPos+slot.y;g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x-1,y-1,x+17,y+17,0xFFB0A4BE);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x,y,x+16,y+16,0xFF776A85);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x,y,x+16,y+1,0xFF40354B);g.fill(net.minecraft.client.renderer.RenderType.guiOverlay(),x,y,x+1,y+16,0xFF40354B);}
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){
        String name=info.getString("name");g.drawString(font,font.plainSubstrByWidth(name.isEmpty()?"TEMPORAL ECHO":name,imageWidth-18),9,9,0xFFBB86FC,false);
        g.drawString(font,(info.getBoolean("strict")?"Input":"Adaptive")+" | XP "+info.getInt("xpLevel")+" | Cycle "+info.getInt("cycle")+(info.getInt("repeats")>0?"/"+info.getInt("repeats"):""),9,20,0xFFBDB1C8,false);
        g.drawString(font,font.plainSubstrByWidth("Task: "+info.getString("task"),imageWidth-18),9,31,0xFFF0E5F5,false);
        g.drawString(font,font.plainSubstrByWidth(info.getString("reason"),imageWidth-18),9,42,0xFFE5B975,false);
        g.drawString(font,"Echo inventory (paused)",9,51,0xFFBDB1C8,false);
        g.drawString(font,"Your inventory",9,148,0xFFBDB1C8,false);
        g.drawString(font,"Armor",181,51,0xFFBDB1C8,false);g.drawString(font,"Offhand",181,105,0xFFBDB1C8,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();renderBg(g,partial,mx,my);g.flush();super.render(g,mx,my,partial);renderTooltip(g,mx,my);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){/* The panel is drawn directly in render(), not in the background hook. */}
}
