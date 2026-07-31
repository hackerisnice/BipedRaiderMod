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
    private int cobwebCooldown = 0; 
    
    private float maxFallDistance = 0f;
    private boolean isMaceAttacking = false;
    private boolean waitingForCrit = false; 
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

        if (!mob.onGround() && !waitingForCrit) { 
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
        } else {
            mob.getNavigation().moveTo(target, speedModifier);
            mob.switchMainHandItem(new ItemStack(Items.DIAMOND_SWORD));
            
            if (cobwebCooldown <= 0 && distSqr <= 25.0 && target.onGround() && mob.level().getBlockState(target.blockPosition()).canBeReplaced()) {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                mob.switchMainHandItem(new ItemStack(Items.COBWEB));
                mob.level().setBlock(target.blockPosition(), Blocks.COBWEB.defaultBlockState(), 3);
                mob.swing(InteractionHand.MAIN_HAND);
                mob.restoreMainHandItem();
                cobwebCooldown = 200; 
            }

            if (attackCooldown <= 0 && distSqr <= 12.0) {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                
                if (mob.onGround() && !waitingForCrit) {
                    mob.jumpFromGround();
                    waitingForCrit = true; 
                } 
                else if (waitingForCrit && mob.getDeltaMovement().y < 0) {
                    mob.swing(InteractionHand.MAIN_HAND);
                    float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    target.hurt(mob.damageSources().mobAttack(mob), baseDmg * 1.5F);
                    mob.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    
                    waitingForCrit = false;
                    attackCooldown = 20; 
                }
            } 
            // ★ 修复举盾动作：加入 !mob.isUsingItem() 判断，防止无限重置状态
            else if (attackCooldown > 5 && distSqr <= 16.0 && !waitingForCrit) {
                if (!mob.isUsingItem()) {
                    mob.startUsingItem(InteractionHand.OFF_HAND);
                }
            } else if (attackCooldown <= 5) {
                if (mob.isUsingItem()) {
                    mob.releaseUsingItem();
                }
            }
        }

        if (attackCooldown > 0) attackCooldown--;
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        mob.restoreMainHandItem();
        isMaceAttacking = false;
        waitingForCrit = false;
        maxFallDistance = 0f;
    }
}
