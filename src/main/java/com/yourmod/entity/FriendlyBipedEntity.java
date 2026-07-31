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
        
        // 优先级 1：极远距离防走丢，投掷珍珠
        this.goalSelector.addGoal(1, new CompanionFollowPearlGoal(this));
        
        // ★ 优先级 2：保命第一，残血时无限制吃附魔金苹果
        this.goalSelector.addGoal(2, new CompanionEatAppleGoal(this));
        
        // ★ 优先级 3：战斗系统。优先级高于跟随，意味着一旦开打，必须打完才会回头
        this.goalSelector.addGoal(3, new CompanionCombatGoal(this, 1.5D));
        
        // ★ 优先级 4：日常贴身跟随。只有在非战斗状态下（目标为 null 时），才会因为距离主人太远而触发
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.2D, 5.0F, 2.0F));
        
        // 优先级 5及以下：日常闲逛与看主人
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 64.0F, 1.0F));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));

        // ================= 目标锁定 AI =================
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        
        // 索敌范围限制：不会跑太远去主动挑衅怪物
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, (target) -> {
            LivingEntity owner = this.getOwner();
            if (owner == null) return false;
            return target.distanceToSqr(owner) <= 576.0D;
        }));
    }

    @Override
    public void tick() {
        super.tick();

        // 空闲时脑袋强制盯住主人
        if (this.getTarget() == null && this.getOwner() != null) {
            this.getLookControl().setLookAt(this.getOwner(), 30.0F, 30.0F);
        }

        // 落地水机制
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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player) {
            return false; 
        }
        return super.hurt(source, amount);
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
