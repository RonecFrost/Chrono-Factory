package com.ronec.chronofactory;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
public final class RelayScreen extends Screen{
    final BlockPos pos;private CompoundTag state;private int x,y,w,poll,page;private static final UUID NONE=new UUID(0,0);
    RelayScreen(BlockPos pos,CompoundTag state){super(Component.literal("Temporal Relay"));this.pos=pos;this.state=state;}
    private ListTag entries(){return state.getList("choices",Tag.TAG_COMPOUND);}
    private UUID selected(){for(Tag e:entries()){CompoundTag t=(CompoundTag)e;if(t.getBoolean("selected"))return t.getUUID("id");}return NONE;}
    private CompoundTag choice(){for(Tag e:entries()){CompoundTag t=(CompoundTag)e;if(t.getUUID("id").equals(selected()))return t;}return new CompoundTag();}
    private boolean idle(){return state.getBoolean("available")&&state.getBoolean("allowed")&&!state.getBoolean("active")&&!state.getBoolean("pending");}
    private void send(String action,UUID id,int count){PacketDistributor.sendToServer(new TemporalRelay.Command(pos,action,id,count));}
    void update(CompoundTag tag){boolean rebuild=state.getBoolean("active")!=tag.getBoolean("active")||state.getBoolean("pending")!=tag.getBoolean("pending")||state.getBoolean("available")!=tag.getBoolean("available")||state.getBoolean("allowed")!=tag.getBoolean("allowed")||state.contains("watch")!=tag.contains("watch")||!entries().equals(tag.getList("choices",Tag.TAG_COMPOUND))||!selected().equals(tag.hasUUID("selected")?tag.getUUID("selected"):NONE);state=tag;if(rebuild){clearWidgets();init();}}
    private void button(int a,int b,int width,String label,Runnable cb,boolean enabled){Button v=Button.builder(Component.literal(label),r->cb.run()).bounds(x+a,y+b,width,20).build();v.active=enabled;addRenderableWidget(v);}
    @Override protected void init(){w=Math.min(278,width-10);x=(width-w)/2;y=(height-224)/2;
        boolean allowed=state.getBoolean("available")&&state.getBoolean("allowed");
        button(w-83,29,73,"Eject watch",()->send("eject",NONE,0),allowed&&state.contains("watch"));
        int max=Math.max(0,(entries().size()-1)/3);page=Math.min(page,max);
        for(int row=0;row<3;row++){int i=page*3+row;if(i>=entries().size())break;CompoundTag t=entries().getCompound(i);UUID id=t.getUUID("id");int n=t.getInt("repeats");
            button(10,79+row*22,w-67,(t.getBoolean("selected")?"[x] ":"[ ] ")+font.plainSubstrByWidth(t.getString("name"),w-120)+(t.getBoolean("running")?" *":""),()->send("select",id,0),idle());
            button(w-53,79+row*22,43,n==0?"Inf.":n+"x",()->minecraft.setScreen(new RepeatScreen(this,n,v->send("repeat",id,v))),idle());
        }
        button(10,147,30,"<",()->{page--;clearWidgets();init();},page>0);
        button(44,147,30,">",()->{page++;clearWidgets();init();},page<max);
        
        button(10,194,95,"Stop all jobs",()->send("stop",NONE,0),allowed&&(state.getBoolean("active")||state.getBoolean("pending")));
        button(w-76,194,66,"Close",this::onClose,true);
    }
    @Override public void tick(){if(++poll%10==0)send("poll",NONE,0);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float p){}
    @Override public void render(GuiGraphics g,int mx,int my,float p){
        g.fill(0,0,width,height,0xB0000000);g.fill(x-1,y-1,x+w+1,y+225,0xFF503067);g.fill(x,y,x+w,y+224,0xFF17131D);g.fill(x,y,x+w,y+3,0xFFAB4DE3);
        g.drawString(font,"TEMPORAL RELAY",x+10,y+10,0xFFBB86FC,false);
        g.fill(x+10,y+29,x+30,y+49,0xFF534365);g.fill(x+12,y+31,x+28,y+47,0xFF261C31);
        if(state.contains("watch")&&minecraft.level!=null){ItemStack item=ItemStack.parseOptional(minecraft.level.registryAccess(),state.getCompound("watch"));g.renderItem(item,x+12,y+31);}
        g.drawString(font,state.contains("watch")?(state.getBoolean("creative")?"Creative watch":"Personal watch"):"Insert a watch",x+35,y+35,0xFFE7DAF0,false);
        g.drawString(font,state.getBoolean("powered")?"REDSTONE ON":"REDSTONE OFF",x+10,y+56,state.getBoolean("powered")?0xFFFF7373:0xFFAAAAAF,false);
        g.drawString(font,state.getBoolean("creative")?"Unlimited FE":state.getInt("energy")+" / 100000 FE",x+w-118,y+56,0xFFBB86FC,false);
        String reason=state.getBoolean("available")?state.getString("reason"):"Relay unavailable / out of range";
        
        int yy=y+172;for(var line:font.split(Component.literal(reason),w-20)){if(yy>y+184)break;g.drawString(font,line,x+10,yy,0xFFD0BEDF,false);yy+=10;}
        super.render(g,mx,my,p);
    }
    @Override public boolean isPauseScreen(){return false;}
}
