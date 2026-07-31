package com.yourmod.entity.ai;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class CompanionShootCrystalGoal extends Goal {

    private final FriendlyBipedEntity mob;
    private EndCrystal targetCrystal = null;
    private int attackCooldown = 0;

    public CompanionShootCrystalGoal(FriendlyBipedEntity mob) {
        this.mob = mob;
        // 瞄准水晶时必须停下脚步专心运算弹道
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (attackCooldown > 0) {
            attackCooldown--;
            return false;
        }

        // 范围扩大到 96 格，确保能感知到极高空的水晶
        List<EndCrystal> crystals = mob.level().getEntitiesOfClass(EndCrystal.class, mob.getBoundingBox().inflate(96.0D));
        if (crystals.isEmpty()) return false;

        double closestDist = Double.MAX_VALUE;
        EndCrystal closest = null;
        for (EndCrystal crystal : crystals) {
            double dist = mob.distanceToSqr(crystal);
            
            // ★ 核心改动：彻底移除 hasLineOfSight(透视检测)
            // 只要水晶在这个维度且在范围内，哪怕隔着黑曜石柱子也直接锁定
            if (dist < closestDist) {
                closestDist = dist;
                closest = crystal;
            }
        }

        if (closest != null) {
            targetCrystal = closest;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        mob.releaseUsingItem();
        mob.switchMainHandItem(new ItemStack(Items.BOW));
    }

    @Override
    public void tick() {
        if (targetCrystal == null || !targetCrystal.isAlive()) return;

        // 让脑袋稍微抬高，视觉上做出“仰望抛射”的动作
        mob.getLookControl().setLookAt(targetCrystal.getX(), targetCrystal.getY() + 10.0, targetCrystal.getZ(), 30.0F, 30.0F);
        mob.getNavigation().stop();

        if (attackCooldown <= 0) {
            mob.swing(InteractionHand.MAIN_HAND);
            Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), mob.getMainHandItem());
            
            // ★ 核心物理计算：抛物线弹道
            double dX = targetCrystal.getX() - mob.getX();
            double dY = targetCrystal.getY() - mob.getEyeY(); 
            double dZ = targetCrystal.getZ() - mob.getZ();
            
            // 获取水平距离
            double horizontalDist = Math.sqrt(dX * dX + dZ * dZ);
            
            // 抛物线抬枪补偿：水平距离越远，Y轴瞄准点抬得越高
            // 这里的 0.22 是针对 3.0F 满蓄力箭速的抛物线拟合系数
            double yOffset = dY + (horizontalDist * 0.22); 
            
            // 归一化生成最终的抛射向量
            Vec3 aim = new Vec3(dX, yOffset, dZ).normalize();
            
            arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
            
            // 3.0F 最大力度抛射，0.0F 绝对精准无扩散
            arrow.shoot(aim.x, aim.y, aim.z, 3.0F, 0.0F); 
            arrow.setBaseDamage(4.0);
            mob.level().addFreshEntity(arrow);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
            
            attackCooldown = 30; // 射完后等待 1.5 秒观察落点
        } else {
            attackCooldown--;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetCrystal != null && targetCrystal.isAlive() && mob.distanceToSqr(targetCrystal) <= 9216.0D;
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        targetCrystal = null;
        attackCooldown = 10;
    }
}
