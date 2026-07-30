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
        if (isMaceAttacking && !wasOnGround && mob.onGround()) return true; // 重锤续命
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

        // ================= 1. 高空重锤 =================
        if (!mob.onGround()) {
            maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
            if (maxFallDistance > 1.5f && !isMaceAttacking) {
                mob.switchMainHandItem(new ItemStack(Items.MACE));
                isMaceAttacking = true;
            }
        } else if (!wasOnGround && isMaceAttacking) {
            // 落地瞬间判定
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
            return; // 空中优先重锤，不执行其他攻击
        }

        // ================= 2. 远距离：弓箭 =================
        if (distSqr > 64.0) { // 距离 > 8格
            mob.getNavigation().moveTo(target, speedModifier * 0.8);
            mob.switchMainHandItem(new ItemStack(Items.BOW));
            
            if (attackCooldown <= 0) {
                mob.swing(InteractionHand.MAIN_HAND);
                Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), mob.getMainHandItem());
                Vec3 aim = target.getBoundingBox().getCenter().subtract(mob.getEyePosition()).normalize();
                
                arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
                arrow.shoot(aim.x, aim.y + 0.1, aim.z, 1.6F, 1.0F); // 标准射击精度
                arrow.setBaseDamage(4.0);
                mob.level().addFreshEntity(arrow);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                
                attackCooldown = 30; // 1.5秒射一箭
            }
        } 
        // ================= 3. 近距离：钻石剑 =================
        else {
            mob.getNavigation().moveTo(target, speedModifier);
            mob.switchMainHandItem(new ItemStack(Items.DIAMOND_SWORD));
            
            if (distSqr <= 4.0 && attackCooldown <= 0) {
                mob.swing(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(target);
                attackCooldown = 15; // 近战攻速极快
            }
        }

        if (attackCooldown > 0) attackCooldown--;
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        isMaceAttacking = false;
        maxFallDistance = 0f;
    }
}
