package com.yourmod;

import com.yourmod.block.HeartBlock;
import com.yourmod.entity.CustomBipedEntity;
import com.yourmod.entity.FriendlyBipedEntity;
import com.yourmod.util.IEntityDataSaver;
import net.fabricmc.api.ModInitializer;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class BipedRaiderMod implements ModInitializer {

    public static final String MODID = "bipedraidermod";

    // 1.21 注册方块和物品
    public static final Block HEART_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK, ResourceLocation.parse(MODID + ":heart_block"),
            new HeartBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(3.0F))
    );
    public static final Item HEART_BLOCK_ITEM = Registry.register(
            BuiltInRegistries.ITEM, ResourceLocation.parse(MODID + ":heart_block"),
            new BlockItem(HEART_BLOCK, new Item.Properties())
    );

    // 1.21 注册实体
    public static final EntityType<CustomBipedEntity> CUSTOM_BIPED = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.parse(MODID + ":custom_biped"),
            EntityType.Builder.of(CustomBipedEntity::new, MobCategory.MONSTER).sized(0.6F, 1.8F).clientTrackingRange(64).build("custom_biped")
    );
    public static final EntityType<FriendlyBipedEntity> FRIENDLY_BIPED = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, ResourceLocation.parse(MODID + ":friendly_biped"),
            EntityType.Builder.of(FriendlyBipedEntity::new, MobCategory.CREATURE).sized(0.6F, 1.8F).clientTrackingRange(64).build("friendly_biped")
    );

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(CUSTOM_BIPED, CustomBipedEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FRIENDLY_BIPED, FriendlyBipedEntity.createAttributes());
        SpawnPlacements.register(CUSTOM_BIPED, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
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

                FriendlyBipedEntity aiko = FRIENDLY_BIPED.create(level);
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
