package com.yourmod;

import com.yourmod.block.HeartBlock;
import com.yourmod.entity.CustomBipedEntity;
import com.yourmod.entity.FriendlyBipedEntity;
import com.yourmod.client.renderer.CustomBipedRenderer;
import com.yourmod.client.renderer.FriendlyBipedRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod("bipedraidermod")
public class BipedRaiderMod {

    public static final String MODID = "bipedraidermod";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    
    // ★ 注册方块和物品
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Block> HEART_BLOCK = BLOCKS.register("heart_block", () -> new HeartBlock(BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));
    public static final RegistryObject<Item> HEART_BLOCK_ITEM = ITEMS.register("heart_block", () -> new BlockItem(HEART_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<EntityType<CustomBipedEntity>> CUSTOM_BIPED =
            ENTITIES.register("custom_biped", () -> EntityType.Builder
                    .of(CustomBipedEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(64)
                    .build("custom_biped"));

    public static final RegistryObject<EntityType<FriendlyBipedEntity>> FRIENDLY_BIPED =
            ENTITIES.register("friendly_biped", () -> EntityType.Builder
                    .of(FriendlyBipedEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(64)
                    .build("friendly_biped"));

    public BipedRaiderMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onAttributeCreate);
        modBus.addListener(this::registerSpawnPlacements);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        EntityRenderers.register(CUSTOM_BIPED.get(), CustomBipedRenderer::new);
        EntityRenderers.register(FRIENDLY_BIPED.get(), FriendlyBipedRenderer::new); 
    }

    private void onAttributeCreate(EntityAttributeCreationEvent event) {
        event.put(CUSTOM_BIPED.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 2.0D)
                .build());
                
        event.put(FRIENDLY_BIPED.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D) 
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 4.0D)
                .build());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                CUSTOM_BIPED.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> Monster.checkMonsterSpawnRules((EntityType) type, level, spawnType, pos, random),
                SpawnPlacementRegisterEvent.Operation.OR
        );
    }

    @Mod.EventBusSubscriber(modid = BipedRaiderMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class PetEventHandler {
        
        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                net.minecraft.server.level.ServerLevel serverLevel = player.serverLevel();
                for (net.minecraft.world.entity.Entity entity : serverLevel.getAllEntities()) {
                    if (entity instanceof FriendlyBipedEntity pet && player.getUUID().equals(pet.getOwnerUUID())) {
                        pet.teleportTo(player.getX(), player.getY(), player.getZ());
                    }
                }
            }
        }

        // ★ 核心机制：祭坛复活仪式
        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            Level level = event.getLevel();
            Player player = event.getEntity();
            BlockPos pos = event.getPos();
            ItemStack item = event.getItemStack();

            // 如果点击的是 Heart Block 且手里拿着信标
            if (level.getBlockState(pos).is(HEART_BLOCK.get()) && item.is(Items.BEACON)) {
                // 必须放在方块顶端
                if (event.getFace() == Direction.UP) {
                    if (level.isClientSide) {
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        return;
                    }

                    // 唯一性检验：遍历服务器，检查你是否已经有 Aiko 了
                    boolean alreadyHasAiko = false;
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        for (net.minecraft.world.entity.Entity e : serverLevel.getAllEntities()) {
                            if (e instanceof FriendlyBipedEntity aiko && player.getUUID().equals(aiko.getOwnerUUID())) {
                                alreadyHasAiko = true;
                                break;
                            }
                        }
                    }

                    if (alreadyHasAiko) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c你已经有一位保镖了，无法再复活更多！"));
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.FAIL);
                        return;
                    }

                    // 献祭信标
                    if (!player.isCreative()) {
                        item.shrink(1);
                    }
                    // 摧毁心脏方块 (仪式消耗)
                    level.destroyBlock(pos, false);
                    player.getPersistentData().remove("PlacedHeartBlockPos");

                    // 降临 Aiko
                    FriendlyBipedEntity aiko = FRIENDLY_BIPED.get().create(level);
                    if (aiko != null) {
                        aiko.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
                        aiko.tame(player);
                        aiko.setCustomName(net.minecraft.network.chat.Component.literal("Aiko"));
                        aiko.setCustomNameVisible(true);
                        aiko.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                        level.addFreshEntity(aiko);
                        
                        // 播放信标激活的宏大音效
                        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    }
                    
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
    }
}
