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
        boolean isRangedThreat = false; // ★ 新增：远程威胁警报

        if (target instanceof Player player) {
            ItemStack playerItem = player.getMainHandItem();
            holdsAxe = playerItem.getItem() instanceof AxeItem;
            boolean holdsSword = playerItem.getItem() instanceof SwordItem;

            // 1. 判定玩家挥剑
            if (holdsSword && player.swingTime > 0) {
                Vec3 viewVector = player.getViewVector(1.0F).normalize();
                Vec3 playerToMob = mob.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
                if (viewVector.dot(playerToMob) > 0.8) {
                    isSwingingAtMe = true;
                }
            }
            
            // 2. 判定玩家正在拉弓/拉弩/蓄力三叉戟，且准星对准了 Boss
            if (player.isUsingItem() && (player.getUseItem().getItem() instanceof BowItem || 
                player.getUseItem().getItem() instanceof CrossbowItem || 
                player.getUseItem().getItem() instanceof TridentItem)) {
                
                Vec3 viewVector = player.getViewVector(1.0F).normalize();
                Vec3 playerToMob = mob.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
                if (viewVector.dot(playerToMob) > 0.5) { // 弓箭的准星容错率放大
                    isRangedThreat = true;
                }
            }
        }

        // 3. 雷达扫描：如果玩家没有拿弓，但有正在飞行的箭矢靠近 (8格内)
        if (!isRangedThreat) {
            List<Projectile> projectiles = mob.level().getEntitiesOfClass(Projectile.class, mob.getBoundingBox().inflate(8.0D));
            for (Projectile p : projectiles) {
                if (p.getDeltaMovement().lengthSqr() > 0.05) { // 只要有实体在快速飞行
                    isRangedThreat = true;
                    break;
                }
            }
        }

        // ================= 战术应对决策 =================
        if (holdsAxe) {
            if (mob.isUsingItem()) mob.releaseUsingItem();
            mob.getNavigation().moveTo(target, speedModifier * 1.3);
        } 
        else if (isSwingingAtMe) {
            mob.startUsingItem(InteractionHand.OFF_HAND);
            mob.getNavigation().stop(); // 面对近战劈砍，停下脚步稳固举盾
            shieldHoldTimer = 10; 
        } 
        else if (isRangedThreat) {
            // ★ 面对远程火力，举起盾牌，并顶着箭雨慢速向玩家压迫
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

        if (distSqr > 16.0 && attackCooldown <= 0) {
            comboCount = 0;
        }

        double horizDist = Math.sqrt(Math.pow(mob.getX() - target.getX(), 2) + Math.pow(mob.getZ() - target.getZ(), 2));
        double yDiff = target.getY() - mob.getY();

        if (attackCooldown <= 0 && (distSqr <= 16.0 || (horizDist <= 4.0 && yDiff > 1.0 && yDiff < 6.0))) {
            if (mob.isUsingItem()) mob.releaseUsingItem(); // 攻击瞬间必定放下盾牌
            
            // 防空起飞连击
            if (yDiff > 1.5 && mob.onGround()) {
                Vec3 leap = target.position().subtract(mob.position()).normalize().scale(0.4);
                mob.setDeltaMovement(leap.x, Math.min(yDiff * 0.3 + 0.2, 1.5), leap.z); 
                
                mob.swing(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(target);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.0F);
            } else {
                // 常规连击
                mob.swing(InteractionHand.MAIN_HAND);
                mob.doHurtTarget(target);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.0F);
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
