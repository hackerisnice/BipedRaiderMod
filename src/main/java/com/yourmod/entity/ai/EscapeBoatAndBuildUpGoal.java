package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 综合战术：被船困住时跳出并破坏船；玩家高处时搭高追击；高空下落时用重锤砸击。
 */
public class EscapeBoatAndBuildUpGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private final double speedModifier;
    private int boatAttackTimer = 0;
    private boolean wasOnGround = true;
    private int buildCooldown = 0;

    public EscapeBoatAndBuildUpGoal(CustomBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.level = mob.level();
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // 1. 被船困住
        if (mob.isPassenger() && mob.getVehicle() instanceof Boat) {
            return true;
        }
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        // 2. 与玩家有高低差（玩家高处）且距离适中，可以搭高
        if (target.getY() > mob.getY() + 2 && mob.distanceToSqr(target) < 16.0 * 16.0) {
            return true;
        }
        // 3. 正在空中下落（fallDistance > 1.5）且目标在近处，准备重锤攻击
        if (mob.fallDistance > 1.5f && !mob.onGround() && target.distanceToSqr(mob) < 4.0) {
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        wasOnGround = mob.onGround();
        boatAttackTimer = 0;
    }

    @Override
    public void tick() {
        // === 船脱困逻辑 ===
        if (mob.isPassenger() && mob.getVehicle() instanceof Boat boat) {
            // 脱离船
            mob.stopRiding();
            // 攻击船破坏它
            if (boatAttackTimer <= 0) {
                mob.swing(InteractionHand.MAIN_HAND);
                boat.hurt(mob.damageSources().mobAttack(mob), 5.0F);
                boatAttackTimer = 10; // 每秒攻击一次
            } else {
                boatAttackTimer--;
            }
            return;
        }

        LivingEntity target = mob.getTarget();
        if (target == null) return;

        // === 搭高逻辑 ===
        if (target.getY() > mob.getY() + 2 && buildCooldown <= 0) {
            // 朝向目标
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            // 准备方块
            mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
            // 跳跃并在最高点放置方块
            if (mob.onGround()) {
                mob.jumpFromGround();
            }
            // 放置方块：在脚下放置
            BlockPos placePos = mob.blockPosition().below();
            if (level.isEmptyBlock(placePos.above()) && mob.getDeltaMovement().y > 0) {
                level.setBlock(placePos.above(), Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.playSound(null, placePos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                mob.swing(InteractionHand.MAIN_HAND);
                buildCooldown = 20; // 1秒冷却
                mob.restoreMainHandItem();
            }
        }
        if (buildCooldown > 0) buildCooldown--;

        // === 重锤下落攻击 ===
        if (!mob.onGround() && mob.fallDistance > 1.5f) {
            // 切换重锤
            mob.switchMainHandItem(new ItemStack(Items.MACE));
            // 检测落地瞬间（从空中到地面的帧）
            if (!wasOnGround && mob.onGround() && target.distanceToSqr(mob) < 4.0) {
                // 落地攻击
                mob.swing(InteractionHand.MAIN_HAND);
                // 计算重锤伤害：基础 + 下落加成 (1.21 原版公式)
                float baseDamage = (float) mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
                float fallBonus = Math.min(mob.fallDistance, 20) * 1.5F;
                float totalDamage = baseDamage + fallBonus + 3.0F;
                // 造成伤害并产生重击效果
                target.hurt(mob.damageSources().mobAttack(mob), totalDamage);
                level.playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
                // 重置 fallDistance 以避免二次伤害
                mob.fallDistance = 0;
                mob.restoreMainHandItem();
            }
        }
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        boatAttackTimer = 0;
        buildCooldown = 0;
    }
}
