package com.yourmod.entity;

import com.yourmod.entity.ai.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.HolderLookup;

// 必须继承 Monster
public class CustomBipedEntity extends Monster {

    @Nullable
    private ItemStack savedMainHandItem = null;

    public CustomBipedEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // 1. 保命优先
        this.goalSelector.addGoal(1, new EatEnchantedGoldenAppleGoal(this));
        // 2. 暴走反击 (连击3次触发跑酷重锤)
        this.goalSelector.addGoal(2, new EscapeBoatAndBuildUpGoal(this, 1.2D));
        // 3. 极近距离战术
        this.goalSelector.addGoal(3, new AxeBreakShieldGoal(this));
        this.goalSelector.addGoal(4, new ThrowHarmingPotionAtFeetGoal(this));
        // 4. 寻路与障碍清理
        this.goalSelector.addGoal(5, new BreakBlockToReachTargetGoal(this));
        // 5. 远程追击
        this.goalSelector.addGoal(6, new EnderPearlTeleportGoal(this));

        // 6. 基础移动与攻击 (1.5倍速疾跑追击，0.6倍速慢走巡逻)
        this.goalSelector.addGoal(7, new MeleeAttackGoal(this, 1.5D, true));
        this.goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        // 目标选择器：false代表无视墙壁透视索敌
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
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

    public boolean isMainHandSaved() {
        return savedMainHandItem != null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        HolderLookup.Provider provider = this.level().registryAccess();
        if (this.savedMainHandItem != null && !this.savedMainHandItem.isEmpty()) {
            tag.put("SavedMainHandItem", this.savedMainHandItem.saveOptional(provider));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        HolderLookup.Provider provider = this.level().registryAccess();
        if (tag.contains("SavedMainHandItem")) {
            this.savedMainHandItem = ItemStack.parseOptional(provider, tag.getCompound("SavedMainHandItem"));
        } else {
            this.savedMainHandItem = ItemStack.EMPTY;
        }
    }
}
