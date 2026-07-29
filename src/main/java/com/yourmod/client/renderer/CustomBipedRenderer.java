package com.yourmod.client.renderer;

import com.yourmod.BipedRaiderMod;
import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

public class CustomBipedRenderer extends HumanoidMobRenderer<CustomBipedEntity, HumanoidModel<CustomBipedEntity>> {

    public CustomBipedRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(CustomBipedEntity entity) {
        // 直接硬编码 bipedraidermod，确保路径绝对读取为：
        // assets/bipedraidermod/textures/entity/custom_biped.png
        return ResourceLocation.fromNamespaceAndPath("bipedraidermod", "textures/entity/custom_biped.png");
    }
}
