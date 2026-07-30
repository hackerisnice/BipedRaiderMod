package com.yourmod.entity;

import com.yourmod.entity.ai.CompanionCombatGoal;
import com.yourmod.entity.ai.CompanionFollowPearlGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
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

    // 落地水机制相关变量
    private BlockPos placedWaterPos = null;
    private int waterPickupTimer = 0;

    public FriendlyBipedEntity(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    // ★ 修复报错：实现 AgeableMob 必需的繁育后代方法
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        return null; // 保镖不需要像动物一样繁殖，直接返回 null
    }

    // 实现 TamableAnimal 必需的 isFood 抽象方法
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CompanionFollowPearlGoal(this));
        this.goalSelector.addGoal(2, new CompanionCombatGoal(this, 1.5D));
        
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.2D, 10.0F, 2.0F));
        
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class, false));
    }

    @Override
    public void tick() {
        super.tick();

        // ================= 极客物理：落地水 (MLG Water Bucket) =================
        if (!this.level().isClientSide) {
            
            // 1. 水桶回收逻辑
            if (placedWaterPos != null) {
                waterPickupTimer--;
                // 如果计时器到了，或者脚已经接触到地面（水），立刻收水
                if (waterPickupTimer <= 0 || this.onGround() || this.isInWater()) {
                    if (this.level().getBlockState(placedWaterPos).is(Blocks.WATER)) {
                        this.level().setBlock(placedWaterPos, Blocks.AIR.defaultBlockState(), 3);
                        this.restoreMainHandItem(); // 把空桶收起，切回钻石剑/弓箭
                    }
                    placedWaterPos = null;
                }
            }

            // 2. 自由落体危险判定
            if (this.fallDistance > 3.5f && !this.onGround() && placedWaterPos == null) {
                // 预测：如果脚下 2 格内有固体方块，说明马上要摔在地上
                BlockPos posBelow = this.blockPosition().below(2);
                if (this.level().getBlockState(posBelow).blocksMotion() || 
                    this.level().getBlockState(posBelow.above()).blocksMotion()) {
                    
                    BlockPos waterPos = this.blockPosition();
                    // 确保水是放在可以被替换的地方（如空气或草丛）
                    if (this.level().getBlockState(waterPos).canBeReplaced()) {
                        // 戏要做足：切换出水桶
                        this.switchMainHandItem(new ItemStack(Items.WATER_BUCKET));
                        // 瞬间放置水源方块
                        this.level().setBlock(waterPos, Blocks.WATER.defaultBlockState(), 3);
                        placedWaterPos = waterPos;
                        waterPickupTimer = 20; // 设定1秒最大容错时间回收
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
