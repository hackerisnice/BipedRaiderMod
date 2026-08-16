package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 蛛网束缚 + TNT 点杀 + 举盾防爆状态机
 */
public class HostileCobwebTrapGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private LivingEntity target;
    private PrimedTnt activeTnt;
    private int cooldown = 0;
    private int phase = 0;
    private int shieldTimer = 0;

    public HostileCobwebTrapGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        // 仅在 4.5 格近战距离内触发
        if (mob.distanceToSqr(target) > 20.25) return false;
        if (!target.onGround()) return false;

        BlockPos targetPos = target.blockPosition();
        return level.getBlockState(targetPos).canBeReplaced();
    }

    @Override
    public boolean canContinueToUse() {
        // 只要 TNT 还未爆炸，Goal 绝不打断，全程保持盾牌防御姿态
        return phase > 0 && target != null && target.isAlive();
    }

    @Override
    public void start() {
        phase = 0;
        shieldTimer = 0;
        activeTnt = null;

        BlockPos targetPos = target.blockPosition();

        // 1. 脚底放蜘蛛网
        mob.switchMainHandItem(new ItemStack(Items.COBWEB));
        mob.swing(InteractionHand.MAIN_HAND);
        level.setBlock(targetPos, Blocks.COBWEB.defaultBlockState(), 3);
        level.playSound(null, targetPos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.HOSTILE, 1.0F, 1.0F);

        // 2. 寻找目标身旁的合法空位放置并激活 TNT
        BlockPos tntPos = targetPos.north();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos check = targetPos.relative(dir);
            if (level.getBlockState(check).canBeReplaced()) {
                tntPos = check;
                break;
            }
        }

        mob.switchMainHandItem(new ItemStack(Items.TNT));
        mob.swing(InteractionHand.MAIN_HAND);

        // 生成点燃的 TNT (引信 45 刻，约 2.25 秒)
        activeTnt = new PrimedTnt(level, tntPos.getX() + 0.5, tntPos.getY(), tntPos.getZ() + 0.5, mob);
        activeTnt.setFuse(45);
        level.addFreshEntity(activeTnt);
        level.playSound(null, tntPos, SoundEvents.TNT_PRIME, SoundSource.HOSTILE, 1.0F, 1.0F);

        mob.restoreMainHandItem();

        // 3. 转入举盾防爆阶段
        phase = 1;
        shieldTimer = 55; // 预留略长于引信的时间确保防爆成功

        // 小幅度后撤拉开距离，进入防御姿态
        Vec3 awayFromTnt = mob.position().subtract(activeTnt.position()).normalize();
        mob.setDeltaMovement(awayFromTnt.x * 0.3, mob.getDeltaMovement().y, awayFromTnt.z * 0.3);
        mob.startUsingItem(InteractionHand.OFF_HAND);
    }

    @Override
    public void tick() {
        if (phase == 1) {
            // 视线与身体绝对死锁 TNT 爆心，触发原版盾牌正面格挡爆炸机制
            if (activeTnt != null && activeTnt.isAlive()) {
                mob.getLookControl().setLookAt(activeTnt, 30.0F, 30.0F);
            } else if (target != null) {
                mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            mob.yBodyRot = mob.getYHeadRot();

            // 保持举盾姿态
            if (!mob.isUsingItem()) {
                mob.startUsingItem(InteractionHand.OFF_HAND);
            }

            shieldTimer--;
            // TNT 已经爆炸（被移除）或计时结束
            if ((activeTnt == null || activeTnt.isRemoved()) && shieldTimer <= 10) {
                phase = 0;
            }
        }
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) {
            mob.releaseUsingItem();
        }
        mob.restoreMainHandItem();
        activeTnt = null;
        phase = 0;
        shieldTimer = 0;
        // 连招冷却 12 秒
        cooldown = 240;
    }
}
