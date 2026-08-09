package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

import java.util.EnumSet;
import java.util.List;

public class HostileAdvancedCombatGoal extends Goal {
    
    private final CustomBipedEntity mob;
    private final double speedModifier;
    private int attackCooldown = 0;
    private int shieldHoldTimer = 0;
    private int comboCount = 0;

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

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        shieldHoldTimer = 0;
        comboCount = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distSqr = mob.distanceToSqr(target);

        boolean holdsAxe = false;
        boolean isSwingingAtMe = false;
        boolean isRangedThreat = false; 

        if (target instanceof Player player) {
            ItemStack playerItem = player.getMainHandItem();
            holdsAxe = playerItem.getItem() instanceof AxeItem;
            boolean holdsSword = playerItem.getItem() instanceof SwordItem;

            if (holdsSword && player.swingTime > 0) {
                Vec3 viewVector = player.getViewVector(1.0F).normalize();
                Vec3 playerToMob = mob.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
                if (viewVector.dot(playerToMob) > 0.8) isSwingingAtMe = true;
            }
            
            if (player.isUsingItem() && (player.getUseItem().getItem() instanceof BowItem || 
                player.getUseItem().getItem() instanceof CrossbowItem || 
                player.getUseItem().getItem() instanceof TridentItem)) {
                
                Vec3 viewVector = player.getViewVector(1.0F).normalize();
                Vec3 playerToMob = mob.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
                if (viewVector.dot(playerToMob) > 0.5) isRangedThreat = true;
            }
        }

        if (!isRangedThreat) {
            List<Projectile> projectiles = mob.level().getEntitiesOfClass(Projectile.class, mob.getBoundingBox().inflate(8.0D));
            for (Projectile p : projectiles) {
                if (p.getDeltaMovement().lengthSqr() > 0.05) {
                    isRangedThreat = true;
                    break;
                }
            }
        }

        if (holdsAxe) {
            if (mob.isUsingItem()) mob.releaseUsingItem();
            mob.getNavigation().moveTo(target, speedModifier * 1.3);
        } 
        else if (isSwingingAtMe) {
            mob.startUsingItem(InteractionHand.OFF_HAND);
            mob.getNavigation().stop(); 
            shieldHoldTimer = 10; 
        } 
        else if (isRangedThreat) {
            mob.startUsingItem(InteractionHand.OFF_HAND);
            mob.getNavigation().moveTo(target, speedModifier * 0.5); 
            shieldHoldTimer = 10;
        }
        else {
            if (shieldHoldTimer > 0) {
                shieldHoldTimer--;
            } else {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                mob.getNavigation().moveTo(target, speedModifier);
            }
        }

        if (attackCooldown > 0) attackCooldown--;
        if (distSqr > 16.0 && attackCooldown <= 0) comboCount = 0;

        double horizDist = Math.sqrt(Math.pow(mob.getX() - target.getX(), 2) + Math.pow(mob.getZ() - target.getZ(), 2));
        double yDiff = target.getY() - mob.getY();

        if (attackCooldown <= 0 && (distSqr <= 16.0 || (horizDist <= 4.0 && yDiff > 1.0 && yDiff < 6.0))) {
            if (mob.isUsingItem()) mob.releaseUsingItem(); 
            
            if (yDiff > 1.5 && mob.onGround()) {
                Vec3 leap = target.position().subtract(mob.position()).normalize().scale(0.5);
                mob.setDeltaMovement(leap.x, Math.min(yDiff * 0.35 + 0.5, 2.0), leap.z); 
                
                // ★ 修复：拆包所有音效
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
                if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.GUST, mob.getX(), mob.getY(), mob.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
                }
                
                mob.swing(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(target);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
            } else {
                mob.swing(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(target);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
            }

            Vec3 lunge = target.position().subtract(mob.position()).normalize().scale(0.25);
            mob.setDeltaMovement(mob.getDeltaMovement().add(lunge.x, 0, lunge.z));

            comboCount++;
            
            if (comboCount >= 3) {
                attackCooldown = 25; 
                comboCount = 0;
            } else {
                attackCooldown = 6;  
            }
        }
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        comboCount = 0;
    }
}
