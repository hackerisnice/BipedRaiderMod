package com.yourmod.entity;

import com.yourmod.entity.ai.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class CustomBipedEntity extends PathfinderMob {

    // 用于保存被 Goal 临时替换前的原始主手物品
    @Nullable
    private ItemStack savedMainHandItem = null;

    public CustomBipedEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
protected void registerGoals() {
    this.goalSelector.addGoal(0, new FloatGoal(this));

    // ---------- 四大自定义战术 Goal ----------
    this.goalSelector.addGoal(1, new EscapeBoatAndBuildUpGoal(this, 1.2D));
    this.goalSelector.addGoal(2, new ThrowHarmingPotionAtFeetGoal(this));
    this.goalSelector.addGoal(3, new EnderPearlTeleportGoal(this));
    // ★ 新增：举盾破盾 Goal（优先级高于破墙和普通近战）
    this.goalSelector.addGoal(5, new AxeBreakShieldGoal(this));
    this.goalSelector.addGoal(4, new BreakBlockToReachTargetGoal(this));
    // -----------------------------------------

    this.goalSelector.addGoal(6, new MeleeAttackGoal(this, 1.0D, true));
    this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.8D));
    this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
    this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

    this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
}

    // ========== 主手物品保存与还原机制 ==========

    /**
     * 将当前主手物品保存到 savedMainHandItem，并将主手替换为 newItem。
     * 若已保存过，则不会覆盖原保存物品（确保原始物品永不丢失）。
     */
    public void switchMainHandItem(ItemStack newItem) {
        if (savedMainHandItem == null) {
            savedMainHandItem = this.getMainHandItem().copy(); // 深拷贝
        }
        this.setItemInHand(InteractionHand.MAIN_HAND, newItem);
    }

    /**
     * 还原主手物品为被替换前的原始物品，并清除保存记录。
     * 调用后 savedMainHandItem 置为 null。
     */
    public void restoreMainHandItem() {
        if (savedMainHandItem != null) {
            this.setItemInHand(InteractionHand.MAIN_HAND, savedMainHandItem.copy());
            savedMainHandItem = null;
        }
    }

    /**
     * 检查当前是否已经保存了原始主手物品（说明正处于 Goal 替换状态）
     */
    public boolean isMainHandSaved() {
        return savedMainHandItem != null;
    }

    // ========== 生命周期 ==========

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (savedMainHandItem != null && !savedMainHandItem.isEmpty()) {
            tag.put("SavedMainHandItem", savedMainHandItem.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SavedMainHandItem")) {
            savedMainHandItem = ItemStack.of(tag.getCompound("SavedMainHandItem"));
        }
    }
}
