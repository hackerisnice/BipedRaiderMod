package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HostileAdvancedCombatGoal extends Goal {
    
    private final CustomBipedEntity mob;
    private final double speedModifier;
    private int attackCooldown = 0;
    
    private float circleDirection = 1.0F;
    private int circleSwitchTimer = 0;
    private int postHitRetreatTimer = 0; 
    private int strafeJumpCooldown = 0;

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
        circleDirection = mob.getRandom().nextBoolean() ? 1.0F : -1.0F;
        postHitRetreatTimer = 0;
        strafeJumpCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.yBodyRot = mob.getYHeadRot(); 
        double distance = Math.sqrt(mob.distanceToSqr(target));

        if (target instanceof Player player) {
            ItemStack playerItem = player.getMainHandItem();
            if (playerItem.getItem() instanceof AxeItem && mob.isUsingItem()) {
                mob.releaseUsingItem();
            }
        }

        // ==========================================
        // 寻路与走位判定
        // ==========================================
        if (distance > 8.0 && postHitRetreatTimer <= 0) {
            boolean hasPath = mob.getNavigation().moveTo(target, speedModifier * 1.2);
            
            // ★ 核心修复：如果原版寻路找不到路（隔着悬崖或发呆），启动物理引擎强行跨越！
            if (!hasPath || mob.getNavigation().isDone()) {
                Vec3 toTarget = target.position().subtract(mob.position()).normalize();
                mob.setDeltaMovement(toTarget.x * 0.45, mob.getDeltaMovement().y, toTarget.z * 0.45);
                if (mob.horizontalCollision && mob.onGround()) mob.getJumpControl().jump();
            }
        } else {
            mob.getNavigation().stop();
            Vec3 mobPos = mob.position();
            Vec3 targetPos = target.position();
            Vec3 toTarget = targetPos.subtract(mobPos).normalize();
            Vec3 tangent = new Vec3(-toTarget.z, 0, toTarget.x).scale(circleDirection);

            circleSwitchTimer--;
            if (circleSwitchTimer <= 0 || mob.horizontalCollision) {
                circleDirection *= -1.0F;
                circleSwitchTimer = 15 + mob.getRandom().nextInt(25);
                if (mob.horizontalCollision && mob.onGround() && strafeJumpCooldown <= 0) {
                    mob.getJumpControl().jump();
                    strafeJumpCooldown = 10;
                }
            }
            if (strafeJumpCooldown > 0) strafeJumpCooldown--;

            Vec3 finalVelocity = Vec3.ZERO;

            if (postHitRetreatTimer > 0) {
                finalVelocity = toTarget.scale(-0.4); 
                postHitRetreatTimer--;
                if (postHitRetreatTimer <= 0) mob.releaseUsingItem();
            } else if (attackCooldown <= 3 && distance <= 4.0) {
                finalVelocity = toTarget.scale(0.55);
            } else {
                finalVelocity = finalVelocity.add(tangent.scale(0.35)); 
                if (distance > 2.8) {
                    finalVelocity = finalVelocity.add(toTarget.scale(0.38));
                } else if (distance < 2.3) {
                    finalVelocity = finalVelocity.add(toTarget.scale(-0.45));
                }
            }

            mob.setDeltaMovement(finalVelocity.x, mob.getDeltaMovement().y, finalVelocity.z);
        }

        // ==========================================
        // 全局“神仙走搭”保护 (免疫任何虚空与掉落)
        // ==========================================
        BlockPos posBelow = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
        if (mob.level().getBlockState(posBelow).canBeReplaced()) {
            mob.level().setBlock(posBelow, Blocks.COBBLESTONE.defaultBlockState(), 3);
            // 降低音效频率防止吵闹，但方块依然瞬间放置
            if (mob.getRandom().nextFloat() < 0.2f) {
                mob.level().playSound(null, posBelow, SoundType.STONE.getPlaceSound(), SoundSource.HOSTILE, 0.5F, 1.0F);
            }
        }

        // ==========================================
        // 攻击与后跳判定
        // ==========================================
        if (attackCooldown <= 0 && distance <= 3.2) {
            mob.swing(InteractionHand.MAIN_HAND);
            if (mob.doHurtTarget(target)) {
                postHitRetreatTimer = 8;
                if (mob.onGround()) mob.getJumpControl().jump(); 
                mob.startUsingItem(InteractionHand.OFF_HAND); 
            }
            attackCooldown = 8 + mob.getRandom().nextInt(5); 
        }

        if (attackCooldown > 0) attackCooldown--;
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        mob.getNavigation().stop();
    }
}
