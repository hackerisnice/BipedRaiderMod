package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 渔竿控速与破疾跑连击 AI (Fishing Rod Pull Combo)
 * 触发距离：4.0 ~ 7.5 格 (中距离拉扯区)
 */
public class HostileFishingRodGoal extends Goal {

    private final CustomBipedEntity mob;
    private int cooldown = 0;
    private int phaseTicks = 0;
    private boolean isHooking = false;

    public HostileFishingRodGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        // 仅在 4 ~ 7.5 格的中距离拉扯区间触发
        double distanceSqr = mob.distanceToSqr(target);
        if (distanceSqr < 16.0D || distanceSqr > 56.25D) return false;

        // 必须拥有直视视线
        return mob.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        return isHooking && mob.getTarget() != null && mob.getTarget().isAlive();
    }

    @Override
    public void start() {
        isHooking = true;
        phaseTicks = 0;

        // 1. 瞬切主手为钓鱼竿并甩杆
        mob.switchMainHandItem(new ItemStack(Items.FISHING_ROD));
        mob.swing(InteractionHand.MAIN_HAND);
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 1.0F, 1.2F);
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.yBodyRot = mob.getYHeadRot();
        phaseTicks++;

        if (phaseTicks == 4) {
            // 2. 判定钩中目标：收杆音效 + 强力向量拉拽
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 1.0F, 1.0F);
            mob.swing(InteractionHand.MAIN_HAND);

            // 计算将玩家拉向 Boss 的水平与垂直分量向量
            Vec3 pullVec = mob.position().subtract(target.position()).normalize();
            pullVec = pullVec.scale(0.55).add(0, 0.25, 0); // 赋予轻微击飞+强拉效果

            target.setDeltaMovement(pullVec.x, pullVec.y, pullVec.z);
            target.hurtMarked = true; // 强制服务端同步玩家位移，打破玩家疾跑

            if (target instanceof Player player) {
                player.setSprinting(false); // 强制打断疾跑状态
            }

        } else if (phaseTicks == 7) {
            // 3. 瞬间切回钻石剑，Boss 自身向前突进爆发，准备接平砍
            mob.restoreMainHandItem();

            Vec3 lungeVec = target.position().subtract(mob.position()).normalize().scale(0.48);
            mob.setDeltaMovement(lungeVec.x, mob.getDeltaMovement().y, lungeVec.z);

        } else if (phaseTicks >= 10) {
            // 4. 连招结束，退出状态机
            isHooking = false;
        }
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        isHooking = false;
        phaseTicks = 0;
        // 设定 6~8 秒冷却时间，防止无限勾人
        cooldown = 120 + mob.getRandom().nextInt(40);
    }
}
