package com.ronec.chronofactory.mixin;
import com.ronec.chronofactory.ChronoFactory;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ContainerInputMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleContainerClick",at=@At("HEAD"))
    private void chronoClick(ServerboundContainerClickPacket packet,CallbackInfo ci){
        if(player.server.isSameThread()&&packet.getContainerId()==player.containerMenu.containerId)
            ChronoFactory.rawGui(player,packet.getSlotNum(),packet.getButtonNum(),packet.getClickType().ordinal(),false);
    }
    @Inject(method="handleContainerClick",at=@At("RETURN"))
    private void chronoAfterClick(ServerboundContainerClickPacket packet,CallbackInfo ci){
        if(player.server.isSameThread()&&packet.getContainerId()==player.containerMenu.containerId)ChronoFactory.afterGui(player);
    }
    @Inject(method="handleContainerButtonClick",at=@At("HEAD"))
    private void chronoButton(net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket packet,CallbackInfo ci){
        if(player.server.isSameThread()&&packet.containerId()==player.containerMenu.containerId)ChronoFactory.rawMenuButton(player,packet.buttonId());
    }
    @Inject(method="handleContainerButtonClick",at=@At("RETURN"))
    private void chronoAfterButton(net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket packet,CallbackInfo ci){
        if(player.server.isSameThread())ChronoFactory.afterMenuButton(player);
    }
    @Inject(method="handlePlayerAction",at=@At("HEAD"))
    private void chronoRelease(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket packet,CallbackInfo ci){
        if(player.server.isSameThread()&&packet.getAction()==net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM)ChronoFactory.nativeRelease(player);
    }
    @Inject(method="handleContainerClose",at=@At("HEAD"))
    private void chronoClose(ServerboundContainerClosePacket packet,CallbackInfo ci){
        if(player.server.isSameThread())ChronoFactory.rawGui(player,0,0,0,true);
    }
}
