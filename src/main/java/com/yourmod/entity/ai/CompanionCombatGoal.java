package com.yourmod.entity.ai;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
    
    private boolean isMaceAttacking = false;
    private float maxFallDistance = 0f;

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
        if (isMaceAttacking) return true; // 空中绝不打断重锤
        return canUse();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        cobwebCooldown = 0;
        isMaceAttacking = false;
        maxFallDistance = 0f;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        boolean isDragon = target instanceof EnderDragon;
        EnderDragon dragon = isDragon ? (EnderDragon) target : null;
        Entity aimEntity = isDragon ? dragon.head : target;
        
        mob.getLookControl().setLookAt(aimEntity, 30.0F, 30.0F);
        if (cobwebCooldown > 0) cobwebCooldown--;

        double dx = aimEntity.getX() - mob.getX();
        double dz = aimEntity.getZ() - mob.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        double distSqr = mob.distanceToSqr(aimEntity);

        // ==========================================
        // ★ Aiko 核心战术：风弹起飞 + 落地重锤
        // ==========================================
        if (isMaceAttacking) {
            if (!mob.onGround()) {
                maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
                Vec3 adjust = aimEntity.position().subtract(mob.position()).normalize().scale(0.06);
                mob.setDeltaMovement(mob.getDeltaMovement().add(adjust.x, 0, adjust.z));
            } else {
                if (maxFallDistance > 1.0f) {
                    mob.level().playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    mob.swing(InteractionHand.MAIN_HAND);
                    float dmg = 8.0f + maxFallDistance * 1.5f;
                    if (isDragon) {
                        dragon.head.hurt(mob.damageSources().mobAttack(mob), dmg);
                    } else {
                        target.hurt(mob.damageSources().mobAttack(mob), dmg);
                    }
                }
                mob.restoreMainHandItem();
                isMaceAttacking = false;
                maxFallDistance = 0f;
            }
            return;
        }

        // 逼近到 5 格以内，引爆风弹开启重锤连招！
        if (horizDist <= 5.0 && attackCooldown <= 0 && mob.onGround() && !isDragon) {
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GUST, mob.getX(), mob.getY(), mob.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
            }
            
            Vec3 lunge = aimEntity.position().subtract(mob.position()).normalize().scale(0.3);
            mob.setDeltaMovement(lunge.x, 1.6, lunge.z);
            
            mob.switchMainHandItem(new ItemStack(Items.MACE));
            isMaceAttacking = true;
            maxFallDistance = 0f;
            attackCooldown = 40;
            return;
        }
        // ==========================================

        double bowEngageDist = isDragon ? 256.0 : 64.0;
        
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
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                
                attackCooldown = 30; 
            }
        } else {
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
            
            if (attackCooldown > 5 && horizDist <= 16.0) {
                if (!mob.isUsingItem()) mob.startUsingItem(InteractionHand.OFF_HAND);
            } else if (attackCooldown <= 5) {
                if (mob.isUsingItem()) mob.releaseUsingItem();
            }
        }

        if (attackCooldown > 0) attackCooldown--;
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        mob.restoreMainHandItem();
        isMaceAttacking = false;
        maxFallDistance = 0f;
    }
}
