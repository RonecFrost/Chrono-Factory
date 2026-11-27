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
    private static boolean sentUse,sentAttack;
    private static boolean recording(Minecraft mc){if(mc.player==null)return false;for(int i=0;i<36;i++){var stack=mc.player.getInventory().getItem(i);if(ChronoFactory.watch(stack)&&ChronoFactory.data(stack).getBoolean("recording"))return true;}return false;}
    private static void input(boolean use,boolean down){if(use){if(sentUse==down)return;sentUse=down;}else{if(sentAttack==down)return;sentAttack=down;}PacketDistributor.sendToServer(new ChronoFactory.HeldInput(use,down));}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(!mc.options.keyAttack.isDown())attackHeld=false;
        if(!mc.options.keyUse.isDown())useHeld=false;
        if(mc.player==null){attackHeld=false;useHeld=false;sentAttack=false;sentUse=false;airTicks=0;return;}
        boolean recording=recording(mc);
        boolean eligible=recording&&mc.screen==null&&!ChronoFactory.watch(mc.player.getMainHandItem());
        if(recording){int flags=0;if(mc.screen==null){if(mc.options.keyUp.isDown())flags|=StrictReplay.FORWARD;if(mc.options.keyDown.isDown())flags|=StrictReplay.BACK;if(mc.options.keyLeft.isDown())flags|=StrictReplay.LEFT;if(mc.options.keyRight.isDown())flags|=StrictReplay.RIGHT;if(mc.options.keyJump.isDown())flags|=StrictReplay.JUMP;if(mc.options.keyShift.isDown())flags|=StrictReplay.SNEAK;if(mc.options.keySprint.isDown())flags|=StrictReplay.SPRINT;if(eligible&&mc.options.keyUse.isDown())flags|=StrictReplay.USE;if(eligible&&mc.options.keyAttack.isDown())flags|=StrictReplay.ATTACK;}else flags|=StrictReplay.SCREEN;
        if(eligible&&mc.options.keyDrop.isDown())flags|=StrictReplay.DROP;
        PacketDistributor.sendToServer(new ChronoFactory.InputFrame(flags,mc.player.getInventory().selected,mc.player.getYRot(),mc.player.getXRot()));}
        input(true,eligible&&mc.options.keyUse.isDown());input(false,eligible&&mc.options.keyAttack.isDown());
        boolean air=recording&&mc.screen==null&&mc.options.keyAttack.isDown()&&!ChronoFactory.watch(mc.player.getMainHandItem())&&(mc.hitResult==null||mc.hitResult.getType()==net.minecraft.world.phys.HitResult.Type.MISS);
        if(air){if(airTicks++%4==0)PacketDistributor.sendToServer(ChronoFactory.AirSwing.INSTANCE);}else airTicks=0;
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered e){
        var p=Minecraft.getInstance().player;
        if(p==null)return;
        if(!(p.getMainHandItem().getItem() instanceof ChronoFactory.Watch)){
            if(recording(Minecraft.getInstance())){if(e.isUseItem())input(true,true);if(e.isAttack())input(false,true);}
            return;
        }
        if(e.isAttack()){
            e.setCanceled(true);e.setSwingHand(false);
            if(!attackHeld){attackHeld=true;PacketDistributor.sendToServer(ChronoFactory.Click.INSTANCE);}
        }else if(e.isUseItem()){
            if(Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.BlockHitResult block&&Minecraft.getInstance().level.getBlockState(block.getBlockPos()).is(TemporalRelay.BLOCK.get()))return;
            if(Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity() instanceof EchoEntity)return;
            e.setCanceled(true);e.setSwingHand(false);
            if(!useHeld){useHeld=true;PacketDistributor.sendToServer(new ChronoFactory.UiCommand(new UUID(0,0),"open",0));}
        }
    }
    public static void relay(TemporalRelay.State msg){
        Minecraft mc=Minecraft.getInstance();if(mc.screen instanceof RelayScreen screen&&screen.pos.equals(msg.pos()))screen.update(msg.state());else if(msg.open())mc.setScreen(new RelayScreen(msg.pos(),msg.state()));
    }
    public static void inspect(ChronoFactory.InspectState msg){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof EchoInventoryScreen screen&&screen.getMenu().key.equals(msg.key()))screen.update(msg.state());
        else if(mc.screen instanceof EchoInspectScreen screen&&screen.key.equals(msg.key()))screen.update(msg.state());
        else if(msg.open())mc.setScreen(new EchoInspectScreen(msg.key(),msg.state()));
    }
    public static void show(ChronoFactory.UiState msg){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof TimekeeperScreen screen&&screen.key.equals(msg.key()))screen.update(msg.state());
        else mc.setScreen(new TimekeeperScreen(msg.key(),msg.state()));
    }
}
