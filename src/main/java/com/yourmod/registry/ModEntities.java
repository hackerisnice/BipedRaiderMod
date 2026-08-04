package com.yourmod.registry;

import com.yourmod.BipedRaiderMod;
import com.yourmod.entity.CustomBipedEntity;
import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {

    public static final EntityType<CustomBipedEntity> CUSTOM_BIPED = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, 
            ResourceLocation.parse(BipedRaiderMod.MODID + ":custom_biped"),
            EntityType.Builder.of(CustomBipedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(64)
                    .build("custom_biped")
    );

    public static final EntityType<FriendlyBipedEntity> FRIENDLY_BIPED = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, 
            ResourceLocation.parse(BipedRaiderMod.MODID + ":friendly_biped"),
            EntityType.Builder.of(FriendlyBipedEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(64)
                    .build("friendly_biped")
    );

    // 调用此方法以触发类的静态初始化
    public static void register() {}
}
