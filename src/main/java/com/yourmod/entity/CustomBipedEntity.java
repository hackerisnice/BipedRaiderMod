package com.yourmod.entity;

import com.yourmod.BipedRaiderMod;
import com.yourmod.entity.ai.*;
import com.yourmod.util.IEntityDataSaver;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 2.0D);
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

        // ★ 视觉增强：只要腾空，脚底产生 1.21 风弹粒子拖尾
        if (this.level().isClientSide && !this.onGround()) {
            for (int i = 0; i < 2; i++) {
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.GUST,
                        this.getX() + (this.random.nextDouble() - 0.5) * 0.8,
                        this.getY() + this.random.nextDouble() * 0.5,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.8,
                        0.0, -0.1, 0.0);
            }
        }
        
        if (!this.level().isClientSide && this.isAlive()) {
            
            // ★ 镜头逻辑重构：绝对自瞄！让 Replay Mod 拍出来的视角完美无瑕
            if (this.getTarget() != null) {
                LivingEntity target = this.getTarget();
                // 计算三维向量
                double dx = target.getX() - this.getX();
                double dy = target.getEyeY() - this.getEyeY(); // 锁定敌方眼睛
                double dz = target.getZ() - this.getZ();
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                
                // 将向量转换为原版 Minecraft 的 Yaw 和 Pitch
                float targetYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                float targetPitch = (float)(-(Math.atan2(dy, horizDist) * (180.0 / Math.PI)));
                
                // 瞬间锁死全身骨骼
                this.setYRot(targetYaw);
                this.setXRot(targetPitch);
                this.yHeadRot = targetYaw;
                this.yBodyRot = targetYaw;
            }

            // 保留反制逃课的碎船机制
            List<Boat> boats = this.level().getEntitiesOfClass(Boat.class, this.getBoundingBox().inflate(1.5D));
            for (Boat boat : boats) {
                boat.discard();
                this.level().playSound(null, this.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 1.0F, 1.0F);
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
                    if (stack.is(BipedRaiderMod.HEART_BLOCK_ITEM)) {
                        hasItem = true;
                        break;
                    }
                }
                boolean hasPlaced = false;
                long posLong = ((IEntityDataSaver) player).getPersistentData().getLong("PlacedHeartBlockPos");
                if (posLong != 0) {
                    BlockPos p = BlockPos.of(posLong);
                    if (player.level().getBlockState(p).is(BipedRaiderMod.HEART_BLOCK)) {
                        hasPlaced = true;
                    } else {
                        ((IEntityDataSaver) player).getPersistentData().remove("PlacedHeartBlockPos");
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
                    this.spawnAtLocation(BipedRaiderMod.HEART_BLOCK_ITEM);
                }
            } else {
                this.spawnAtLocation(BipedRaiderMod.HEART_BLOCK_ITEM);
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

    public boolean canEatApple() { return goldenApplesEaten < MAX_APPLES; }
    public void consumeApple() { goldenApplesEaten++; }
    public void switchMainHandItem(ItemStack newItem) { this.setItemInHand(InteractionHand.MAIN_HAND, newItem); }
    public void restoreMainHandItem() { this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD)); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GoldenApplesEaten", this.goldenApplesEaten);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("GoldenApplesEaten")) this.goldenApplesEaten = tag.getInt("GoldenApplesEaten");
    }
}
