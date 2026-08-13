package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 纯数学物理走位 AI (电竞级 PVP 模块)
 * 完全抛弃原版 MoveControl，直接操作三维向量与动能，实现 0 延迟微操。
 */
public class HostileAdvancedCombatGoal extends Goal {
    
    private final CustomBipedEntity mob;
    private final double speedModifier;
    
    // 连招与冷却
    private int attackCooldown = 0;
    
    // 自定义物理走位状态
    private float circleDirection = 1.0F; // 1 = 顺时针, -1 = 逆时针
    private int circleSwitchTimer = 0;
    private int postHitRetreatTimer = 0;  // 攻击后强制后撤 (S-tap) 的计时器
    private int strafeJumpCooldown = 0;   // 防止连续跳跃卡死

    public HostileAdvancedCombatGoal(CustomBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        // 剥夺原版底层 AI 的移动权限，完全由这个 Goal 接管
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

        // 1. 视线 100% 锁死（爆头线）
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // ★ 核心修复：强制身体旋转与头部一致，防止脱离 MoveControl 后出现“背身走位”的穿模现象
        mob.yBodyRot = mob.getYHeadRot(); 
        
        double distance = Math.sqrt(mob.distanceToSqr(target));

        // 2. 武器识别 (面对斧子直接放下盾牌，防止被破盾僵直)
        if (target instanceof Player player) {
            ItemStack playerItem = player.getMainHandItem();
            if (playerItem.getItem() instanceof AxeItem && mob.isUsingItem()) {
                mob.releaseUsingItem();
            }
        }

        // ==========================================
        // 3. 核心：纯数学向量级 PVP 走位 (无闲置状态)
        // ==========================================
        
        // 如果距离大于 8 格，利用原版寻路快速拉近距离（适应复杂地形）
        if (distance > 8.0 && postHitRetreatTimer <= 0) {
            mob.getNavigation().moveTo(target, speedModifier * 1.2);
        } else {
            // 逼近到 8 格以内，关闭寻路，接管底层物理引擎！
            mob.getNavigation().stop();
            
            // 计算指向玩家的标准向量
            Vec3 mobPos = mob.position();
            Vec3 targetPos = target.position();
            Vec3 toTarget = targetPos.subtract(mobPos).normalize();
            
            // 计算圆环横移的切线向量
            Vec3 tangent = new Vec3(-toTarget.z, 0, toTarget.x).scale(circleDirection);

            // 定时或撞墙时改变横向走位方向 (走 A 变向)
            circleSwitchTimer--;
            if (circleSwitchTimer <= 0 || mob.horizontalCollision) {
                circleDirection *= -1.0F;
                circleSwitchTimer = 15 + mob.getRandom().nextInt(25); // 随机 0.75秒~2秒 变向一次
                
                // 撞墙时触发智能跳跃，防止卡墙角
                if (mob.horizontalCollision && mob.onGround() && strafeJumpCooldown <= 0) {
                    mob.getJumpControl().jump();
                    strafeJumpCooldown = 10;
                }
            }
            if (strafeJumpCooldown > 0) strafeJumpCooldown--;

            // 构建最终的运动向量
            Vec3 finalVelocity = Vec3.ZERO;

            if (postHitRetreatTimer > 0) {
                // [战术后仰 (S-Tap)]：刚打完一刀，立刻挂倒挡高速后撤拉开距离
                finalVelocity = toTarget.scale(-0.45);
                postHitRetreatTimer--;
            } else if (attackCooldown <= 3 && distance <= 4.0) {
                // [突进斩 (W-Tap)]：攻击即将就绪，瞬间爆发向前冲刺，锁定目标
                finalVelocity = toTarget.scale(0.5);
            } else {
                // [极限卡距离 (Spacing & Strafing)]：永远保持在 2.5 ~ 3.0 的危险边缘
                finalVelocity = finalVelocity.add(tangent.scale(0.3)); // 永远在左右横跳
                
                if (distance > 2.8) {
                    // 玩家后退，压迫跟进
                    finalVelocity = finalVelocity.add(toTarget.scale(0.35));
                } else if (distance < 2.3) {
                    // 玩家前压，立刻后撤，把你控在原版 3.0 的极限盲区
                    finalVelocity = finalVelocity.add(toTarget.scale(-0.4));
                }
            }

            // 应用计算好的物理向量 (必须保留 Y 轴动能以支持跳跃和重力)
            mob.setDeltaMovement(finalVelocity.x, mob.getDeltaMovement().y, finalVelocity.z);
        }

        // ==========================================
        // 4. 精准打击系统 (Hit & Run)
        // ==========================================
        // 原版极限攻击距离是 3.0，考虑动态 hitbox 交集，设为 3.2 触发
        if (attackCooldown <= 0 && distance <= 3.2) {
            mob.swing(InteractionHand.MAIN_HAND);
            // 发动攻击
            if (mob.doHurtTarget(target)) {
                // 核心：如果砍中了，立刻重置走位状态，开启 S-tap 拉扯 (强制后退 6 ticks)
                postHitRetreatTimer = 6;
            }
            // 攻击冷却，加入轻微随机性模拟人类按键节奏
            attackCooldown = 10 + mob.getRandom().nextInt(4); 
        }

        if (attackCooldown > 0) attackCooldown--;
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        mob.getNavigation().stop();
        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0); // 彻底物理刹车
    }
}
