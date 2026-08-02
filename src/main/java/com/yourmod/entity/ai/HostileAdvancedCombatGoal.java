package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HostileAdvancedCombatGoal extends Goal {
    
    private final CustomBipedEntity mob;
    private final double speedModifier;
    private int attackCooldown = 0;
    private int shieldHoldTimer = 0;

    public HostileAdvancedCombatGoal(CustomBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    // ★ 核心修复：强行覆盖原版的中断逻辑。只要目标活着，绝对不死不休，不会再出现“原地发呆”的情况。
    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        shieldHoldTimer = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSqr = mob.distanceToSqr(target);

        boolean holdsAxe = false;
        boolean isSwingingAtMe = false;

        // ★ 高端读取玩家操作
        if (target instanceof Player player) {
            ItemStack playerItem = player.getMainHandItem();
            holdsAxe = playerItem.getItem() instanceof AxeItem;
            boolean holdsSword = playerItem.getItem() instanceof SwordItem;

            // 只有当玩家拿剑，且正在挥舞时才进行视线判定 (屏蔽了挖方块动作)
            if (holdsSword && player.swingTime > 0) {
                // 计算玩家的视角射线，与怪物位置进行点积(Dot Product)
                Vec3 viewVector = player.getViewVector(1.0F).normalize();
                Vec3 playerToMob = mob.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
                
                // 结果 > 0.8 说明玩家正面对着怪物挥剑，而不是在挖旁边的方块
                if (viewVector.dot(playerToMob) > 0.8) {
                    isSwingingAtMe = true;
                }
            }
        }

        // ================= 战术应对 =================
        if (holdsAxe) {
            // 玩家拿斧头，立刻放弃举盾防守，强行提高移速进行疯狗式冲锋
            if (mob.isUsingItem()) mob.releaseUsingItem();
            mob.getNavigation().moveTo(target, speedModifier * 1.3);
        } 
        else if (isSwingingAtMe) {
            // 玩家拿剑劈砍自己，立刻举起副手盾牌，并停下脚步进行格挡
            mob.startUsingItem(InteractionHand.OFF_HAND);
            mob.getNavigation().stop();
            shieldHoldTimer = 10; // 维持举盾状态 0.5 秒
        } 
        else {
            // 正常接敌
            if (shieldHoldTimer > 0) {
                shieldHoldTimer--;
            } else {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                mob.getNavigation().moveTo(target, speedModifier);
            }
        }

        if (attackCooldown > 0) attackCooldown--;

        // ================= 攻击执行 =================
        if (distSqr <= 4.0 && attackCooldown <= 0) {
            if (mob.isUsingItem()) mob.releaseUsingItem(); // 攻击时必须放下盾牌
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
            attackCooldown = 20;
        }
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
    }
}
