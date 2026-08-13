package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class HostileCounterSmashGoal extends Goal {

    private final CustomBipedEntity mob;
    private int phase = 0;
    private int tickDelay = 0;
    private float maxFallDistance = 0f;
    private int buildCooldown = 0;

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
        buildCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (phase == 0) {
            // ==========================================
            // 阶段 0：视觉假动作 (丢珍珠音效+挥手，但不生成实体)
            // ==========================================
            mob.getNavigation().stop();
            mob.switchMainHandItem(new ItemStack(Items.ENDER_PEARL));
            mob.swing(InteractionHand.MAIN_HAND);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
            
            mob.restoreMainHandItem();
            phase = 1;
            tickDelay = 15; // 等待 0.75 秒
            
        } else if (phase == 1) {
            // ==========================================
            // 阶段 1：智能安全传送 (彻底修复卡地里的 Bug)
            // ==========================================
            if (tickDelay > 0) {
                tickDelay--;
            } else {
                Vec3 away = mob.position().subtract(target.position()).normalize();
                boolean teleported = false;
                
                // 尝试 10 次，寻找一个远离玩家 12~16 格的绝对安全落脚点
                for (int i = 0; i < 10; i++) {
                    double targetX = mob.getX() + away.x * 12.0 + (mob.getRandom().nextDouble() - 0.5) * 8.0;
                    double targetY = mob.getY() + (mob.getRandom().nextDouble() - 0.5) * 8.0;
                    double targetZ = mob.getZ() + away.z * 12.0 + (mob.getRandom().nextDouble() - 0.5) * 8.0;
                    
                    // randomTeleport 自带物理碰撞安全检查，防窒息
                    if (mob.randomTeleport(targetX, targetY, targetZ, true)) {
                        teleported = true;
                        break;
                    }
                }
                
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
                phase = teleported ? 2 : 2; // 就算没传出去也强制进下个阶段
            }
            
        } else if (phase == 2) {
            // 阶段 2：强制走搭逼近 (逻辑不变)
            double distanceSqr = mob.distanceToSqr(target);
            if (distanceSqr <= 25.0D) {
                phase = 3;
                return;
            }
            Vec3 dir = target.position().subtract(mob.position()).normalize();
            mob.setDeltaMovement(dir.x * 0.35, mob.getDeltaMovement().y, dir.z * 0.35);

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
            // 阶段 3：风弹起飞 + 落地重锤 (逻辑不变)
            if (!mob.onGround() && maxFallDistance == 0f) {
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
                if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.GUST, mob.getX(), mob.getY(), mob.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
                }
                Vec3 lunge = target.position().subtract(mob.position()).normalize().scale(0.3);
                mob.setDeltaMovement(lunge.x, 1.6, lunge.z);
                mob.switchMainHandItem(new ItemStack(Items.MACE));
                maxFallDistance = 0.1f;
            } else if (maxFallDistance > 0f) {
                if (!mob.onGround()) {
                    maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
                    Vec3 adjust = target.position().subtract(mob.position()).normalize().scale(0.06);
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
    }
}
