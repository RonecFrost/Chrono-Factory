package com.ronec.chronofactory;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TimekeeperScreen extends Screen {
    final UUID key;
    private CompoundTag state;
    private int left,top,w,h,page;
    private int rows=3;
    private final int purple=0xFFBB86FC, grey=0xFFAAAAAF;
    TimekeeperScreen(UUID key,CompoundTag state){super(Component.literal("Timekeeper"));this.key=key;this.state=state;}
    void update(CompoundTag s){state=s;clearWidgets();init();}
    private boolean creative(){return state.getBoolean("creative");}
    private boolean recording(){return state.getBoolean("recording");}
    private boolean hasCurrent(){return state.getBoolean("current");}
    private boolean running(){return state.getBoolean("currentActive");}
    private ListTag saved(){return state.getList("saved",Tag.TAG_COMPOUND);}
    private void send(String action,int index){PacketDistributor.sendToServer(new ChronoFactory.UiCommand(key,action,index));}
    private Button button(int x,int y,int width,String label,Runnable callback,boolean enabled){
        Button b=Button.builder(Component.literal(label),v->callback.run()).bounds(left+x,top+y,width,20).build();
        b.active=enabled;return addRenderableWidget(b);
    }
    @Override protected void init(){
        w=Math.min(344,width-12);h=Math.min(creative()?278:204,height-8);
        left=(width-w)/2;top=(height-h)/2;
        int half=(w-30)/2;
        boolean unlocked=!recording()&&!state.getBoolean("otherRecording");
        String main=recording()?"Finish recording":running()?"Dismiss Echo":hasCurrent()?"Manifest Echo":"Start recording";
        button(10,78,half,main,()->{
            if(recording()){send("cycle",0);}
            else if(running())send("dismiss_current",0);
            else if(hasCurrent())send("manifest",0);
            else {send("cycle",0);onClose();}
        },recording()||unlocked);
        button(20+half,78,half,recording()?"Cancel recording":"Clear recording",()->send(recording()?"cancel":"clear",0),recording()||(unlocked&&hasCurrent()));
        button(10,103,half,"Retain: "+(state.getBoolean("retain")?"ON":"OFF"),()->send("retain",0),unlocked);
        button(20+half,103,half,"Tint: "+ChronoFactory.TINT_NAMES[state.getInt("tint")%ChronoFactory.TINT_NAMES.length],()->send("tint",-1),unlocked);
        if(creative()){
            button(10,128,half,"Save current",()->send("save",0),unlocked&&hasCurrent()&&saved().size()<20);
            button(20+half,128,half,"Dismiss all",()->send("dismiss_all",0),unlocked&&state.getInt("activeCount")>0);
            rows=Math.max(1,(h-181)/22);
            int max=Math.max(0,(saved().size()-1)/rows);page=Math.min(page,max);
            for(int row=0;row<rows;row++){
                int i=page*rows+row;if(i>=saved().size())break;
                int y=154+row*22;CompoundTag entry=saved().getCompound(i);
                button(w-174,y,50,ChronoFactory.TINT_NAMES[entry.getInt("tint")%ChronoFactory.TINT_NAMES.length],()->send("tint",i),unlocked);
                button(w-122,y,74,entry.getBoolean("active")?"Dismiss":"Manifest",()->send("toggle",i),unlocked);
                button(w-44,y,34,"Del",()->send("delete",i),unlocked);
            }
            button(10,h-25,32,"<",()->{page--;clearWidgets();init();},page>0);
            button(46,h-25,32,">",()->{page++;clearWidgets();init();},page<max);
            button(w-88,h-25,78,"Close",this::onClose,true);
        }else button(w-88,h-26,78,"Close",this::onClose,true);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        g.fill(0,0,width,height,0xB0000000);
        g.fill(left-1,top-1,left+w+1,top+h+1,0xFF503067);
        g.fill(left,top,left+w,top+h,0xFF17131D);
        g.fill(left,top,left+w,top+3,0xFFAB4DE3);
        g.drawString(font,creative()?"CREATIVE PERSONAL TIMEKEEPER":"PERSONAL TIMEKEEPER",left+10,top+10,purple,false);
        g.drawString(font,"PARALLEL TIMELINE",left+10,top+23,grey,false);
        g.fill(left+10,top+37,left+w-10,top+47,0xFF30243A);
        int energy=state.getInt("energy");
        int bar=creative()?w-20:(int)((w-20)*Math.min(1f,Math.max(0f,energy/100000f)));
        g.fill(left+10,top+37,left+10+bar,top+47,0xFF7530AA);
        g.drawCenteredString(font,creative()?"UNLIMITED ENERGY":energy+" / 100000 FE",left+w/2,top+38,0xFFFFFFFF);
        String status=recording()?"RECORDING":running()?"ECHO ACTIVE":hasCurrent()?"RECORDING READY":"NO RECORDING";
        if(state.getBoolean("otherRecording"))status="ANOTHER WATCH IS RECORDING";
        g.drawString(font,status,left+10,top+52,recording()?0xFFFF6C84:running()?0xFF7BE0AB:purple,false);
        g.drawString(font,state.getInt("steps")+" steps  |  "+state.getInt("activeCount")+" active echoes",left+10,top+65,grey,false);
        if(creative()){
            for(int row=0;row<rows;row++){
                int i=page*rows+row;if(i>=saved().size())break;
                CompoundTag t=saved().getCompound(i);int y=154+row*22;
                g.fill(left+10,top+y,left+w-180,top+y+20,0xFF261C31);
                String name=font.plainSubstrByWidth(t.getString("name"),Math.max(10,w-198));
                g.drawString(font,name,left+14,top+y+2,0xFF000000|ChronoFactory.TINTS[t.getInt("tint")%ChronoFactory.TINTS.length],false);
                g.drawString(font,t.getInt("steps")+" steps"+(t.getBoolean("active")?" / active":""),left+14,top+y+11,grey,false);
            }
            int max=Math.max(1,(saved().size()+rows-1)/rows);
            g.drawString(font,(page+1)+"/"+max+"   "+saved().size()+" saved",left+84,top+h-19,grey,false);
        }else g.drawString(font,"Left-click to cycle. Right-click to manage.",left+10,top+132,grey,false);
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen(){return false;}
}
