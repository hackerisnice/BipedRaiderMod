package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;

public class EscapeBoatAndBuildUpGoal extends Goal {

    private final CustomBipedEntity mob;
    private final double speedModifier;
    
    // ★ 核心修复：跳跃刻数计时器
    private int jumpTick = 0; 

    public EscapeBoatAndBuildUpGoal(CustomBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double horizontalDistSqr = dx * dx + dz * dz;
        
        return target.getY() - mob.getY() > 2.0 && horizontalDistSqr < 25.0;
    }

    @Override
    public void start() {
        jumpTick = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.getNavigation().stop();
        
        // ★ 核心修复：强制清除水平速度，保证垂直搭高时不乱晃掉下柱子
        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);

        // 如果在地上，且计时器为0，立刻起跳
        if (mob.onGround() && jumpTick == 0) {
            mob.getJumpControl().jump();
            jumpTick = 1;
        } 
        // 腾空状态下，每刻递增
        else if (jumpTick > 0) {
            jumpTick++;
            
            // ★ 完美时机：起跳后的第 6 刻 (约 0.3 秒)，正是处于跳跃弧线的最高点
            if (jumpTick >= 6) {
                BlockPos posBelow = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
                
                if (mob.level().getBlockState(posBelow).canBeReplaced()) {
                    mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
                    mob.swing(InteractionHand.MAIN_HAND);
                    mob.level().setBlock(posBelow, Blocks.COBBLESTONE.defaultBlockState(), 3);
                    mob.restoreMainHandItem();
                }
                
                // 垫完方块，重置循环准备下一次起跳
                jumpTick = 0;
            }
        }
    }
    
    @Override
    public void stop() {
        jumpTick = 0;
    }
}
