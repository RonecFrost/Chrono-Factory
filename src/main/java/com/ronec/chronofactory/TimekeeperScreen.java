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
    private static final int CLOSED=-2;
    private int popupIndex=CLOSED,popupX,popupY,popupW=114,popupSelected,popupFocus;
    private final int optionHeight=17;
    private final int purple=0xFFBB86FC, grey=0xFFAAAAAF;
    TimekeeperScreen(UUID key,CompoundTag state){super(Component.literal("Timekeeper"));this.key=key;this.state=state;}
    void update(CompoundTag s){state=s;popupIndex=CLOSED;clearWidgets();init();}
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
        popupIndex=CLOSED;
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
        button(20+half,103,half,"Tint: "+ChronoFactory.TINT_NAMES[state.getInt("tint")%ChronoFactory.TINT_NAMES.length]+" ▾",()->openTint(-1,20+half,103,half,state.getInt("tint")),unlocked);
        if(creative()){
            button(10,128,half,"Save current",()->send("save",0),unlocked&&hasCurrent()&&saved().size()<20);
            button(20+half,128,half,"Dismiss all",()->send("dismiss_all",0),unlocked&&state.getInt("activeCount")>0);
            rows=Math.max(1,(h-181)/22);
            int max=Math.max(0,(saved().size()-1)/rows);page=Math.min(page,max);
            for(int row=0;row<rows;row++){
                int i=page*rows+row;if(i>=saved().size())break;
                int y=154+row*22;CompoundTag entry=saved().getCompound(i);
                button(w-174,y,50,ChronoFactory.TINT_NAMES[entry.getInt("tint")%ChronoFactory.TINT_NAMES.length]+" ▾",()->openTint(i,w-174,y,50,entry.getInt("tint")),unlocked);
                button(w-122,y,74,entry.getBoolean("active")?"Dismiss":"Manifest",()->send("toggle",i),unlocked);
                button(w-44,y,34,"Del",()->send("delete",i),unlocked);
            }
            button(10,h-25,32,"<",()->{page--;clearWidgets();init();},page>0);
            button(46,h-25,32,">",()->{page++;clearWidgets();init();},page<max);
            button(w-88,h-25,78,"Close",this::onClose,true);
        }else button(w-88,h-26,78,"Close",this::onClose,true);
    }
    private void openTint(int index,int x,int y,int buttonW,int selection){
        if(popupIndex==index){popupIndex=CLOSED;return;}
        popupIndex=index;popupSelected=Math.floorMod(selection,ChronoFactory.TINT_NAMES.length);popupFocus=popupSelected;
        int total=optionHeight*ChronoFactory.TINT_NAMES.length+4;
        popupW=Math.max(114,buttonW);
        popupX=Math.max(2,Math.min(width-popupW-2,left+x));
        popupY=top+y+21;
        if(popupY+total>height-2)popupY=top+y-total-1;
        popupY=Math.max(2,Math.min(height-total-2,popupY));
    }
    private void chooseTint(int choice){
        int target=popupIndex;popupIndex=CLOSED;
        send("set_tint",(target+1)*ChronoFactory.TINT_NAMES.length+choice);
    }
    private int hoveredOption(double x,double y){
        if(x<popupX+2||x>=popupX+popupW-2||y<popupY+2)return -1;
        int i=(int)((y-popupY-2)/optionHeight);
        return i>=0&&i<ChronoFactory.TINT_NAMES.length?i:-1;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(popupIndex!=CLOSED){
            int hovered=hoveredOption(x,y);
            if(button==0&&hovered>=0)chooseTint(hovered);else popupIndex=CLOSED;
            return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(popupIndex!=CLOSED){
            if(key==256){popupIndex=CLOSED;return true;}
            if(key==264||key==265){popupFocus=Math.floorMod(popupFocus+(key==264?1:-1),ChronoFactory.TINT_NAMES.length);return true;}
            if(key==257||key==335||key==32){chooseTint(popupFocus);return true;}
        }
        return super.keyPressed(key,scan,modifiers);
    }
    private void renderTintDropdown(GuiGraphics g,int mouseX,int mouseY){
        if(popupIndex==CLOSED)return;
        g.pose().pushPose();g.pose().translate(0,0,350);
        int total=optionHeight*ChronoFactory.TINT_NAMES.length+4;
        g.fill(popupX,popupY,popupX+popupW,popupY+total,0xFF9960C4);
        g.fill(popupX+1,popupY+1,popupX+popupW-1,popupY+total-1,0xFF21182B);
        int hovered=hoveredOption(mouseX,mouseY);
        for(int i=0;i<ChronoFactory.TINT_NAMES.length;i++){
            int y=popupY+2+i*optionHeight;
            if(i==hovered||i==popupFocus)g.fill(popupX+2,y,popupX+popupW-2,y+optionHeight,0xFF54366D);
            g.fill(popupX+6,y+3,popupX+17,y+14,0xFF000000|ChronoFactory.TINTS[i]);
            g.drawString(font,ChronoFactory.TINT_NAMES[i],popupX+22,y+5,0xFFF1E5F9,false);
            if(i==popupSelected)g.drawString(font,"✓",popupX+popupW-15,y+5,0xFFCB9CFF,false);
        }
        g.pose().popPose();
    }
    // Screen.render normally invokes its background/blur pass. We draw our own
    // backdrop before the panel and keep vanilla widgets on the same sharp layer.
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick){}
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
        renderTintDropdown(g,mouseX,mouseY);
    }
    @Override public boolean isPauseScreen(){return false;}
}
