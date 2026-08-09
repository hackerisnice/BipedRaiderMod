package com.yourmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yourmod.BipedRaiderMod;
import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public class CustomBipedRenderer extends HumanoidMobRenderer<CustomBipedEntity, HumanoidModel<CustomBipedEntity>> {

    // ★ 材质路径：请确保你的贴图文件在 src/main/resources/assets/bipedraidermod/textures/entity/custom_biped.png
    private static final ResourceLocation TEXTURE = ResourceLocation.parse(BipedRaiderMod.MODID + ":textures/entity/custom_biped.png");

    public CustomBipedRenderer(EntityRendererProvider.Context context) {
        // 使用原版标准的双足模型 (ZOMBIE 模型是通用的标准大臂模型，完美契合玩家动作)
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        
        // ★ 添加盔甲渲染层，如果你的 Boss 穿了装备，也能完美显示
        this.addLayer(new HumanoidArmorLayer<>(this, 
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)), 
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), 
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(CustomBipedEntity entity) {
        return TEXTURE;
    }

    // ★ 核心动画接管：动态姿势渲染
    @Override
    public void render(CustomBipedEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 重置双臂状态
        this.model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        this.model.leftArmPose = HumanoidModel.ArmPose.EMPTY;

        // 识别主手（右臂）动作
        ItemStack mainHand = entity.getMainHandItem();
        if (!mainHand.isEmpty()) {
            this.model.rightArmPose = HumanoidModel.ArmPose.ITEM;
            if (entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                UseAnim anim = mainHand.getUseAnimation();
                if (anim == UseAnim.BLOCK) {
                    this.model.rightArmPose = HumanoidModel.ArmPose.BLOCK; // 举盾
                } else if (anim == UseAnim.BOW) {
                    this.model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW; // 拉弓
                } else if (anim == UseAnim.CROSSBOW) {
                    this.model.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD; // 持弩
                }
            }
        }

        // 识别副手（左臂）动作
        ItemStack offHand = entity.getOffhandItem();
        if (!offHand.isEmpty()) {
            this.model.leftArmPose = HumanoidModel.ArmPose.ITEM;
            if (entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.OFF_HAND) {
                UseAnim anim = offHand.getUseAnimation();
                if (anim == UseAnim.BLOCK) {
                    this.model.leftArmPose = HumanoidModel.ArmPose.BLOCK; // 副手举盾
                } else if (anim == UseAnim.BOW) {
                    this.model.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                } else if (anim == UseAnim.CROSSBOW) {
                    this.model.leftArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
                }
            }
        }

        // 调用父类进行最终渲染
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
