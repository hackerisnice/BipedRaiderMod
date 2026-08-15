package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 战术受击绝杀状态机 (修复拉扯丢失与虚空卡墙 Bug)
 */
public class HostileCounterSmashGoal extends Goal {

    private final CustomBipedEntity mob;
    private int phase = 0;
    private int tickDelay = 0;
    private float maxFallDistance = 0f;
    private Vec3 safeRetreatPos = null;

    public HostileCounterSmashGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && mob.getPlayerHitCount() >= 3;
    }

    @Override
    public boolean canContinueToUse() {
        return phase < 4 && mob.getTarget() != null && mob.getTarget().isAlive();
    }

    @Override
    public void start() {
        phase = 0;
        tickDelay = 0;
        maxFallDistance = 0f;
        safeRetreatPos = null;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.yBodyRot = mob.getYHeadRot();

        if (phase == 0) {
            // ==========================================
            // 阶段 0：雷达寻找 8~10 格内的绝对安全后撤点
            // ==========================================
            mob.getNavigation().stop();
            mob.switchMainHandItem(new ItemStack(Items.ENDER_PEARL));
            mob.swing(InteractionHand.MAIN_HAND);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);

            Vec3 awayDir = mob.position().subtract(target.position()).normalize();
            // 如果与玩家重合，随机一个方向
            if (awayDir.lengthSqr() < 0.001) {
                awayDir = new Vec3(1, 0, 0);
            }

            safeRetreatPos = findSafeRetreatPosition(awayDir);
            
            mob.restoreMainHandItem();
            phase = 1;
            tickDelay = 6; // 延迟 0.3 秒，给予玩家视觉反应时间

        } else if (phase == 1) {
            // ==========================================
            // 阶段 1：精准战术瞬移（杜绝飞出视野）
            // ==========================================
            if (tickDelay > 0) {
                tickDelay--;
                return;
            }

            if (safeRetreatPos != null) {
                // 生成传送起点与终点粒子效果
                if (mob.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1.0, mob.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
                    serverLevel.sendParticles(ParticleTypes.PORTAL, safeRetreatPos.x, safeRetreatPos.y + 1.0, safeRetreatPos.z, 20, 0.3, 0.5, 0.3, 0.1);
                }
                // 执行精准传送
                mob.teleportTo(safeRetreatPos.x, safeRetreatPos.y, safeRetreatPos.z);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            phase = 2;

        } else if (phase == 2) {
            // ==========================================
            // 阶段 2：虚空走搭追击
            // ==========================================
            double distanceSqr = mob.distanceToSqr(target);
            if (distanceSqr <= 25.0D) { // 接近到 5 格，开启重锤
                phase = 3;
                return;
            }

            Vec3 dir = target.position().subtract(mob.position()).normalize();
            mob.setDeltaMovement(dir.x * 0.45, mob.getDeltaMovement().y, dir.z * 0.45);

            if (mob.horizontalCollision && mob.onGround()) {
                mob.getJumpControl().jump();
            }

            // 无论处于何种高度，脚下踩空立刻自动垫方块
            BlockPos posBelow = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
            if (mob.level().getBlockState(posBelow).canBeReplaced()) {
                mob.level().setBlock(posBelow, Blocks.COBBLESTONE.defaultBlockState(), 3);
                mob.level().playSound(null, posBelow, SoundType.STONE.getPlaceSound(), SoundSource.HOSTILE, 0.8F, 1.0F);
            }

        } else if (phase == 3) {
            // ==========================================
            // 阶段 3：风弹起飞 + 落地重锤
            // ==========================================
            if (!mob.onGround() && maxFallDistance == 0f) {
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
                if (mob.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.GUST, mob.getX(), mob.getY(), mob.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
                }
                Vec3 lunge = target.position().subtract(mob.position()).normalize().scale(0.35);
                mob.setDeltaMovement(lunge.x, 1.6, lunge.z);
                mob.switchMainHandItem(new ItemStack(Items.MACE));
                maxFallDistance = 0.1f;
            } else if (maxFallDistance > 0f) {
                if (!mob.onGround()) {
                    maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
                    Vec3 adjust = target.position().subtract(mob.position()).normalize().scale(0.08);
                    mob.setDeltaMovement(mob.getDeltaMovement().add(adjust.x, 0, adjust.z));
                } else {
                    if (maxFallDistance > 1.0f) {
                        mob.level().playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
                        mob.swing(InteractionHand.MAIN_HAND);
                        target.hurt(mob.damageSources().mobAttack(mob), 8.0f + maxFallDistance * 1.5f);
                    }
                    mob.restoreMainHandItem();
                    phase = 4;
                }
            }
        }
    }

    /**
     * 安全落脚点雷达算法：在 8~10 格范围内寻找有实心方块且上方为空气的安全坐标
     */
    private Vec3 findSafeRetreatPosition(Vec3 awayDir) {
        double retreatDist = 9.0D; // 锁定后撤距离为 9 格
        double targetX = mob.getX() + awayDir.x * retreatDist;
        double targetZ = mob.getZ() + awayDir.z * retreatDist;
        int baseY = mob.getBlockY();

        // 在 Y 轴上下 4 格范围内寻找实心地面
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos groundPos = BlockPos.containing(targetX, baseY + dy, targetZ);
            BlockPos headPos1 = groundPos.above();
            BlockPos headPos2 = groundPos.above(2);

            BlockState groundState = mob.level().getBlockState(groundPos);
            BlockState head1State = mob.level().getBlockState(headPos1);
            BlockState head2State = mob.level().getBlockState(headPos2);

            // 地面必须能站立，头部两格必须无阻挡
            if (groundState.blocksMotion() && !head1State.blocksMotion() && !head2State.blocksMotion()) {
                return new Vec3(targetX, baseY + dy + 1.0, targetZ);
            }
        }

        // 如果未找到（如悬崖或虚空），在脚下虚空生成垫脚石并返回
        BlockPos fallbackPos = BlockPos.containing(targetX, baseY, targetZ);
        mob.level().setBlock(fallbackPos.below(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        return new Vec3(targetX, baseY, targetZ);
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        mob.resetPlayerHitCount(); 
        phase = 0;
        maxFallDistance = 0f;
        safeRetreatPos = null;
    }
}
