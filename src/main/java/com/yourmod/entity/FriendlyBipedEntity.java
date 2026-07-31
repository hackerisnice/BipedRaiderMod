package com.yourmod.entity;

import com.yourmod.entity.ai.CompanionCombatGoal;
import com.yourmod.entity.ai.CompanionEatAppleGoal;
import com.yourmod.entity.ai.CompanionFollowPearlGoal;
import com.yourmod.entity.ai.CompanionShootCrystalGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
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
        
        // ★ 优先级 3：打龙前优先射爆视线内的水晶
        this.goalSelector.addGoal(3, new CompanionShootCrystalGoal(this));
        // 优先级 4：普通战斗与打龙
        this.goalSelector.addGoal(4, new CompanionCombatGoal(this, 1.5D));
        
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.2D, 5.0F, 2.0F));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 64.0F, 1.0F));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        
        // ★ 核心改动：重构索敌逻辑 (使用 Mob.class 取代 Monster.class，精准过滤)
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, (target) -> {
            // 绝对不主动招惹末影人
            if (target instanceof EnderMan) return false;
            
            // 必须是敌对生物 (Enemy 接口包含了所有僵尸骷髅以及末影龙)
            if (!(target instanceof Enemy)) return false;

            LivingEntity owner = this.getOwner();
            if (owner == null) return false;
            
            // 如果是末影龙，视野放大到 64 格 (4096)
            if (target instanceof EnderDragon) {
                return target.distanceToSqr(owner) <= 4096.0D;
            }
            
            // 普通怪物，限制在 24 格内防走丢
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

    // ★ 终极保险：只要正在进行落地水判定，直接底层豁免掉落伤害
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (placedWaterPos != null || waterPickupTimer > 0) {
            return false; 
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
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

        if (this.getTarget() == null && owner != null) {
            this.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        }

        // ★ 进阶落地水逻辑：扩大预判范围，极速下坠必不死
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

            if (this.fallDistance > 3.0f && !this.onGround() && placedWaterPos == null) {
                // 预判脚下 1 到 4 格，只要碰到实心方块立刻泼水
                for (int i = 1; i <= 4; i++) {
                    BlockPos checkPos = this.blockPosition().below(i);
                    if (this.level().getBlockState(checkPos).blocksMotion()) {
                        BlockPos waterPos = checkPos.above();
                        if (this.level().getBlockState(waterPos).canBeReplaced()) {
                            this.releaseUsingItem();
                            this.switchMainHandItem(new ItemStack(Items.WATER_BUCKET));
                            this.level().setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
                            placedWaterPos = waterPos;
                            waterPickupTimer = 30; // 停留 1.5 秒
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
}
