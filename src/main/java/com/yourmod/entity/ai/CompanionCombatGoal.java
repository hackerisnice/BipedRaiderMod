package com.yourmod.entity.ai;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
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
        
        boolean isDragon = target instanceof EnderDragon;
        EnderDragon dragon = isDragon ? (EnderDragon) target : null;
        
        Entity aimEntity = isDragon ? dragon.head : target;
        double distSqr = mob.distanceToSqr(aimEntity);

        mob.getLookControl().setLookAt(aimEntity, 30.0F, 30.0F);
        if (cobwebCooldown > 0) cobwebCooldown--;

        if (mob.onGround() && aimEntity.getY() < mob.getY() - 3.0) {
            Vec3 jumpDir = new Vec3(aimEntity.getX() - mob.getX(), 0, aimEntity.getZ() - mob.getZ());
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
            if (maxFallDistance > 1.5f && distSqr < 25.0) { 
                mob.swing(InteractionHand.MAIN_HAND);
                float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                
                if (isDragon) {
                    dragon.head.hurt(mob.damageSources().mobAttack(mob), baseDmg + (Math.min(maxFallDistance, 20) * 1.5f));
                } else {
                    target.hurt(mob.damageSources().mobAttack(mob), baseDmg + (Math.min(maxFallDistance, 20) * 1.5f));
                }
                
                // ★ 砸地音效移除拆包
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

        double bowEngageDist = isDragon ? 256.0 : 64.0;
        
        double horizDist = Math.sqrt(Math.pow(mob.getX() - aimEntity.getX(), 2) + Math.pow(mob.getZ() - aimEntity.getZ(), 2));
        double yDiff = aimEntity.getY() - mob.getY();
        
        if (horizDist > 8.0 && distSqr > bowEngageDist) { 
            if (mob.isUsingItem()) mob.releaseUsingItem(); 
            
            if (isDragon) {
                mob.getNavigation().moveTo(dragon.head.getX(), dragon.head.getY(), dragon.head.getZ(), speedModifier * 0.8);
            } else {
                mob.getNavigation().moveTo(target, speedModifier * 0.8);
            }
            
            mob.switchMainHandItem(new ItemStack(Items.BOW));
            
            if (attackCooldown <= 0) {
                mob.swing(InteractionHand.MAIN_HAND);
                Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), mob.getMainHandItem());
                
                Vec3 targetCenter = aimEntity.getBoundingBox().getCenter();
                Vec3 targetVelocity = target.getDeltaMovement(); 
                
                double flightTime = Math.sqrt(distSqr) / 1.6;
                Vec3 predictedPos = targetCenter.add(targetVelocity.scale(flightTime * 0.9));
                
                Vec3 aim = predictedPos.subtract(mob.getEyePosition()).normalize();
                
                arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
                arrow.shoot(aim.x, aim.y + 0.1, aim.z, 1.6F, 0.0F); 
                arrow.setBaseDamage(isDragon ? 7.0 : 4.0); 
                
                mob.level().addFreshEntity(arrow);
                // ★ 射箭音效移除拆包
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                
                attackCooldown = 30; 
            }
        } 
        else {
            if (isDragon) {
                mob.getNavigation().moveTo(dragon.head.getX(), dragon.head.getY(), dragon.head.getZ(), speedModifier);
            } else {
                mob.getNavigation().moveTo(target, speedModifier);
            }
            
            mob.switchMainHandItem(new ItemStack(Items.DIAMOND_SWORD));
            
            if (!isDragon && cobwebCooldown <= 0 && distSqr <= 25.0 && target.onGround() && mob.level().getBlockState(target.blockPosition()).canBeReplaced()) {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                mob.switchMainHandItem(new ItemStack(Items.COBWEB));
                mob.level().setBlock(target.blockPosition(), Blocks.COBWEB.defaultBlockState(), 3);
                mob.swing(InteractionHand.MAIN_HAND);
                mob.restoreMainHandItem();
                cobwebCooldown = 200; 
            }

            double meleeTriggerDist = isDragon ? 36.0 : 16.0;

            if (attackCooldown <= 0 && (distSqr <= meleeTriggerDist || (horizDist <= 4.0 && yDiff > 1.0 && yDiff < 6.0))) {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                
                if (yDiff > 1.5 && mob.onGround()) {
                    Vec3 leap = aimEntity.position().subtract(mob.position()).normalize().scale(0.5);
                    mob.setDeltaMovement(leap.x, Math.min(yDiff * 0.35 + 0.5, 2.0), leap.z); 
                    
                    // ★ 风弹保留拆包
                    mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
                    if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GUST, mob.getX(), mob.getY(), mob.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
                    }
                    
                    mob.swing(InteractionHand.MAIN_HAND);
                    float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    
                    if (isDragon) {
                        dragon.head.hurt(mob.damageSources().mobAttack(mob), baseDmg * 2.0F);
                    } else {
                        target.hurt(mob.damageSources().mobAttack(mob), baseDmg * 1.5F);
                    }
                    // ★ 暴击音效移除拆包
                    mob.level().playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    attackCooldown = 20;
                }
                else if (mob.onGround() && !waitingForCrit) {
                    mob.jumpFromGround();
                    waitingForCrit = true; 
                } 
                else if (waitingForCrit && mob.getDeltaMovement().y < 0) {
                    mob.swing(InteractionHand.MAIN_HAND);
                    float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    
                    if (isDragon) {
                        dragon.head.hurt(mob.damageSources().mobAttack(mob), baseDmg * 2.0F);
                    } else {
                        target.hurt(mob.damageSources().mobAttack(mob), baseDmg * 1.5F);
                    }
                    // ★ 暴击音效移除拆包
                    mob.level().playSound(null, aimEntity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    
                    waitingForCrit = false;
                    attackCooldown = 20; 
                }
            } 
            else if (attackCooldown > 5 && distSqr <= meleeTriggerDist + 4.0 && !waitingForCrit) {
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
