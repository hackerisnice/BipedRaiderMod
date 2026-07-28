package com.yourmod.registry;

// 1. 导入你的主类，以便访问 BipedRaiderMod.CUSTOM_BIPED
import com.yourmod.BipedRaiderMod;

// 2. 导入你的自定义实体类
import com.yourmod.entity.CustomBipedEntity;

// 3. 导入 Minecraft 原版的 EntityType
import net.minecraft.world.entity.EntityType;

// 4. 导入 Forge 的 RegistryObject
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    
    public static final RegistryObject<EntityType<CustomBipedEntity>> CUSTOM_BIPED = BipedRaiderMod.CUSTOM_BIPED;

    // 如果你有其他的实体注册代码，可以继续写在这里
}
