package com.ronec.chronofactory;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid=ChronoFactory.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class EchoRenderer extends EntityRenderer<EchoEntity>{
    private final PlayerModel<EchoEntity> wide,slim;
    private final net.minecraft.client.renderer.ItemInHandRenderer heldRenderer;
    public EchoRenderer(EntityRendererProvider.Context ctx){super(ctx);heldRenderer=ctx.getItemInHandRenderer();wide=new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER),false);slim=new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM),true);shadowRadius=0;}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ChronoFactory.ECHO.get(),EchoRenderer::new);}
    private PlayerSkin skin(EchoEntity e){
        Minecraft mc=Minecraft.getInstance();
        var connection=mc.getConnection();
        if(connection!=null){var info=connection.getPlayerInfo(e.ownerId());if(info!=null)return info.getSkin();}
        return DefaultPlayerSkin.get(e.ownerId());
    }
    @Override public ResourceLocation getTextureLocation(EchoEntity e){return skin(e).texture();}
    @Override public void render(EchoEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        int phase=e.phase();float fade=e.smoothFade(partial);if(fade<=10)return;
        float alpha=.68f*Mth.clamp((fade-10)/14f,0f,1f);
        PlayerSkin skin=skin(e);PlayerModel<EchoEntity> model=skin.model()==PlayerSkin.Model.SLIM?slim:wide;
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-yaw));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        model.setAllVisible(true);model.young=false;model.riding=false;model.crouching=e.isShiftKeyDown();
        e.swingingArm=e.offSwing()?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND;
        model.rightArmPose=e.blockingVisual()&&!e.offSwing()?net.minecraft.client.model.HumanoidModel.ArmPose.BLOCK:e.visualHeld().isEmpty()?net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY:net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        model.leftArmPose=e.blockingVisual()&&e.offSwing()?net.minecraft.client.model.HumanoidModel.ArmPose.BLOCK:e.visualOff().isEmpty()?net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY:net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        model.attackTime=e.smoothSwing(partial);
        model.setupAnim(e,e.smoothWalk(partial),e.smoothStride(partial),e.smoothAge(partial),0,e.smoothLook(partial));
        int tint=ChronoFactory.TINTS[Math.floorMod(e.tint(),ChronoFactory.TINTS.length)];
        int color=((int)(alpha*255)<<24)|tint;
        // Intermittent limb desync, with actual transparent sections rather than painted patches.
        if(phase==24&&!e.walking()&&e.smoothStride(partial)<.05f&&((e.tickCount/5+e.getId())%47==0)){model.leftArm.visible=false;model.leftSleeve.visible=false;}
        if(phase==24&&!e.walking()&&e.smoothStride(partial)<.05f&&((e.tickCount/5+e.getId())%61==0)){model.rightLeg.visible=false;model.rightPants.visible=false;}
        model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityTranslucent(skin.texture())),light,OverlayTexture.NO_OVERLAY,color);
        if(phase==24&&!e.visualHeld().isEmpty()&&model.rightArm.visible){
            pose.pushPose();model.translateToHand(HumanoidArm.RIGHT,pose);pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate(1f/16f,.125,-.625);
            heldRenderer.renderItem(e,e.visualHeld(),ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,false,pose,buffers,light);
            pose.popPose();
        }
        if(phase==24&&!e.visualOff().isEmpty()&&model.leftArm.visible){pose.pushPose();model.translateToHand(HumanoidArm.LEFT,pose);pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate(-1f/16f,.125,-.625);heldRenderer.renderItem(e,e.visualOff(),ItemDisplayContext.THIRD_PERSON_LEFT_HAND,true,pose,buffers,light);pose.popPose();}
        pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
    }
}
