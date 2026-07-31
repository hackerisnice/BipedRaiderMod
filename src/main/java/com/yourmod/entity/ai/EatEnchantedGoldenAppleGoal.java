package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

public class EatEnchantedGoldenAppleGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private int eatingTicks = 0;
    private boolean hasStartedEating = false;
    private static final int EAT_DURATION = 32; 
    private static final float HEAL_THRESHOLD = 0.3F; 

    public EatEnchantedGoldenAppleGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); 
    }

    @Override
    public boolean canUse() {
        // ★ 核心改动：检查是否达到了 5 次上限
        if (!mob.canEatApple()) {
            return false;
        }
        
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
            if (mob.getMainHandItem().isEmpty()) {
                // ★ 核心改动：通知实体已经吃下了一颗苹果，增加计数
                mob.consumeApple();
                
                mob.restoreMainHandItem();
                hasStartedEating = false;
                eatingTicks = 0;
                return;
            } else {
                mob.restoreMainHandItem();
                hasStartedEating = false;
                eatingTicks = 0;
            }
        }

        if (eatingTicks > EAT_DURATION * 1.5) {
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
        mob.restoreMainHandItem();
        hasStartedEating = false;
        eatingTicks = 0;
        if (mob.isUsingItem()) {
            mob.releaseUsingItem();
        }
        mob.getNavigation().stop();
    }
}
