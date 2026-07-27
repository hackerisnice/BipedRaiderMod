package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * 破墙拆迁目标：当怪物有攻击目标但无直接视线时，挖掘阻挡视线的方块。
 */
public class BreakBlockToReachTargetGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private BlockPos targetBlock = null;
    private int breakProgress = 0;
    private static final int MAX_PROGRESS = 60; // 挖掘总刻度数（3秒）
    private int tickCounter = 0;
    private int swingTimer = 0;

    public BreakBlockToReachTargetGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        // 已经有视线则无需破墙
        if (this.mob.getSensing().hasLineOfSight(target)) return false;

        // 进行射线检测寻找第一个阻挡视线的可破坏固体方块
        Vec3 start = this.mob.getEyePosition();
        Vec3 end = target.getEyePosition();
        BlockHitResult hitResult = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));
        if (hitResult.getType() == BlockHitResult.Type.MISS) return false;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);
        // 检查是否为可破坏且硬度合适的固体方块（排除基岩等）
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || !state.blocksMotion()) return false;

        this.targetBlock = pos.immutable();
        return true;
    }

    @Override
    public void start() {
        breakProgress = 0;
        tickCounter = 0;
        swingTimer = 0;
        // 切换主手为下界合金镐
        mob.switchMainHandItem(new ItemStack(Items.NETHERITE_PICKAXE));
        // 移动到方块附近并面朝方块
        mob.getNavigation().moveTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 1.0);
    }

    @Override
    public void tick() {
        if (targetBlock == null) return;
        BlockState state = level.getBlockState(targetBlock);
        if (state.isAir()) {
            // 方块已被破坏或消失，退出
            clearCracks();
            return;
        }

        // 保持面朝方块
        mob.getLookControl().setLookAt(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);

        // 与方块距离检查，太远则移动靠近
        double dist = mob.position().distanceToSqr(targetBlock.getCenter());
        if (dist > 4.0) {
            mob.getNavigation().moveTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 1.0);
            return; // 不在挖矿范围
        }

        // 每 Tick 增加进度
        breakProgress++;
        tickCounter++;

        // 每 4~5 Tick 播放挥动手臂动画，并发出方块挖掘音效
        if (tickCounter % 5 == 0) {
            mob.swing(InteractionHand.MAIN_HAND);
            // 播放挖掘音效
            level.playSound(null, targetBlock, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        // 计算客户端裂纹阶段 (0~10)
        int crackStage = (int) ((breakProgress / (float) MAX_PROGRESS) * 10);
        crackStage = Math.min(crackStage, 10);
        level.destroyBlockProgress(mob.getId(), targetBlock, crackStage);

        // 挖掘完成
        if (breakProgress >= MAX_PROGRESS) {
            level.destroyBlock(targetBlock, true, mob); // 破坏方块并掉落
            clearCracks();                              // 清除裂纹
            targetBlock = null;
            breakProgress = 0;
            mob.restoreMainHandItem();                 // 还原武器
        }
    }

    @Override
    public void stop() {
        clearCracks();
        targetBlock = null;
        breakProgress = 0;
        mob.restoreMainHandItem(); // 确保无论如何退出都还原主手
    }

    private void clearCracks() {
        if (targetBlock != null) {
            level.destroyBlockProgress(mob.getId(), targetBlock, -1); // 清除渲染裂纹
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (mob.getSensing().hasLineOfSight(target)) return false; // 视线恢复则停止
        if (targetBlock == null || level.getBlockState(targetBlock).isAir()) return false;
        return true;
    }
}
