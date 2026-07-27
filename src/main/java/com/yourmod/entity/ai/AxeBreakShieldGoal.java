package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 破盾战术：当目标玩家举盾时，怪物切换为下界合金斧进行攻击，
 * 利用斧子可破盾的原版机制，迫使玩家盾牌进入冷却。
 * 攻击完成后或目标不再举盾时，自动还原怪物原本的武器。
 */
public class AxeBreakShieldGoal extends Goal {

    private final CustomBipedEntity mob;
    private LivingEntity target;
    private int attackCooldown = 0;          // 攻击间隔计时器（模拟斧子慢速攻击）
    private static final int ATTACK_DELAY = 22; // 攻击冷却总刻度数（约 1.1 秒）

    public AxeBreakShieldGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * 激活条件：目标玩家存活、且正在举盾、且怪物与目标的水平距离 ≤ 3.5 格。
     * 满足条件时立刻保存并切换主手为下界合金斧。
     */
    @Override
    public boolean canUse() {
        LivingEntity potentialTarget = mob.getTarget();
        if (!(potentialTarget instanceof Player player) || !player.isAlive()) {
            return false;
        }
        // 玩家必须正在举盾（1.21 中用 isBlocking() 判断）
        if (!player.isBlocking()) {
            return false;
        }
        // 距离检查：必须在攻击范围以内才能开始（避免远距离无意义切换斧子）
        double distanceSqr = mob.distanceToSqr(player);
        if (distanceSqr > 3.5 * 3.5) {
            return false;
        }
        this.target = player;
        return true;
    }

    /**
     * Goal 开始时：保存原始主手物品，并将主手替换为下界合金斧。
     */
    @Override
    public void start() {
        attackCooldown = 0;
        mob.switchMainHandItem(new ItemStack(Items.NETHERITE_AXE));
    }

    /**
     * 每 tick 执行：朝向目标移动、保持视线、冷却攻击。
     */
    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            return;
        }

        // 1. 始终面朝目标
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // 2. 移动至攻击范围内
        double distanceSqr = mob.distanceToSqr(target);
        if (distanceSqr > 3.0 * 3.0) {
            // 距离太远，导航靠近
            mob.getNavigation().moveTo(target, 1.0D);
        } else {
            // 已经在攻击范围内，停止移动并准备攻击
            mob.getNavigation().stop();
        }

        // 3. 攻击冷却递减
        if (attackCooldown > 0) {
            attackCooldown--;
            return;
        }

        // 4. 距离足够时执行斧攻击（触发原版破盾）
        if (distanceSqr <= 3.0 * 3.0) {
            // 播放手臂挥动动画
            mob.swing(InteractionHand.MAIN_HAND);
            // 使用 doHurtTarget 进行正式攻击（会自动调用 player.hurt，原版会检测斧子并禁用盾牌）
            mob.doHurtTarget(target);
            // 重置攻击冷却
            attackCooldown = ATTACK_DELAY;
        }
    }

    /**
     * 持续条件：目标依然存活、依然是玩家、且依然在举盾。
     * 如果目标死亡、不再是玩家、或放下盾牌，则停止此 Goal。
     */
    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (!(target instanceof Player player)) {
            return false;
        }
        if (!player.isBlocking()) {
            return false;
        }
        return true;
    }

    /**
     * Goal 停止时：无条件还原怪物原本的武器，避免斧子滞留在主手。
     */
    @Override
    public void stop() {
        mob.restoreMainHandItem();
        attackCooldown = 0;
        target = null;
    }
}
