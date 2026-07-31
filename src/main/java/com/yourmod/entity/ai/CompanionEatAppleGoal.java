package com.yourmod.entity.ai;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.EnumSet;

public class CompanionEatAppleGoal extends Goal {

    private final FriendlyBipedEntity mob;
    private int eatingTicks = 0;
    private boolean hasStartedEating = false;
    private static final int EAT_DURATION = 32;
    private static final float HEAL_THRESHOLD = 0.3F; // 血量低于 30% 触发

    public CompanionEatAppleGoal(FriendlyBipedEntity mob) {
        this.mob = mob;
        // 吃苹果时接管移动和视线，防止被打断
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        float healthPercent = mob.getHealth() / mob.getMaxHealth();
        if (healthPercent >= HEAL_THRESHOLD) {
            return false;
        }
        if (mob.isUsingItem()) {
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        // ★ 核心防冲突：强制放下盾牌或弓箭，然后再吃苹果
        mob.releaseUsingItem();
        
        mob.switchMainHandItem(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        mob.startUsingItem(InteractionHand.MAIN_HAND);
        hasStartedEating = true;
        eatingTicks = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!hasStartedEating) return;

        mob.getNavigation().stop();
        if (mob.getTarget() != null) {
            mob.getLookControl().setLookAt(mob.getTarget(), 30.0F, 30.0F);
        }

        eatingTicks++;

        if (!mob.isUsingItem()) {
            // 物品使用完毕，直接还原武器
            mob.restoreMainHandItem();
            hasStartedEating = false;
            eatingTicks = 0;
        } else if (eatingTicks > EAT_DURATION * 1.5) {
            // 超时保护：如果被卡住，强制取消使用状态
            mob.releaseUsingItem();
            mob.restoreMainHandItem();
            hasStartedEating = false;
            eatingTicks = 0;
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getHealth() / mob.getMaxHealth() > 0.5F || !mob.isAlive()) {
            return false;
        }
        return hasStartedEating;
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) {
            mob.releaseUsingItem();
        }
        mob.restoreMainHandItem();
        hasStartedEating = false;
        eatingTicks = 0;
        mob.getNavigation().stop();
    }
}
