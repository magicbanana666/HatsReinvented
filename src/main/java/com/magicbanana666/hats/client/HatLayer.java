package com.magicbanana666.hats.client;

import com.magicbanana666.bananautil.client.head.HeadLocator;
import com.magicbanana666.hats.common.HatsConfig;
import com.magicbanana666.hats.common.hat.HatPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.context.ContextKey;

/**
 * Puts the hat on top of the head of any living entity (the head is found by BananaUtil's HeadLocator).
 */
public class HatLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M>
{
    public static final ContextKey<HatPart> HAT_KEY = new ContextKey<>(com.magicbanana666.hats.HatsReinvented.id("hat"));

    /** Hat scale relative to a bare player head: hats were made to fit over a helmet. */
    private static final float ARMOR_SCALE = 1.27F;
    private static final float ARMOR_OFFSET = 1F / 16F;

    public static int renderCount;

    public HatLayer(RenderLayerParent<S, M> renderer)
    {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot)
    {
        HatPart hat = state.getRenderData(HAT_KEY);
        if(hat == null || !hat.isAHat() || hat.count <= 0 || state.isInvisible || !HatsConfig.client(HatsConfig.renderHats, true))
        {
            return;
        }
        boolean isPlayer = state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState;
        if(!isPlayer && renderCount >= HatsConfig.client(HatsConfig.maxHatRenders, 300))
        {
            return;
        }
        M model = getParentModel();
        HeadLocator.Head head = HeadLocator.find(model);
        if(head == null)
        {
            return;
        }

        poseStack.pushPose();
        model.root().translateAndRotate(poseStack);
        head.translateToHead(poseStack);
        head.translateToTop(poseStack);
        poseStack.translate(0F, -ARMOR_OFFSET * head.scale() - 0.00225F, 0F);
        float scale = head.scale() * ARMOR_SCALE * 1.005F;
        poseStack.scale(scale, scale, scale);
        if(HatRenderer.render(hat, poseStack, collector, lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0F), state.outlineColor))
        {
            renderCount++;
        }
        poseStack.popPose();
    }
}
