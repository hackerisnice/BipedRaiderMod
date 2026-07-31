package com.yourmod.entity;

import com.yourmod.entity.ai.CompanionCombatGoal;
import com.yourmod.entity.ai.CompanionEatAppleGoal;
import com.yourmod.entity.ai.CompanionFollowPearlGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
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
        this.goalSelector.addGoal(1, new CompanionFollowPearlGoal(this));
        this.goalSelector.addGoal(2, new CompanionEatAppleGoal(this));
        this.goalSelector.addGoal(3, new CompanionCombatGoal(this, 1.5D));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.2D, 5.0F, 2.0F));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 64.0F, 1.0F));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, (target) -> {
            LivingEntity owner = this.getOwner();
            if (owner == null) return false;
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
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();

        LivingEntity owner = this.getOwner();

        // ★ 新增：极限防走丢机制 (距离 > 32 格时瞬间传送)
        if (owner != null && !this.level().isClientSide && owner.isAlive()) {
            if (this.level() == owner.level() && this.distanceToSqr(owner) > 1024.0D) {
                this.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                this.getNavigation().stop();
            }
        }

        if (this.getTarget() == null && owner != null) {
            this.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        }

        if (!this.level().isClientSide) {
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

            if (this.fallDistance > 3.5f && !this.onGround() && placedWaterPos == null) {
                BlockPos posBelow = this.blockPosition().below(2);
                if (this.level().getBlockState(posBelow).blocksMotion() || 
                    this.level().getBlockState(posBelow.above()).blocksMotion()) {
                    
                    BlockPos waterPos = this.blockPosition();
                    if (this.level().getBlockState(waterPos).canBeReplaced()) {
                        this.releaseUsingItem();
                        this.switchMainHandItem(new ItemStack(Items.WATER_BUCKET));
                        this.level().setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
                        placedWaterPos = waterPos;
                        waterPickupTimer = 20; 
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
}
