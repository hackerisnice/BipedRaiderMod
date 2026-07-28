package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;

public class EscapeBoatAndBuildUpGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private final double speedModifier;
    
    private int boatAttackTimer = 0;
    private boolean wasOnGround = true;
    
    // 搭高状态计时器
    private int buildCooldown = 0;
    private int buildJumpTimer = 0; // 用于实现“起跳后延迟放置方块”
    
    // 重锤状态记录
    private float maxFallDistance = 0f;
    private boolean isMaceAttacking = false;

    public EscapeBoatAndBuildUpGoal(CustomBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.level = mob.level();
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (mob.isPassenger() && mob.getVehicle() instanceof Boat) return true;
        
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        
        // 目标在上方2格以上时触发搭高
        if (target.getY() > mob.getY() + 2 && mob.distanceToSqr(target) < 16.0 * 16.0) return true;

        // 空中下落触发重锤
        if (!mob.onGround() && mob.fallDistance > 1.5f && target.distanceToSqr(mob) < 25.0) return true;
        
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        // ★ 核心修复1：重锤落地那一帧，强行续命，防止 AI 直接取消
        if (isMaceAttacking && !wasOnGround && mob.onGround()) {
            return true;
        }
        // ★ 核心修复2：正在起跳腾空的过程中，绝不允许被其他 AI 打断
        if (buildJumpTimer > 0) {
            return true;
        }
        return canUse();
    }

    @Override
    public void start() {
        wasOnGround = mob.onGround();
        boatAttackTimer = 0;
        maxFallDistance = 0f;
        isMaceAttacking = false;
        buildJumpTimer = 0;
    }

    @Override
    public void tick() {
        // === 1. 船脱困逻辑 ===
        if (mob.isPassenger() && mob.getVehicle() instanceof Boat boat) {
            mob.stopRiding();
            if (boatAttackTimer <= 0) {
                mob.swing(InteractionHand.MAIN_HAND);
                boat.hurt(mob.damageSources().mobAttack(mob), 5.0F);
                boatAttackTimer = 10;
            } else {
                boatAttackTimer--;
            }
            return;
        }

        LivingEntity target = mob.getTarget();
        if (target == null) return;

        // === 2. 搭高逻辑 (修复腾空与防挤出机制) ===
        // 如果已经开始跳跃了，则无视目标状态，强行把这个方块垫完
        if (buildJumpTimer > 0) {
            buildJumpTimer++;
            
            // ★ 关键1：持续锁死水平速度，强行停止寻路，只允许垂直起降
            mob.getNavigation().stop();
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);

            // ★ 关键2：等待到第 5 Tick，此时怪物的真实坐标已经腾空超过1格
            if (buildJumpTimer >= 5) {
                // 精准获取当前脚底往下一点的方块坐标 (1.21 新写法)
                BlockPos posUnderFeet = BlockPos.containing(mob.getX(), mob.getY() - 0.1, mob.getZ());
                
                // 确保脚下有空间可以放置
                if (level.getBlockState(posUnderFeet).canBeReplaced()) {
                    level.setBlock(posUnderFeet, Blocks.COBBLESTONE.defaultBlockState(), 3);
                    level.playSound(null, posUnderFeet, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    mob.swing(InteractionHand.MAIN_HAND);
                }
                buildCooldown = 12; // 0.6秒的搭建冷却，防止垫得太快被卡住
                mob.restoreMainHandItem();
                buildJumpTimer = 0; // 重置跳跃计时，准备下一轮
            }
        } 
        // 还没起跳，且目标在上方，开始起跳
        else if (target.getY() > mob.getY() + 2 && buildCooldown <= 0) {
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
            
            if (mob.onGround()) {
                mob.jumpFromGround();
                buildJumpTimer = 1; // 正式启动腾空计时器
            }
        }
        
        // 冷却递减
        if (buildCooldown > 0 && buildJumpTimer == 0) {
            buildCooldown--;
        }

        // === 3. 重锤下落攻击 ===
        if (!mob.onGround()) {
            maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
            if (maxFallDistance > 1.5f && !isMaceAttacking && buildJumpTimer == 0) {
                mob.switchMainHandItem(new ItemStack(Items.MACE));
                isMaceAttacking = true;
            }
        } else {
            // 落地瞬间的重击判定
            if (!wasOnGround && isMaceAttacking) {
                if (maxFallDistance > 1.5f && target.distanceToSqr(mob) < 16.0) {
                    mob.swing(InteractionHand.MAIN_HAND);
                    float baseDamage = (float) mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                    float fallBonus = Math.min(maxFallDistance, 20) * 1.5F;
                    float totalDamage = baseDamage + fallBonus + 3.0F;

                    target.hurt(mob.damageSources().mobAttack(mob), totalDamage);
                    level.playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                mob.restoreMainHandItem();
                isMaceAttacking = false;
                maxFallDistance = 0f;
            }
        }
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        boatAttackTimer = 0;
        buildCooldown = 0;
        buildJumpTimer = 0;
        isMaceAttacking = false;
    }
}
