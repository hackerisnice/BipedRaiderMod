package com.yourmod;

import com.yourmod.entity.CustomBipedEntity;
import com.yourmod.client.renderer.CustomBipedRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
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

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<EntityType<CustomBipedEntity>> CUSTOM_BIPED =
            ENTITIES.register("custom_biped", () -> EntityType.Builder
                    .of(CustomBipedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(64)
                    .build("custom_biped"));

    public BipedRaiderMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onAttributeCreate);
        modBus.addListener(this::registerSpawnPlacements);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                CUSTOM_BIPED.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                // Lambda + 强转，彻底绕开泛型报错
                (type, level, spawnType, pos, random) -> Monster.checkMonsterSpawnRules((EntityType) type, level, spawnType, pos, random),
                SpawnPlacementRegisterEvent.Operation.OR
        );
    }
}
