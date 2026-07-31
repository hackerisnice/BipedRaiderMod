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
        // 瞄准水晶时必须停下脚步专心射击
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (attackCooldown > 0) {
            attackCooldown--;
            return false;
        }

        // 扫描周围 64 格内的所有末地水晶
        List<EndCrystal> crystals = mob.level().getEntitiesOfClass(EndCrystal.class, mob.getBoundingBox().inflate(64.0D));
        if (crystals.isEmpty()) return false;

        // 找出视线无遮挡且最近的水晶
        double closestDist = Double.MAX_VALUE;
        EndCrystal closest = null;
        for (EndCrystal crystal : crystals) {
            double dist = mob.distanceToSqr(crystal);
            if (dist < closestDist && mob.getSensing().hasLineOfSight(crystal)) {
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

        // 死死锁住水晶
        mob.getLookControl().setLookAt(targetCrystal, 30.0F, 30.0F);
        mob.getNavigation().stop();

        if (attackCooldown <= 0) {
            mob.swing(InteractionHand.MAIN_HAND);
            Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), mob.getMainHandItem());
            
            // 精准弹道计算：直指水晶中心
            Vec3 aim = targetCrystal.getBoundingBox().getCenter().subtract(mob.getEyePosition()).normalize();
            
            arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
            // 2.0F 极快箭速，0.0F 绝对精准无扩散
            arrow.shoot(aim.x, aim.y + 0.1, aim.z, 2.0F, 0.0F); 
            arrow.setBaseDamage(4.0);
            mob.level().addFreshEntity(arrow);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
            
            attackCooldown = 30; // 射完后等待 1.5 秒
        } else {
            attackCooldown--;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetCrystal != null && targetCrystal.isAlive() && mob.distanceToSqr(targetCrystal) <= 4096.0D;
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        targetCrystal = null;
        attackCooldown = 10;
    }
}
