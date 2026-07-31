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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CompanionCombatGoal extends Goal {

    private final FriendlyBipedEntity mob;
    private final double speedModifier;
    
    private int attackCooldown = 0;
    private int cobwebCooldown = 0; // 蜘蛛网技能冷却
    
    private float maxFallDistance = 0f;
    private boolean isMaceAttacking = false;
    private boolean waitingForCrit = false; // 是否正在腾空准备打暴击
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
        cobwebCooldown = 0;
        maxFallDistance = 0f;
        isMaceAttacking = false;
        waitingForCrit = false;
        wasOnGround = mob.onGround();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSqr = mob.distanceToSqr(target);

        if (cobwebCooldown > 0) cobwebCooldown--;

        if (mob.onGround() && target.getY() < mob.getY() - 3.0) {
            Vec3 jumpDir = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
            if (jumpDir.lengthSqr() > 0.01) {
                jumpDir = jumpDir.normalize().scale(0.35); 
                mob.setDeltaMovement(jumpDir.x, mob.getDeltaMovement().y, jumpDir.z);
            }
        }

        // ================= 1. 高空重锤 =================
        if (!mob.onGround() && !waitingForCrit) { // 如果不是普通跳劈，则累积重锤高度
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
            mob.releaseUsingItem(); 
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
        // ================= 3. 近战：高级身法操作 =================
        else {
            mob.getNavigation().moveTo(target, speedModifier);
            mob.switchMainHandItem(new ItemStack(Items.DIAMOND_SWORD));
            
            // ★ 高端操作 1：蜘蛛网控场 (每 10 秒触发一次，且敌人在 5 格以内)
            if (cobwebCooldown <= 0 && distSqr <= 25.0 && target.onGround() && mob.level().getBlockState(target.blockPosition()).canBeReplaced()) {
                mob.releaseUsingItem();
                mob.switchMainHandItem(new ItemStack(Items.COBWEB));
                mob.level().setBlock(target.blockPosition(), Blocks.COBWEB.defaultBlockState(), 3);
                mob.swing(InteractionHand.MAIN_HAND);
                mob.restoreMainHandItem();
                cobwebCooldown = 200; // 10秒冷却
            }

            // ★ 高端操作 2：跳劈暴击 (Jump Crits)
            if (attackCooldown <= 0 && distSqr <= 12.0) {
                mob.releaseUsingItem();
                
                // 起跳阶段
                if (mob.onGround() && !waitingForCrit) {
                    mob.jumpFromGround();
                    waitingForCrit = true; // 标记正在准备暴击
                } 
                // 下落帧进行处决判定 (Y 轴速度小于 0 表示正在坠落)
                else if (waitingForCrit && mob.getDeltaMovement().y < 0) {
                    mob.swing(InteractionHand.MAIN_HAND);
                    
                    // 利用底层伤害结算：基础伤害 * 1.5 暴击倍率
                    float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    target.hurt(mob.damageSources().mobAttack(mob), baseDmg * 1.5F);
                    mob.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    
                    waitingForCrit = false;
                    attackCooldown = 20; 
                }
            } 
            // 如果在冷却，且目标没死，进行举盾防御
            else if (attackCooldown > 5 && distSqr <= 16.0 && !waitingForCrit) {
                mob.startUsingItem(InteractionHand.OFF_HAND);
            } else if (attackCooldown <= 5) {
                mob.releaseUsingItem();
            }
        }

        if (attackCooldown > 0) attackCooldown--;
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        mob.releaseUsingItem();
        mob.restoreMainHandItem();
        isMaceAttacking = false;
        waitingForCrit = false;
        maxFallDistance = 0f;
    }
}
