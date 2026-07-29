package com.yourmod;

import com.yourmod.entity.CustomBipedEntity;
import com.yourmod.client.renderer.CustomBipedRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod("bipedraidermod")
public class BipedRaiderMod {

    public static final String MODID = "bipedraidermod";

    // 实体类型注册
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<CustomBipedEntity>> CUSTOM_BIPED =
            ENTITIES.register("custom_biped", () -> EntityType.Builder
                    .of(CustomBipedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F) // 标准玩家尺寸
                    .clientTrackingRange(64)
                    .build("custom_biped"));

    public BipedRaiderMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onAttributeCreate);
        // 新增：注册生成规则事件
        modBus.addListener(this::registerSpawnPlacements);
        
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // 绑定渲染器（空白玩家皮肤）
        EntityRenderers.register(CUSTOM_BIPED.get(), CustomBipedRenderer::new);
    }

    private void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(CUSTOM_BIPED.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 2.0D)
                .build());
    }

    // 新增：配置实体生成环境（地面、不包含树叶、原版怪物亮度规则）
    private void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                CUSTOM_BIPED.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                SpawnPlacementRegisterEvent.Operation.OR
        );
    }
}
