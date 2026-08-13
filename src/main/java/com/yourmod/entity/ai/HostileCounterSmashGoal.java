package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HostileCounterSmashGoal extends Goal {

    private final CustomBipedEntity mob;
    private int phase = 0;
    private float maxFallDistance = 0f;
    private ThrownEnderpearl activePearl;

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
        maxFallDistance = 0f;
        activePearl = null;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.yBodyRot = mob.getYHeadRot();

        if (phase == 0) {
            // ==========================================
            // 阶段 0：丢出真实的末影珍珠
            // ==========================================
            mob.getNavigation().stop();
            mob.switchMainHandItem(new ItemStack(Items.ENDER_PEARL));
            mob.swing(InteractionHand.MAIN_HAND);
            
            // 计算背离玩家的向量，并强行抬高角度 (抛物线拉开距离)
            Vec3 away = mob.position().subtract(target.position()).normalize();
            activePearl = new ThrownEnderpearl(mob.level(), mob);
            activePearl.setPos(mob.getEyePosition().x, mob.getEyePosition().y + 0.5, mob.getEyePosition().z);
            // 向上 0.8 的力度，确保能越过普通障碍物
            activePearl.shoot(away.x, 0.8, away.z, 1.5F, 0.0F);
            
            mob.level().addFreshEntity(activePearl);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
            
            mob.restoreMainHandItem();
            phase = 1;
            
        } else if (phase == 1) {
            // ==========================================
            // 阶段 1：等待珍珠落地与防窒息保护
            // ==========================================
            if (activePearl != null && !activePearl.isRemoved()) {
                // 珍珠还在飞，原地等待
                return;
            }
            
            // 落地传送后，强行破坏头部和脚部的阻挡方块，防止传进墙里卡死
            BlockPos pos = mob.blockPosition();
            for (int i = 0; i <= 1; i++) {
                BlockPos checkPos = pos.above(i);
                if (mob.level().getBlockState(checkPos).blocksMotion()) {
                    mob.level().destroyBlock(checkPos, true, mob);
                }
            }
            phase = 2;
            
        } else if (phase == 2) {
            // ==========================================
            // 阶段 2：虚空走搭逼近 (无视地形)
            // ==========================================
            double distanceSqr = mob.distanceToSqr(target);
            if (distanceSqr <= 25.0D) { // 进入 5 格范围，启动重锤
                phase = 3;
                return;
            }
            
            Vec3 dir = target.position().subtract(mob.position()).normalize();
            mob.setDeltaMovement(dir.x * 0.45, mob.getDeltaMovement().y, dir.z * 0.45);

            // 撞墙起跳
            if (mob.horizontalCollision && mob.onGround()) {
                mob.getJumpControl().jump();
            }

            // ★ 核心：脚底踩空立刻垫方块
            BlockPos posBelow = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
            if (mob.level().getBlockState(posBelow).canBeReplaced()) {
                mob.level().setBlock(posBelow, Blocks.COBBLESTONE.defaultBlockState(), 3);
                mob.level().playSound(null, posBelow, SoundType.STONE.getPlaceSound(), SoundSource.HOSTILE, 1.0F, 0.8F);
            }
            
        } else if (phase == 3) {
            // ==========================================
            // 阶段 3：风弹起飞 + 落地重锤
            // ==========================================
            if (!mob.onGround() && maxFallDistance == 0f) {
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
                if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
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

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        mob.resetPlayerHitCount(); 
        phase = 0;
        maxFallDistance = 0f;
        activePearl = null;
    }
}
