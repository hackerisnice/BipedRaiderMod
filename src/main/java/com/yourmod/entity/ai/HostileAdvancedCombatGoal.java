package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HostileAdvancedCombatGoal extends Goal {
    
    private final CustomBipedEntity mob;
    private final double speedModifier;
    private int attackCooldown = 0;
    private int shieldHoldTimer = 0;
    
    // ★ 新增：连击计数器
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

        if (target instanceof Player player) {
            ItemStack playerItem = player.getMainHandItem();
            holdsAxe = playerItem.getItem() instanceof AxeItem;
            boolean holdsSword = playerItem.getItem() instanceof SwordItem;

            if (holdsSword && player.swingTime > 0) {
                Vec3 viewVector = player.getViewVector(1.0F).normalize();
                Vec3 playerToMob = mob.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
                if (viewVector.dot(playerToMob) > 0.8) {
                    isSwingingAtMe = true;
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
        else {
            if (shieldHoldTimer > 0) {
                shieldHoldTimer--;
            } else {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                mob.getNavigation().moveTo(target, speedModifier);
            }
        }

        if (attackCooldown > 0) attackCooldown--;

        // 如果玩家拉开了距离，强制重置连击段数
        if (distSqr > 16.0 && attackCooldown <= 0) {
            comboCount = 0;
        }

        // ================= ★ 终极连招执行 =================
        // 放宽近战范围，确保它能像玩家一样隔着 3 格进行打击
        if (distSqr <= 12.0 && attackCooldown <= 0) {
            if (mob.isUsingItem()) mob.releaseUsingItem(); 
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
            
            // 播放挥剑横扫音效，增加连击压迫感
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.0F);

            // 粘人突进：每次挥剑，身体获得一个向着玩家的推力，强制拉近距离
            Vec3 lunge = target.position().subtract(mob.position()).normalize().scale(0.25);
            mob.setDeltaMovement(mob.getDeltaMovement().add(lunge.x, 0, lunge.z));

            comboCount++;
            
            // 三段式连击结算
            if (comboCount >= 3) {
                attackCooldown = 25; // 三刀砍完，后摇 1.25 秒
                comboCount = 0;
            } else {
                attackCooldown = 6;  // 连击间隔极度缩短为 0.3 秒！
            }
        }
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        comboCount = 0;
    }
}
