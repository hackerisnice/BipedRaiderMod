package com.yourmod.entity;

import com.yourmod.BipedRaiderMod;
import com.yourmod.entity.ai.CompanionCombatGoal;
import com.yourmod.entity.ai.CompanionEatAppleGoal;
import com.yourmod.entity.ai.CompanionFollowPearlGoal;
import com.yourmod.entity.ai.CompanionHandleCrystalGoal;
import com.yourmod.entity.ai.WalkBridgeGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

public class FriendlyBipedEntity extends TamableAnimal {

    @Nullable
    private ItemStack savedMainHandItem = null;
    private BlockPos placedWaterPos = null;
    private int waterPickupTimer = 0;

    public FriendlyBipedEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ARMOR, 4.0D);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null; 
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        
        // ★ 赋予 Aiko 平地阶梯走搭的能力！
        this.goalSelector.addGoal(1, new WalkBridgeGoal(this, 0.35D)); 
        
        this.goalSelector.addGoal(2, new CompanionFollowPearlGoal(this));
        this.goalSelector.addGoal(3, new CompanionEatAppleGoal(this));
        this.goalSelector.addGoal(4, new CompanionHandleCrystalGoal(this));
        this.goalSelector.addGoal(5, new CompanionCombatGoal(this, 1.5D));
        this.goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.2D, 5.0F, 2.0F));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 64.0F, 1.0F));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (target) -> {
            if (target instanceof EnderMan) return false;
            if (!(target instanceof Enemy)) return false;
            LivingEntity owner = this.getOwner();
            if (owner == null) return false;
            if (target instanceof EnderDragon) {
                return target.distanceToSqr(owner) <= 4096.0D;
            }
            return target.distanceToSqr(owner) <= 576.0D;
        }));
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof FriendlyBipedEntity || target instanceof Player) {
            return false;
        }
        return super.wantsToAttack(target, owner);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player || source.getEntity() instanceof FriendlyBipedEntity) {
            return false; 
        }
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (placedWaterPos != null || waterPickupTimer > 0) {
            return false; 
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
    }

    @Override
    public boolean startRiding(net.minecraft.world.entity.Entity vehicle, boolean force) {
        if (vehicle instanceof net.minecraft.world.entity.vehicle.Boat || vehicle instanceof net.minecraft.world.entity.vehicle.Minecart) {
            vehicle.discard(); 
            this.level().playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
            return false;
        }
        return super.startRiding(vehicle, force);
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity owner = this.getOwner();

        if (owner != null && !this.level().isClientSide && owner.isAlive()) {
            if (this.level() == owner.level() && this.distanceToSqr(owner) > 1024.0D) {
                this.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                this.getNavigation().stop();
            }
        }

        if (!this.level().isClientSide) {
            
            LivingEntity target = this.getTarget();
            if (target != null && target.isAlive()) {
                double dx = target.getX() - this.getX();
                double dy = target.getEyeY() - this.getEyeY();
                double dz = target.getZ() - this.getZ();
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                
                float targetYaw = (float)(Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
                float targetPitch = (float)(-(Math.atan2(dy, horizDist) * (180.0 / Math.PI)));
                
                this.setYRot(targetYaw);
                this.setXRot(targetPitch);
                this.yHeadRot = targetYaw;
                this.yBodyRot = targetYaw;
            } else if (owner != null) {
                this.getLookControl().setLookAt(owner, 30.0F, 30.0F);
            }

            java.util.List<net.minecraft.world.entity.vehicle.Boat> boats = this.level().getEntitiesOfClass(net.minecraft.world.entity.vehicle.Boat.class, this.getBoundingBox().inflate(1.5D));
            for (net.minecraft.world.entity.vehicle.Boat boat : boats) {
                boat.discard();
                this.level().playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
            }

            if (placedWaterPos != null) {
                waterPickupTimer--;
                if (waterPickupTimer <= 0 || this.onGround() || this.isInWater()) {
                    if (this.level().getBlockState(placedWaterPos).is(Blocks.WATER)) {
                        this.level().setBlock(placedWaterPos, Blocks.AIR.defaultBlockState(), 3);
                        this.restoreMainHandItem(); 
                    }
                    placedWaterPos = null;
                }
            }

            if (this.fallDistance > 3.0f && !this.onGround() && placedWaterPos == null) {
                for (int i = 1; i <= 4; i++) {
                    BlockPos checkPos = this.blockPosition().below(i);
                    if (this.level().getBlockState(checkPos).blocksMotion()) {
                        BlockPos waterPos = checkPos.above();
                        if (this.level().getBlockState(waterPos).canBeReplaced()) {
                            if (this.isUsingItem()) this.releaseUsingItem();
                            this.switchMainHandItem(new ItemStack(Items.WATER_BUCKET));
                            this.level().setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
                            placedWaterPos = waterPos;
                            waterPickupTimer = 30; 
                            break;
                        }
                    }
                }
            }
        }
    }

    public void switchMainHandItem(ItemStack newItem) {
        if (savedMainHandItem == null) {
            savedMainHandItem = this.getMainHandItem().copy();
        }
        this.setItemInHand(InteractionHand.MAIN_HAND, newItem);
    }

    public void restoreMainHandItem() {
        if (savedMainHandItem != null) {
            this.setItemInHand(InteractionHand.MAIN_HAND, savedMainHandItem.copy());
            savedMainHandItem = null;
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide) {
            this.spawnAtLocation(BipedRaiderMod.HEART_BLOCK_ITEM);
        }
        super.die(cause);
    }
}
