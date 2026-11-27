package com.ronec.chronofactory;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
public final class TimelineNameScreen extends Screen{
    private final TimekeeperScreen parent;
    private final UUID watch,timeline;
    private String initial;private EditBox field;private Button save;private int left,top,w;
    TimelineNameScreen(TimekeeperScreen parent,UUID watch,UUID timeline,String initial){
        super(Component.literal("Rename timeline"));this.parent=parent;this.watch=watch;this.timeline=timeline;this.initial=initial;
    }
    @Override protected void init(){
        w=Math.min(270,width-12);left=(width-w)/2;top=(height-112)/2;
        field=new EditBox(font,left+10,top+35,w-20,20,Component.literal("Timeline name"));field.setMaxLength(48);field.setValue(initial);
        addRenderableWidget(field);setInitialFocus(field);field.setHighlightPos(0);
        int half=(w-26)/2;
        save=addRenderableWidget(Button.builder(Component.literal("Save name"),b->submit()).bounds(left+10,top+75,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(left+16+half,top+75,half,20).build());
        field.setResponder(v->{initial=v;save.active=!v.strip().isEmpty();});save.active=!field.getValue().strip().isEmpty();
    }
    private void submit(){String name=field.getValue().strip();if(name.isEmpty())return;PacketDistributor.sendToServer(new ChronoFactory.RenameTimeline(watch,timeline,name));minecraft.setScreen(parent);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==257||key==335){submit();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public void render(GuiGraphics g,int x,int y,float p){
        g.fill(0,0,width,height,0xB0000000);g.fill(left-1,top-1,left+w+1,top+113,0xFF503067);g.fill(left,top,left+w,top+112,0xFF17131D);g.fill(left,top,left+w,top+3,0xFFAB4DE3);
        g.drawString(font,"RENAME TIMELINE",left+10,top+10,0xFFBB86FC,false);
        g.drawString(font,"Up to 48 characters",left+10,top+59,0xFFAAAAAF,false);super.render(g,x,y,p);
    }
    @Override public boolean isPauseScreen(){return false;}
}
