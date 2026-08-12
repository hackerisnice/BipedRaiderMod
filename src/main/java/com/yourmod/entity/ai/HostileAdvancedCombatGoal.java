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
import net.minecraft.world.item.Items;
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
    
    private boolean isMaceAttacking = false;
    private float maxFallDistance = 0f;

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
        if (isMaceAttacking) return true; // 重锤期间绝不打断
        return canUse();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        shieldHoldTimer = 0;
        isMaceAttacking = false;
        maxFallDistance = 0f;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);

        // ==========================================
        // ★ 核心战术：风弹起飞 + 落地重锤
        // ==========================================
        if (isMaceAttacking) {
            if (!mob.onGround()) {
                maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
                // 在空中利用气流微调位置，追踪玩家的头顶
                Vec3 adjust = target.position().subtract(mob.position()).normalize().scale(0.06);
                mob.setDeltaMovement(mob.getDeltaMovement().add(adjust.x, 0, adjust.z));
            } else {
                // 落地触发重锤！
                if (maxFallDistance > 1.0f) {
                    mob.level().playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
                    mob.swing(InteractionHand.MAIN_HAND);
                    // 根据坠落高度计算毁灭性伤害
                    target.hurt(mob.damageSources().mobAttack(mob), 8.0f + maxFallDistance * 1.5f);
                }
                mob.restoreMainHandItem();
                isMaceAttacking = false;
                maxFallDistance = 0f;
            }
            return; // 重锤动画执行期间，跳过其他常规战斗逻辑
        }

        // 当走搭逼近到 5 格以内，且攻击冷却就绪时，直接引爆风弹起飞！
        if (horizDist <= 5.0 && attackCooldown <= 0 && mob.onGround()) {
            // 风弹引爆，起飞前使用！
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
            if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.GUST, mob.getX(), mob.getY(), mob.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
            }
            
            // 给足向上的推力，并稍微往前跃向目标
            Vec3 lunge = target.position().subtract(mob.position()).normalize().scale(0.3);
            mob.setDeltaMovement(lunge.x, 1.6, lunge.z);
            
            // 在半空中掏出重锤
            mob.switchMainHandItem(new ItemStack(Items.MACE));
            isMaceAttacking = true;
            maxFallDistance = 0f;
            attackCooldown = 40; 
            return;
        }
        // ==========================================

        // 常规盾牌与走位逻辑 (保持不变)
        boolean holdsAxe = false;
        boolean isRangedThreat = false; 
        if (target instanceof Player player) {
            ItemStack playerItem = player.getMainHandItem();
            holdsAxe = playerItem.getItem() instanceof AxeItem;
            if (player.isUsingItem() && (player.getUseItem().getItem() instanceof BowItem || 
                player.getUseItem().getItem() instanceof CrossbowItem || player.getUseItem().getItem() instanceof TridentItem)) {
                isRangedThreat = true;
            }
        }

        if (!isRangedThreat) {
            List<Projectile> projectiles = mob.level().getEntitiesOfClass(Projectile.class, mob.getBoundingBox().inflate(8.0D));
            for (Projectile p : projectiles) {
                if (p.getDeltaMovement().lengthSqr() > 0.05) {
                    isRangedThreat = true; break;
                }
            }
        }

        if (holdsAxe) {
            if (mob.isUsingItem()) mob.releaseUsingItem();
            mob.getNavigation().moveTo(target, speedModifier * 1.3);
        } else if (isRangedThreat) {
            mob.startUsingItem(InteractionHand.OFF_HAND);
            mob.getNavigation().moveTo(target, speedModifier * 0.5); 
            shieldHoldTimer = 10;
        } else {
            if (shieldHoldTimer > 0) {
                shieldHoldTimer--;
            } else {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                mob.getNavigation().moveTo(target, speedModifier);
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
