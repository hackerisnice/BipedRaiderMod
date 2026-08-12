package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.EnumSet;
import java.util.List;

/**
 * 精简后的常规战斗 AI：专注走位与举盾防御
 */
public class HostileAdvancedCombatGoal extends Goal {
    
    private final CustomBipedEntity mob;
    private final double speedModifier;
    private int attackCooldown = 0;
    private int shieldHoldTimer = 0;

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
    public void start() {
        attackCooldown = 0;
        shieldHoldTimer = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // 常规盾牌与走位逻辑
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
            // 对方拿斧头：放下盾牌，加速近战硬刚
            if (mob.isUsingItem()) mob.releaseUsingItem();
            mob.getNavigation().moveTo(target, speedModifier * 1.3);
        } else if (isRangedThreat) {
            // 对方有远程威胁：举盾，缓慢压迫
            mob.startUsingItem(InteractionHand.OFF_HAND);
            mob.getNavigation().moveTo(target, speedModifier * 0.5); 
            shieldHoldTimer = 10;
        } else {
            // 正常接敌
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
    }
}
