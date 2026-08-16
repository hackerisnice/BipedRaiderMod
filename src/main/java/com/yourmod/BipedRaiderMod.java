package com.yourmod;

import com.yourmod.block.HeartBlock;
import com.yourmod.entity.CustomBipedEntity;
import com.yourmod.entity.FriendlyBipedEntity;
import com.yourmod.registry.ModEntities;
import com.yourmod.util.IEntityDataSaver;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class BipedRaiderMod implements ModInitializer {

    public static final String MODID = "bipedraidermod";

    public static final Block HEART_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK, ResourceLocation.parse(MODID + ":heart_block"),
            new HeartBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(3.0F))
    );
    public static final Item HEART_BLOCK_ITEM = Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation.parse(MODID + ":heart_block"),
            new BlockItem(HEART_BLOCK, new Item.Properties())
    );

    @Override
    public void onInitialize() {
        ModEntities.register();

        FabricDefaultAttributeRegistry.register(ModEntities.CUSTOM_BIPED, CustomBipedEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.FRIENDLY_BIPED, FriendlyBipedEntity.createAttributes());
        
        SpawnPlacements.register(ModEntities.CUSTOM_BIPED, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
        
        // ==========================================
        // ★ 多维度自然刷新配置
        // ==========================================
        // 1. 主世界：稀有精英怪 (权重 15)
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInOverworld(),
                MobCategory.MONSTER,
                ModEntities.CUSTOM_BIPED,
                15, 1, 1
        );

        // 2. 下界：高危遭遇怪 (权重 85，每次 1~2 只)
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInTheNether(),
                MobCategory.MONSTER,
                ModEntities.CUSTOM_BIPED,
                85, 1, 2
        );

        // 3. 末地：主场压迫怪 (权重 100，每次 1~2 只)
        BiomeModifications.addSpawn(
                BiomeSelectors.foundInTheEnd(),
                MobCategory.MONSTER,
                ModEntities.CUSTOM_BIPED,
                100, 1, 2
        );
        // ==========================================

        registerEvents();
    }

    private void registerEvents() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            net.minecraft.server.level.ServerLevel serverLevel = newPlayer.serverLevel();
            for (net.minecraft.world.entity.Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof FriendlyBipedEntity pet && newPlayer.getUUID().equals(pet.getOwnerUUID())) {
                    pet.teleportTo(newPlayer.getX(), newPlayer.getY(), newPlayer.getZ());
                }
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            ItemStack item = player.getItemInHand(hand);

            if (level.getBlockState(pos).is(HEART_BLOCK) && item.is(Items.BEACON) && hitResult.getDirection() == Direction.UP) {
                if (level.isClientSide) return InteractionResult.SUCCESS;

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
                    return InteractionResult.FAIL;
                }

                if (!player.isCreative()) item.shrink(1);

                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    for (int i = 0; i < 5; i++) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                        if (lightning != null) {
                            lightning.moveTo(Vec3.atBottomCenterOf(pos));
                            lightning.setVisualOnly(true);
                            serverLevel.addFreshEntity(lightning);
                        }
                    }
                }

                level.destroyBlock(pos, false);
                ((IEntityDataSaver) player).getPersistentData().remove("PlacedHeartBlockPos");

                FriendlyBipedEntity aiko = ModEntities.FRIENDLY_BIPED.create(level);
                if (aiko != null) {
                    aiko.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
                    aiko.tame(player);
                    aiko.setCustomName(net.minecraft.network.chat.Component.literal("Aiko"));
                    aiko.setCustomNameVisible(true);
                    aiko.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                    level.addFreshEntity(aiko);
                    level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }
}
