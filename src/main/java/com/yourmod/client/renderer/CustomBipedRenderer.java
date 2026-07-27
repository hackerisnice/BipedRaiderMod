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

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(BipedRaiderMod.MODID, "textures/entity/custom_biped.png");

    public CustomBipedRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        // 可添加盔甲层，但皮肤为空白，通常省略
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(CustomBipedEntity entity) {
        return TEXTURE; // 指向 assets/yourmod/textures/entity/custom_biped.png 空白皮肤
    }
}
