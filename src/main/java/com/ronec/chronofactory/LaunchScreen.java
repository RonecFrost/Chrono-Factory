package com.ronec.chronofactory;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
final class LaunchScreen extends Screen{
    private final TimekeeperScreen parent;private final UUID key;private final int index;private final String name;
    LaunchScreen(TimekeeperScreen parent,UUID key,int index,String name){super(Component.literal("Launch timeline"));this.parent=parent;this.key=key;this.index=index;this.name=name;}
    void start(boolean offset){PacketDistributor.sendToServer(new ChronoFactory.UiCommand(key,offset?"preview_offset":"preview_start",index));minecraft.setScreen(null);}
    @Override protected void init(){int x=width/2-110,y=height/2-52;
        addRenderableWidget(Button.builder(Component.literal("Start"),b->start(false)).bounds(x,y+36,105,20).build());
        addRenderableWidget(Button.builder(Component.literal("Start at me"),b->start(true)).bounds(x+115,y+36,105,20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(x+65,y+66,90,20).build());
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
    @Override public void render(GuiGraphics g,int mx,int my,float p){int x=width/2-120,y=height/2-62;g.fill(0,0,width,height,0xB0000000);g.fill(x,y,x+240,y+112,0xFF21182B);g.fill(x,y,x+240,y+3,0xFFAB4DE3);g.drawCenteredString(font,font.plainSubstrByWidth(name,220),width/2,y+10,0xFFBB86FC);g.drawCenteredString(font,"Preview first, then left-click to manifest",width/2,y+25,0xFFE5D5F0);super.render(g,mx,my,p);}
}
