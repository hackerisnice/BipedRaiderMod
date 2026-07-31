package com.yourmod.entity.ai;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CompanionCombatGoal extends Goal {

    private final FriendlyBipedEntity mob;
    private final double speedModifier;
    
    private int attackCooldown = 0;
    private float maxFallDistance = 0f;
    private boolean isMaceAttacking = false;
    private boolean wasOnGround = true;

    public CompanionCombatGoal(FriendlyBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }
    
    @Override
    public boolean canContinueToUse() {
        if (isMaceAttacking && !wasOnGround && mob.onGround()) return true; 
        return canUse();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        maxFallDistance = 0f;
        isMaceAttacking = false;
        wasOnGround = mob.onGround();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSqr = mob.distanceToSqr(target);

        // ★ 新增：跳崖追击判定
        // 如果目标比保镖低了 3 格以上，无视原版寻路的悬崖保护，强行注入水平速度冲下边缘
        if (mob.onGround() && target.getY() < mob.getY() - 3.0) {
            Vec3 jumpDir = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
            if (jumpDir.lengthSqr() > 0.01) {
                jumpDir = jumpDir.normalize().scale(0.35); // 获得向前的初速度冲出方块
                mob.setDeltaMovement(jumpDir.x, mob.getDeltaMovement().y, jumpDir.z);
            }
        }

        // ================= 1. 高空重锤 =================
        if (!mob.onGround()) {
            maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
            if (maxFallDistance > 1.5f && !isMaceAttacking) {
                mob.switchMainHandItem(new ItemStack(Items.MACE));
                isMaceAttacking = true;
            }
        } else if (!wasOnGround && isMaceAttacking) {
            if (maxFallDistance > 1.5f && distSqr < 16.0) {
                mob.swing(InteractionHand.MAIN_HAND);
                float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                target.hurt(mob.damageSources().mobAttack(mob), baseDmg + (Math.min(maxFallDistance, 20) * 1.5f));
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            mob.restoreMainHandItem();
            isMaceAttacking = false;
            maxFallDistance = 0f;
        }

        if (isMaceAttacking) {
            wasOnGround = mob.onGround();
            return; 
        }

        // ================= 2. 远距离：弓箭 =================
        if (distSqr > 64.0) { 
            mob.releaseUsingItem(); // 确保举弓时不被盾牌打断
            mob.getNavigation().moveTo(target, speedModifier * 0.8);
            mob.switchMainHandItem(new ItemStack(Items.BOW));
            
            if (attackCooldown <= 0) {
                mob.swing(InteractionHand.MAIN_HAND);
                Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), mob.getMainHandItem());
                Vec3 aim = target.getBoundingBox().getCenter().subtract(mob.getEyePosition()).normalize();
                
                arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
                arrow.shoot(aim.x, aim.y + 0.1, aim.z, 1.6F, 1.0F); 
                arrow.setBaseDamage(4.0);
                mob.level().addFreshEntity(arrow);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                
                attackCooldown = 30; 
            }
        } 
        // ================= 3. 近距离：钻石剑与举盾 =================
        else {
            mob.getNavigation().moveTo(target, speedModifier);
            mob.switchMainHandItem(new ItemStack(Items.DIAMOND_SWORD));
            
            // ★ 核心改动：近战举盾防御循环
            if (attackCooldown <= 0) {
                // 攻击冷却完毕，放下盾牌进行攻击
                mob.releaseUsingItem(); 
                if (distSqr <= 9.0) { // 近战攻击范围
                    mob.swing(InteractionHand.MAIN_HAND);
                    mob.doHurtTarget(target);
                    attackCooldown = 25; // 给一点较长的冷却时间，使得能观察到举盾动作
                }
            } else if (attackCooldown > 5 && distSqr <= 16.0) {
                // 冷却超过 5 刻，且敌人就在眼前，立刻举起副手的盾牌防守
                mob.startUsingItem(InteractionHand.OFF_HAND);
            } else if (attackCooldown <= 5) {
                // 即将可以攻击（最后5刻），提早放下盾牌取消移速惩罚，准备冲锋挥剑
                mob.releaseUsingItem();
            }
        }

        if (attackCooldown > 0) attackCooldown--;
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        mob.releaseUsingItem(); // 异常停止时确保放下盾牌
        mob.restoreMainHandItem();
        isMaceAttacking = false;
        maxFallDistance = 0f;
    }
}
