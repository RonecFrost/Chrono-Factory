package com.ronec.chronofactory;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class RepeatScreen extends Screen{
    private final Screen parent;private final IntConsumer commit;private boolean infinite;private String text;private EditBox count;private Button mode,save;private int x,y,w;
    RepeatScreen(Screen parent,int current,IntConsumer commit){super(Component.literal("Repeat settings"));this.parent=parent;this.commit=commit;infinite=current==0;text=Integer.toString(current==0?1:current);}
    private int value(){if(infinite)return 0;try{int n=Integer.parseInt(count.getValue());return n>=1&&n<=ChronoFactory.MAX_REPEATS?n:-1;}catch(Exception ex){return -1;}}
    private void refresh(){mode.setMessage(Component.literal(infinite?"Infinite":"Fixed number of iterations"));count.active=!infinite;save.active=value()>=0;}
    @Override protected void init(){w=Math.min(258,width-12);x=(width-w)/2;y=(height-142)/2;
        mode=addRenderableWidget(Button.builder(Component.empty(),b->{infinite=!infinite;refresh();}).bounds(x+10,y+30,w-20,20).build());
        count=new EditBox(font,x+10,y+60,w-20,20,Component.literal("Iterations"));count.setMaxLength(4);count.setFilter(v->v.matches("[0-9]*"));count.setValue(text);addRenderableWidget(count);
        int half=(w-26)/2;save=addRenderableWidget(Button.builder(Component.literal("Save"),b->submit()).bounds(x+10,y+110,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(x+16+half,y+110,half,20).build());
        count.setResponder(v->{text=v;refresh();});refresh();
    }
    private void submit(){int n=value();if(n<0)return;minecraft.setScreen(parent);commit.accept(n);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float p){}
    @Override public void render(GuiGraphics g,int mx,int my,float p){
        g.fill(0,0,width,height,0xB0000000);g.fill(x-1,y-1,x+w+1,y+143,0xFF503067);g.fill(x,y,x+w,y+142,0xFF17131D);g.fill(x,y,x+w,y+3,0xFFAB4DE3);
        g.drawString(font,"TIMELINE REPEAT SETTINGS",x+10,y+10,0xFFBB86FC,false);
        g.drawString(font,infinite?"Runs until dismissed.":"Complete 1 to 9999 loops, then stop.",x+10,y+87,0xFFBDB1C8,false);super.render(g,mx,my,p);
    }
    @Override public boolean isPauseScreen(){return false;}
}
