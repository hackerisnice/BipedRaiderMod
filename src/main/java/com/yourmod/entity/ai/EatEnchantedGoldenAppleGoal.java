package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

/**
 * 低血量应急进食：当怪物生命值低于最大生命值的 30% 时，
 * 立刻切换为附魔金苹果并开始食用，吃完后自动还原原始武器。
 *
 * 进食过程中怪物会停止移动，抬起手臂播放使用物品动画。
 * 若在进食期间被攻击或目标条件变化，Goal 会安全中断并还原武器，
 * 绝不留斧头（或其他武器）滞留在主手。
 */
public class EatEnchantedGoldenAppleGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private int eatingTicks = 0;                 // 已进食的 Tick 计数
    private boolean hasStartedEating = false;   // 是否已开始使用物品
    private static final int EAT_DURATION = 32; // 附魔金苹果食用总时长（Ticks）
    private static final float HEAL_THRESHOLD = 0.3F; // 生命值阈值：30% 触发

    public EatEnchantedGoldenAppleGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); // 允许移动和视线，但进食时会强制停下
    }

    /**
     * 激活条件：
     * 1. 怪物生命值比例低于 30%。
     * 2. 怪物当前没有在使用物品（防止重复触发）。
     * 3. 可选：距离上次使用苹果已过冷却（可通过外部冷却控制，此处未加）。 
     */
    @Override
    public boolean canUse() {
        // 血量检查
        float healthPercent = mob.getHealth() / mob.getMaxHealth();
        if (healthPercent >= HEAL_THRESHOLD) {
            return false;
        }
        // 已在进食中则不再重复激活
        if (mob.isUsingItem()) {
            return false;
        }
        // 确保没有其他高优先级行为正在执行（比如正在逃出船、破盾等）
        // 但 Goal 系统自身会处理优先级，这里只需条件符合
        return true;
    }

    /**
     * Goal 启动：保存原始武器，切换附魔金苹果，立即开始食用。
     */
    @Override
    public void start() {
        // 1. 保存并切换主手物品
        mob.switchMainHandItem(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));

        // 2. 开始使用物品（手臂抬起动画，客户端可看到苹果）
        mob.startUsingItem(InteractionHand.MAIN_HAND);
        hasStartedEating = true;
        eatingTicks = 0;

        // 3. 进食期间停止一切移动，确保不会因走路打断使用
        mob.getNavigation().stop();
    }

    /**
     * 每 Tick 执行：保持使用状态，直到进食完成或被打断。
     */
    @Override
    public void tick() {
        // 如果因为某些原因目标已无效，直接中断
        if (!hasStartedEating) {
            return;
        }

        // 持续停止移动，防止走路打断进食动画
        mob.getNavigation().stop();

        // 维持视线方向（可看向当前目标或随机方向）
        if (mob.getTarget() != null) {
            mob.getLookControl().setLookAt(mob.getTarget(), 30.0F, 30.0F);
        }

        eatingTicks++;

        // 检查是否已经吃完：当怪物不再使用物品，且主手物品被消耗（变为空）
        if (!mob.isUsingItem()) {
            // 如果主手物品已经是空，说明附魔金苹果已被成功吃掉
            if (mob.getMainHandItem().isEmpty()) {
                // 进食完成，应用效果由游戏内部自动触发（苹果本身的效果）
                // 还原武器并标记结束
                mob.restoreMainHandItem();
                hasStartedEating = false;
                eatingTicks = 0;
                return;
            } else {
                // 使用被打断（例如受到伤害），物品未被消耗，也应还原武器并退出
                mob.restoreMainHandItem();
                hasStartedEating = false;
                eatingTicks = 0;
            }
        }

        // 安全阈值：如果进食时间超过最大持续时间的 1.5 倍仍未结束，强制中断
        if (eatingTicks > EAT_DURATION * 1.5) {
            mob.releaseUsingItem();        // 强制结束使用
            mob.restoreMainHandItem();
            hasStartedEating = false;
            eatingTicks = 0;
        }
    }

    /**
     * 是否可继续：只有在主动进食过程中且怪物生命值仍低于 50%（防止刚吃一口就因回血停止），
     * 并且原始武器保存有效时才继续。如果目标消失或血量已满，应终止。
     */
    @Override
    public boolean canContinueToUse() {
        // 停止条件：生命值恢复到 50% 以上或死亡
        if (mob.getHealth() / mob.getMaxHealth() > 0.5F || !mob.isAlive()) {
            return false;
        }
        // 如果尚未开始进食或进食已结束，不再继续
        return hasStartedEating;
    }

    /**
     * 无论何种原因退出，必须还原武器。
     * 还原操作调用 restoreMainHandItem，内部已处理重复还原保护。
     */
    @Override
    public void stop() {
        mob.restoreMainHandItem();
        hasStartedEating = false;
        eatingTicks = 0;
        // 防止遗留使用状态
        if (mob.isUsingItem()) {
            mob.releaseUsingItem();
        }
        // 允许怪物恢复移动
        mob.getNavigation().stop();
    }
}
