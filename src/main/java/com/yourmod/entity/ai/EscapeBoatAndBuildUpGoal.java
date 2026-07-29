package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.EnumSet;

public class EscapeBoatAndBuildUpGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    
    private int comboHits = 0;
    private int lastHurtTime = 0;

    private int phase = 0;
    private int scaffoldTicks = 0;
    
    private float maxFallDistance = 0f;
    private boolean wasOnGround = true;

    public EscapeBoatAndBuildUpGoal(CustomBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        int currentHurt = mob.getLastHurtByMobTimestamp();
        if (currentHurt != lastHurtTime) {
            lastHurtTime = currentHurt;
            comboHits++;
        }

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        if (comboHits >= 3) {
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        return phase > 0 && target != null && target.isAlive();
    }

    @Override
    public void start() {
        comboHits = 0;
        phase = 1;
        scaffoldTicks = 0;
        maxFallDistance = 0f;
        wasOnGround = mob.onGround();
        mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        // === 阶段 1：直线跑酷走搭 ===
        if (phase == 1) {
            scaffoldTicks++;

            // 1. 直线向玩家冲锋 (1.5倍疾跑速度)
            mob.getNavigation().moveTo(target, 1.5D);
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // 2. 跑酷起跳：目标较高，或者面前一格是悬崖坑洞
            if (mob.onGround()) {
                BlockPos blockAhead = mob.blockPosition().relative(mob.getDirection()).below();
                boolean isGapAhead = !level.getBlockState(blockAhead).blocksMotion();
                
                if (target.getY() > mob.getY() || isGapAhead) {
                    mob.jumpFromGround();
                }
            }

            // 3. 空中垫方块：只要处于空中，脚底是空气就塞入圆石
            if (!mob.onGround()) {
                BlockPos posUnderFeet = BlockPos.containing(mob.getX(), mob.getY() - 0.1, mob.getZ());
                if (level.getBlockState(posUnderFeet).canBeReplaced()) {
                    level.setBlock(posUnderFeet, Blocks.COBBLESTONE.defaultBlockState(), 3);
                    level.playSound(null, posUnderFeet, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    mob.swing(InteractionHand.MAIN_HAND);
                }
            }

            // 4. 阶段切换：搭到高于玩家 4.5 格以上
            if (mob.getY() > target.getY() + 4.5 && scaffoldTicks > 20) {
                phase = 2;
                mob.switchMainHandItem(new ItemStack(Items.MACE));
            }
        } 
        
        // === 阶段 2：重锤下落追踪 ===
        else if (phase == 2) {
            if (!mob.onGround()) {
                maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
                
                // 空中追踪微调
                Vec3 dropDir = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
                if (dropDir.lengthSqr() > 0.01) {
                    dropDir = dropDir.normalize().scale(0.12);
                    mob.setDeltaMovement(dropDir.x, mob.getDeltaMovement().y, dropDir.z);
                }
            } else {
                if (!wasOnGround) {
                    if (maxFallDistance > 1.5f && target.distanceToSqr(mob) < 16.0) {
                        mob.swing(InteractionHand.MAIN_HAND);
                        float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                        float fallBonus = Math.min(maxFallDistance, 20) * 1.5f; 
                        target.hurt(mob.damageSources().mobAttack(mob), baseDmg + fallBonus + 3.0f);
                        level.playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
                    }
                    phase = 0; 
                }
            }
        }
        
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
        phase = 0;
        comboHits = 0;
        scaffoldTicks = 0;
        maxFallDistance = 0f;
    }
}
