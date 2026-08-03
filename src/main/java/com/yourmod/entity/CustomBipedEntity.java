package com.yourmod.entity;

import com.yourmod.BipedRaiderMod;
import com.yourmod.entity.ai.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.DifficultyInstance;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CustomBipedEntity extends Monster {

    private int goldenApplesEaten = 0;
    private static final int MAX_APPLES = 5;

    public CustomBipedEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 multiplier) {
        if (!state.is(Blocks.COBWEB)) {
            super.makeStuckInBlock(state, multiplier);
        }
    }

    @Override
    public boolean startRiding(net.minecraft.world.entity.Entity vehicle, boolean force) {
        if (vehicle instanceof Boat || vehicle instanceof Minecart) {
            vehicle.discard(); 
            this.level().playSound(null, this.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 1.0F);
            return false;
        }
        return super.startRiding(vehicle, force);
    }

    @Override
    public void tick() {
        super.tick();
        
        if (!this.level().isClientSide && this.isAlive()) {
            List<Boat> boats = this.level().getEntitiesOfClass(Boat.class, this.getBoundingBox().inflate(1.5D));
            for (Boat boat : boats) {
                boat.discard();
                this.level().playSound(null, this.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 1.0F, 1.0F);
            }

            // ★ 核心修复：把下半身的判定点从脚底（腿部）抬高到腰部（Y + 0.5）
            // 这样它搭方块时，踩在脚底的圆石就不会被判定为“卡住自己”而遭到粉碎了。
            BlockPos headPos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            BlockPos waistPos = BlockPos.containing(this.getX(), this.getY() + 0.5, this.getZ());
            
            if (this.level().getBlockState(headPos).blocksMotion()) {
                this.level().destroyBlock(headPos, true, this); 
            }
            if (this.level().getBlockState(waistPos).blocksMotion()) {
                this.level().destroyBlock(waistPos, true, this); 
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide) {
            Player player = this.level().getNearestPlayer(this, 16.0D);
            if (player != null) {
                
                boolean hasItem = false;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.is(BipedRaiderMod.HEART_BLOCK_ITEM.get())) {
                        hasItem = true;
                        break;
                    }
                }
                
                boolean hasPlaced = false;
                long posLong = player.getPersistentData().getLong("PlacedHeartBlockPos");
                if (posLong != 0) {
                    BlockPos p = BlockPos.of(posLong);
                    if (player.level().getBlockState(p).is(BipedRaiderMod.HEART_BLOCK.get())) {
                        hasPlaced = true;
                    } else {
                        player.getPersistentData().remove("PlacedHeartBlockPos");
                    }
                }

                boolean hasAiko = false;
                if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    for (net.minecraft.world.entity.Entity e : serverLevel.getAllEntities()) {
                        if (e instanceof FriendlyBipedEntity aiko && player.getUUID().equals(aiko.getOwnerUUID())) {
                            hasAiko = true;
                            break;
                        }
                    }
                }

                if (hasItem || hasPlaced || hasAiko) {
                    this.spawnAtLocation(Items.BEACON);
                } else {
                    this.spawnAtLocation(BipedRaiderMod.HEART_BLOCK_ITEM.get());
                }
            } else {
                this.spawnAtLocation(BipedRaiderMod.HEART_BLOCK_ITEM.get());
            }
        }
        
        super.die(cause);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EatEnchantedGoldenAppleGoal(this));
        this.goalSelector.addGoal(2, new EscapeBoatAndBuildUpGoal(this, 1.2D));
        this.goalSelector.addGoal(3, new LavaTrapGoal(this));
        this.goalSelector.addGoal(4, new HostileCobwebTrapGoal(this));
        this.goalSelector.addGoal(5, new AxeBreakShieldGoal(this));
        this.goalSelector.addGoal(6, new ThrowHarmingPotionAtFeetGoal(this));
        this.goalSelector.addGoal(7, new BreakBlockToReachTargetGoal(this));
        this.goalSelector.addGoal(8, new EnderPearlTeleportGoal(this));
        this.goalSelector.addGoal(9, new HostileAdvancedCombatGoal(this, 1.5D));
        this.goalSelector.addGoal(10, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(11, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(12, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public boolean canEatApple() {
        return goldenApplesEaten < MAX_APPLES;
    }

    public void consumeApple() {
        goldenApplesEaten++;
    }

    public void switchMainHandItem(ItemStack newItem) {
        this.setItemInHand(InteractionHand.MAIN_HAND, newItem);
    }

    public void restoreMainHandItem() {
        this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GoldenApplesEaten", this.goldenApplesEaten);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GoldenApplesEaten")) {
            this.goldenApplesEaten = tag.getInt("GoldenApplesEaten");
        }
    }
}
