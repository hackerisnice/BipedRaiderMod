package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class BreakBlockToReachTargetGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private BlockPos targetBlock = null;
    private int breakProgress = 0;
    private int tickCounter = 0;
    
    // ★ 新增：动态计算所需的总挖掘刻度
    private int maxBreakTicks = 60; 

    public BreakBlockToReachTargetGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (this.mob.getSensing().hasLineOfSight(target)) return false;

        Vec3 start = this.mob.getEyePosition();
        Vec3 end = target.getBoundingBox().getCenter();
        
        BlockHitResult hitResult = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
        if (hitResult.getType() == BlockHitResult.Type.MISS) return false;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || !state.blocksMotion()) return false;

        this.targetBlock = pos.immutable();
        return true;
    }

    @Override
    public void start() {
        breakProgress = 0;
        tickCounter = 0;
        mob.switchMainHandItem(new ItemStack(Items.NETHERITE_PICKAXE));
        mob.getNavigation().moveTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 1.0);
        
        // ★ 核心改动：获取方块硬度，赋予下界合金镐的神速
        BlockState state = level.getBlockState(targetBlock);
        float hardness = state.getDestroySpeed(level, targetBlock);
        // 石头硬度 1.5 -> 7 刻(0.35秒)挖开；泥土 0.5 -> 4 刻(0.2秒)挖开。保底 4 刻。
        maxBreakTicks = Math.max(4, (int) (hardness * 5)); 
    }

    @Override
    public void tick() {
        if (targetBlock == null) return;
        BlockState state = level.getBlockState(targetBlock);
        if (state.isAir()) {
            clearCracks();
            return;
        }

        mob.getLookControl().setLookAt(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);

        double dist = mob.position().distanceToSqr(targetBlock.getCenter());
        if (dist > 4.0) {
            mob.getNavigation().moveTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 1.0);
            return;
        }

        breakProgress++;
        tickCounter++;

        if (tickCounter % 3 == 0) { // 提高挥舞手臂频率
            mob.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, targetBlock, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        // 动态计算裂纹
        int crackStage = (int) ((breakProgress / (float) maxBreakTicks) * 10);
        level.destroyBlockProgress(mob.getId(), targetBlock, Math.min(crackStage, 10));

        // 极速挖开
        if (breakProgress >= maxBreakTicks) {
            level.destroyBlock(targetBlock, true, mob);
            clearCracks();
            targetBlock = null;
            breakProgress = 0;
            mob.restoreMainHandItem();
        }
    }

    @Override
    public void stop() {
        clearCracks();
        targetBlock = null;
        breakProgress = 0;
        mob.restoreMainHandItem();
    }

    private void clearCracks() {
        if (targetBlock != null) {
            level.destroyBlockProgress(mob.getId(), targetBlock, -1);
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (mob.getSensing().hasLineOfSight(target)) return false;
        if (targetBlock == null || level.getBlockState(targetBlock).isAir()) return false;
        return true;
    }
}
