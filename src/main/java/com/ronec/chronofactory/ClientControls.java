package com.ronec.chronofactory;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid=ChronoFactory.ID,value=Dist.CLIENT)
public final class ClientControls{
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e){
        var p=Minecraft.getInstance().player;
        if(p!=null&&e.isAttack()&&p.getMainHandItem().getItem() instanceof ChronoFactory.Watch){
            e.setCanceled(true);e.setSwingHand(false);PacketDistributor.sendToServer(ChronoFactory.Click.INSTANCE);
        }
    }
}
