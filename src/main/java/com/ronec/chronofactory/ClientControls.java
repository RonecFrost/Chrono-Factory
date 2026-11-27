package com.ronec.chronofactory;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid=ChronoFactory.ID,value=Dist.CLIENT)
public final class ClientControls{
    private static boolean attackHeld,useHeld;
    private static int airTicks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(!mc.options.keyAttack.isDown())attackHeld=false;
        if(!mc.options.keyUse.isDown())useHeld=false;
        if(mc.player==null){attackHeld=false;useHeld=false;airTicks=0;return;}
        boolean recording=false;
        for(int i=0;i<36;i++){var stack=mc.player.getInventory().getItem(i);if(ChronoFactory.watch(stack)&&ChronoFactory.data(stack).getBoolean("recording")){recording=true;break;}}
        boolean air=recording&&mc.screen==null&&mc.options.keyAttack.isDown()&&!ChronoFactory.watch(mc.player.getMainHandItem())&&(mc.hitResult==null||mc.hitResult.getType()==net.minecraft.world.phys.HitResult.Type.MISS);
        if(air){if(airTicks++%4==0)PacketDistributor.sendToServer(ChronoFactory.AirSwing.INSTANCE);}else airTicks=0;
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e){
        var p=Minecraft.getInstance().player;
        if(p==null||!(p.getMainHandItem().getItem() instanceof ChronoFactory.Watch))return;
        if(e.isAttack()){
            e.setCanceled(true);e.setSwingHand(false);
            if(!attackHeld){attackHeld=true;PacketDistributor.sendToServer(ChronoFactory.Click.INSTANCE);}
        }else if(e.isUseItem()){
            if(Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity() instanceof EchoEntity)return;
            e.setCanceled(true);e.setSwingHand(false);
            if(!useHeld){useHeld=true;PacketDistributor.sendToServer(new ChronoFactory.UiCommand(new UUID(0,0),"open",0));}
        }
    }
    public static void show(ChronoFactory.UiState msg){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof TimekeeperScreen screen&&screen.key.equals(msg.key()))screen.update(msg.state());
        else mc.setScreen(new TimekeeperScreen(msg.key(),msg.state()));
    }
}
