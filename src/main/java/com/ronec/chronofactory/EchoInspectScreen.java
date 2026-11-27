package com.ronec.chronofactory;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Live read-only inventory inspection; no client copies can be picked up. */
public final class EchoInspectScreen extends Screen {
    final UUID key;private CompoundTag state;
    private final ItemStack[] items=new ItemStack[41];private ItemStack cursor=ItemStack.EMPTY;
    private int left,top,poll;
    EchoInspectScreen(UUID key,CompoundTag state){super(Component.literal("Echo inventory"));this.key=key;Arrays.fill(items,ItemStack.EMPTY);this.state=state;}
    void update(CompoundTag tag){state=tag;decode();}
    private void decode(){
        Arrays.fill(items,ItemStack.EMPTY);cursor=ItemStack.EMPTY;
        if(minecraft==null||minecraft.level==null)return;
        for(Tag entry:state.getList("inventory",Tag.TAG_COMPOUND)){
            CompoundTag t=(CompoundTag)entry;int slot=t.getByte("Slot")&255;
            if(slot>=100&&slot<=103)slot=36+slot-100;else if(slot==150)slot=40;
            if(slot>=0&&slot<items.length)items[slot]=ItemStack.parseOptional(minecraft.level.registryAccess(),t);
        }
        if(state.contains("cursor"))cursor=ItemStack.parseOptional(minecraft.level.registryAccess(),state.getCompound("cursor"));
    }
    @Override protected void init(){
        left=(width-252)/2;top=(height-210)/2;decode();
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(left+174,top+184,66,20).build());
    }
    @Override public void tick(){
        if(++poll%10==0)PacketDistributor.sendToServer(new ChronoFactory.UiCommand(key,"inspect",0));
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    private ItemStack slot(GuiGraphics g,int x,int y,ItemStack st,int mx,int my){
        g.fill(x,y,x+18,y+18,0xFF5A4569);g.fill(x+1,y+1,x+17,y+17,0xFF2C2335);
        if(!st.isEmpty()){g.renderItem(st,x+1,y+1);g.renderItemDecorations(font,st,x+1,y+1);}
        if(mx>=x&&mx<x+18&&my>=y&&my<y+18){g.fill(x+1,y+1,x+17,y+17,0x66D5B5FF);return st;}
        return ItemStack.EMPTY;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xB0000000);g.fill(left-1,top-1,left+253,top+211,0xFF503067);g.fill(left,top,left+252,top+210,0xFF17131D);
        g.fill(left,top,left+252,top+3,0xFFAB4DE3);
        String name=state.getString("name");if(name.isEmpty())name="Temporal Echo";
        g.drawString(font,font.plainSubstrByWidth(name,230),left+10,top+10,0xFFBB86FC,false);
        if(!state.getBoolean("available")){g.drawString(font,"Echo unavailable or out of range.",left+10,top+38,0xFFFF9A9A,false);super.render(g,mx,my,partial);return;}
        boolean strict=state.getBoolean("strict");String mode=strict?"Input":"Adaptive";
        g.drawString(font,mode+"  |  Cycle "+state.getInt("cycle")+(state.getInt("repeats")>0?"/"+state.getInt("repeats"):"")+"  |  "+state.getInt("step")+"/"+state.getInt("total")+(strict?" ticks":" steps"),left+10,top+23,0xFFBDB1C8,false);
        g.fill(left+10,top+36,left+242,top+91,0xFF261C31);
        g.drawString(font,"CURRENT TASK",left+15,top+41,0xFFBB86FC,false);
        g.drawString(font,font.plainSubstrByWidth(state.getString("task"),220),left+15,top+53,0xFFF0E5F5,false);
        int y=top+65;for(var line:font.split(Component.literal(state.getString("reason")),218)){if(y>top+81)break;g.drawString(font,line,left+15,y,0xFFCBC0D3,false);y+=10;}
        g.drawString(font,"INVENTORY",left+10,top+96,0xFFBDB1C8,false);
        ItemStack hovered=ItemStack.EMPTY;
        for(int row=0;row<3;row++)for(int col=0;col<9;col++){ItemStack h=slot(g,left+10+col*18,top+108+row*18,items[9+row*9+col],mx,my);if(!h.isEmpty())hovered=h;}
        for(int col=0;col<9;col++){ItemStack h=slot(g,left+10+col*18,top+166,items[col],mx,my);if(!h.isEmpty())hovered=h;}
        for(int row=0;row<4;row++){ItemStack h=slot(g,left+182,top+108+row*18,items[39-row],mx,my);if(!h.isEmpty())hovered=h;}
        ItemStack h=slot(g,left+210,top+108,items[40],mx,my);if(!h.isEmpty())hovered=h;
        g.drawString(font,"Cursor",left+210,top+132,0xFFBDB1C8,false);
        h=slot(g,left+210,top+143,cursor,mx,my);if(!h.isEmpty())hovered=h;
        g.drawString(font,"Live view",left+10,top+190,0xFFBDB1C8,false);
        super.render(g,mx,my,partial);if(!hovered.isEmpty())g.renderTooltip(font,hovered,mx,my);
    }
    @Override public boolean isPauseScreen(){return false;}
}
