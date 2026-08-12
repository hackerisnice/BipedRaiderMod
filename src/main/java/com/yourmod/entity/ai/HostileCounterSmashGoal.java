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

/**
 * 挨打反击系统：被打3下后触发。
 * Phase 0: 丢珍珠拉开距离
 * Phase 1: 等待传送完成
 * Phase 2: 走搭逼近
 * Phase 3: 升龙重锤！
 */
public class HostileCounterSmashGoal extends Goal {

    private final CustomBipedEntity mob;
    private int phase = 0;
    private int tickDelay = 0;
    private float maxFallDistance = 0f;
    private int buildCooldown = 0;

    public HostileCounterSmashGoal(CustomBipedEntity mob) {
        this.mob = mob;
        // 接管移动和视线
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        // ★ 触发条件：目标存活，且累计被打次数 >= 3
        return target != null && target.isAlive() && mob.getPlayerHitCount() >= 3;
    }

    @Override
    public boolean canContinueToUse() {
        // 只要还没执行完阶段 4 (结束阶段)，并且目标还在，就一直锁定执行
        return phase < 4 && mob.getTarget() != null && mob.getTarget().isAlive();
    }

    @Override
    public void start() {
        phase = 0;
        tickDelay = 0;
        maxFallDistance = 0f;
        buildCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (phase == 0) {
            // ==========================================
            // 阶段 0：丢珍珠战术后撤
            // ==========================================
            mob.getNavigation().stop();
            mob.switchMainHandItem(new ItemStack(Items.ENDER_PEARL));
            mob.swing(InteractionHand.MAIN_HAND);
            
            // 计算背离玩家的向量 (往后上方丢)
            Vec3 awayVec = mob.position().subtract(target.position()).normalize();
            awayVec = awayVec.scale(1.5).add(0, 0.6, 0); 

            ThrownEnderpearl pearl = new ThrownEnderpearl(mob.level(), mob);
            pearl.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.1, mob.getEyePosition().z);
            pearl.shoot(awayVec.x, awayVec.y, awayVec.z, 1.5F, 0.0F);
            
            mob.level().addFreshEntity(pearl);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);

            mob.restoreMainHandItem();
            phase = 1;
            tickDelay = 25; // 预留 1.25 秒让珍珠飞行和落地
            
        } else if (phase == 1) {
            // ==========================================
            // 阶段 1：等待传送
            // ==========================================
            if (tickDelay > 0) {
                tickDelay--;
            } else {
                phase = 2; // 传送落地，转入走搭阶段
            }
            
        } else if (phase == 2) {
            // ==========================================
            // 阶段 2：强制走搭逼近
            // ==========================================
            double dx = target.getX() - mob.getX();
            double dz = target.getZ() - mob.getZ();
            double horizDist = Math.sqrt(dx * dx + dz * dz);

            // 逼近到 5 格以内，立刻切入重锤绝杀！
            if (horizDist <= 5.0) {
                phase = 3;
                return;
            }

            // 走搭逻辑
            Vec3 dir = target.position().subtract(mob.position()).normalize();
            mob.setDeltaMovement(dir.x * 0.35, mob.getDeltaMovement().y, dir.z * 0.35);

            // 只要没目标高出 4 格，就一直垫脚
            if (mob.getY() < target.getY() + 4.0) {
                if (mob.onGround()) {
                    mob.getJumpControl().jump();
                    buildCooldown = 1;
                } else if (buildCooldown > 0) {
                    buildCooldown++;
                    if (buildCooldown >= 4) {
                        BlockPos placePos = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
                        if (mob.level().getBlockState(placePos).canBeReplaced()) {
                            mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
                            mob.level().setBlock(placePos, Blocks.COBBLESTONE.defaultBlockState(), 3);
                            mob.swing(InteractionHand.MAIN_HAND);
                            mob.level().playSound(null, placePos, SoundType.STONE.getPlaceSound(), SoundSource.HOSTILE, 1.0F, 0.8F);
                            mob.restoreMainHandItem();
                        }
                        buildCooldown = 0;
                    }
                }
            }
            
        } else if (phase == 3) {
            // ==========================================
            // 阶段 3：风弹起飞 + 落地重锤
            // ==========================================
            if (!mob.onGround() && maxFallDistance == 0f) {
                // 引爆风弹，给足起飞初速度
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
                if (mob.level() instanceof net.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.GUST, mob.getX(), mob.getY(), mob.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
                }
                
                Vec3 lunge = target.position().subtract(mob.position()).normalize().scale(0.3);
                mob.setDeltaMovement(lunge.x, 1.6, lunge.z);
                
                mob.switchMainHandItem(new ItemStack(Items.MACE));
                maxFallDistance = 0.1f; // 标记已起飞
            } else if (maxFallDistance > 0f) {
                // 空中追踪与落地判定
                if (!mob.onGround()) {
                    maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
                    Vec3 adjust = target.position().subtract(mob.position()).normalize().scale(0.06);
                    mob.setDeltaMovement(mob.getDeltaMovement().add(adjust.x, 0, adjust.z));
                } else {
                    // 落地瞬间判定伤害
                    if (maxFallDistance > 1.0f) {
                        mob.level().playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
                        mob.swing(InteractionHand.MAIN_HAND);
                        target.hurt(mob.damageSources().mobAttack(mob), 8.0f + maxFallDistance * 1.5f);
                    }
                    mob.restoreMainHandItem();
                    phase = 4; // 标记连招结束
                }
            }
        }
    }

    @Override
    public void stop() {
        // ★ 无论如何停止（目标死亡或者连招打完），重置一切状态
        mob.restoreMainHandItem();
        mob.resetPlayerHitCount(); // 清零挨打计数器，重新开始攒怒气
        phase = 0;
        maxFallDistance = 0f;
    }
}
