package com.yourmod.client.renderer;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class FriendlyBipedRenderer extends HumanoidMobRenderer<FriendlyBipedEntity, HumanoidModel<FriendlyBipedEntity>> {

    public FriendlyBipedRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(FriendlyBipedEntity entity) {
        // 复用之前的材质
        return ResourceLocation.fromNamespaceAndPath("bipedraidermod", "textures/entity/custom_biped.png");
    }
}
