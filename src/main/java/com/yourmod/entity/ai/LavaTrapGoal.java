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

import java.util.EnumSet;

public class LavaTrapGoal extends Goal {
    private final CustomBipedEntity mob;
    private final Level level;
    private LivingEntity target;
    private BlockPos placedLavaPos = null;
    private int cooldown = 0;
    private int scoopTimer = 0;

    public LavaTrapGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        // 放岩浆时允许移动，但会接管视线
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        
        // 必须逼近到 4 格以内 (2 * 2 = 4)
        if (mob.distanceToSqr(target) > 4.0) return false;
        
        // 确保目标脚下是可以被替换的方块（如空气、草丛），而不是实心方块
        BlockPos targetPos = target.blockPosition();
        return level.getBlockState(targetPos).canBeReplaced();
    }

    @Override
    public void start() {
        // 切出岩浆桶
        mob.switchMainHandItem(new ItemStack(Items.LAVA_BUCKET));
        mob.swing(InteractionHand.MAIN_HAND);
        
        BlockPos targetPos = target.blockPosition();
        level.setBlock(targetPos, Blocks.LAVA.defaultBlockState(), 3);
        placedLavaPos = targetPos;
        
        level.playSound(null, targetPos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.HOSTILE, 1.0F, 1.0F);
        
        scoopTimer = 20; // 停留 1 秒（20 tick），足够让玩家点燃
        cooldown = 160;  // 8 秒放一次岩浆
    }

    @Override
    public void tick() {
        if (target != null) {
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        
        if (placedLavaPos != null) {
            scoopTimer--;
            if (scoopTimer <= 0) {
                // 收回岩浆
                if (level.getBlockState(placedLavaPos).is(Blocks.LAVA)) {
                    level.setBlock(placedLavaPos, Blocks.AIR.defaultBlockState(), 3);
                    // 模拟收回动作，播放音效
                    level.playSound(null, placedLavaPos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                mob.restoreMainHandItem();
                placedLavaPos = null;
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return placedLavaPos != null; 
    }

    @Override
    public void stop() {
        // 异常中断时保底清除岩浆
        if (placedLavaPos != null && level.getBlockState(placedLavaPos).is(Blocks.LAVA)) {
            level.setBlock(placedLavaPos, Blocks.AIR.defaultBlockState(), 3);
        }
        mob.restoreMainHandItem();
        placedLavaPos = null;
    }
}
