package com.ronec.chronofactory;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
public final class PlaybackScreen extends Screen{
    final UUID key;private final TimekeeperScreen parent;private CompoundTag state;private int page,poll,x,y,w,h,rows;
    PlaybackScreen(TimekeeperScreen parent,UUID key,CompoundTag state){super(Component.literal("Playback controls"));this.parent=parent;this.key=key;this.state=state;}
    private ListTag jobs(){return state.getList("jobs",Tag.TAG_COMPOUND);}
    void update(CompoundTag tag){boolean changed=!jobs().equals(tag.getList("jobs",Tag.TAG_COMPOUND));state=tag;if(changed){clearWidgets();init();}}
    private void send(UUID id,String cmd){PacketDistributor.sendToServer(new ChronoFactory.UiCommand(id,cmd,0));}
    private void button(int a,int b,int width,String text,Runnable action,boolean enabled){Button v=Button.builder(Component.literal(text),r->action.run()).bounds(x+a,y+b,width,20).build();v.active=enabled;addRenderableWidget(v);}
    @Override protected void init(){w=Math.min(344,width-12);h=Math.min(282,height-8);x=(width-w)/2;y=(height-h)/2;rows=Math.max(1,(h-60)/62);int max=Math.max(0,(jobs().size()-1)/rows);page=Math.min(page,max);
        for(int n=0;n<rows;n++){int k=page*rows+n;if(k>=jobs().size())break;CompoundTag row=jobs().getCompound(k);UUID id=row.getUUID("id");int yy=28+n*62,third=(w-32)/3;
            button(8,yy+34,third,"Previous",()->send(id,"echo_prev"),row.getBoolean("canPrev"));
            button(12+third,yy+34,third,row.getBoolean("paused")?"Resume":"Pause",()->send(id,row.getBoolean("paused")?"echo_resume":"echo_pause"),true);
            button(16+third*2,yy+34,third,"Next task",()->send(id,"echo_next"),row.getBoolean("canNext"));
        }
        button(8,h-25,32,"<",()->{page--;clearWidgets();init();},page>0);button(44,h-25,32,">",()->{page++;clearWidgets();init();},page<max);button(w-80,h-25,72,"Back",this::onClose,true);
    }
    @Override public void tick(){if(++poll%10==0)send(key,"refresh");}
    @Override public void onClose(){parent.update(state);minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
    @Override public void render(GuiGraphics g,int mx,int my,float p){
        g.fill(0,0,width,height,0xB0000000);g.fill(x-1,y-1,x+w+1,y+h+1,0xFF503067);g.fill(x,y,x+w,y+h,0xFF17131D);g.fill(x,y,x+w,y+3,0xFFAB4DE3);
        g.drawString(font,"ECHO PLAYBACK CONTROLS",x+8,y+10,0xFFBB86FC,false);
        for(int n=0;n<rows;n++){int k=page*rows+n;if(k>=jobs().size())break;CompoundTag row=jobs().getCompound(k);int yy=y+28+n*62;
            g.fill(x+6,yy-3,x+w-6,yy+55,0xFF261C31);
            g.drawString(font,font.plainSubstrByWidth(row.getString("name")+(row.getBoolean("paused")?" [PAUSED]":""),w-16),x+8,yy,0xFFF0E5F5,false);
            g.drawString(font,(row.getBoolean("strict")?"Tick ":"Step ")+row.getInt("step")+"/"+row.getInt("total")+" | "+row.getString("task"),x+8,yy+11,0xFFBDB1C8,false);
            g.drawString(font,font.plainSubstrByWidth(row.getString("reason"),w-16),x+8,yy+22,0xFFE5B975,false);
        }
        if(jobs().isEmpty())g.drawString(font,"No active Echoes",x+8,y+36,0xFFBDB1C8,false);
        super.render(g,mx,my,p);
    }
}
