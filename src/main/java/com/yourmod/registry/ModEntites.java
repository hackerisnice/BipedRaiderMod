package com.yourmod.registry;

import com.yourmod.BipedRaiderMod;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.RegistryObject;
// 可在此集中管理所有实体引用，便于其他类调用
public class ModEntities {
    public static final RegistryObject<EntityType<CustomBipedEntity>> CUSTOM_BIPED = BipedRaiderMod.CUSTOM_BIPED;
}
