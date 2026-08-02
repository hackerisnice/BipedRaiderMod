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
        
        // 目标比自己高出 2 格以上，且水平距离在 5 格以内，直接启动垂直速搭
        return target.getY() - mob.getY() > 2.0 && horizontalDistSqr < 25.0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        // 死死盯着目标
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.getNavigation().stop();

        // 只要脚踩实地，立刻起跳
        if (mob.onGround()) {
            mob.getJumpControl().jump();
        } 
        // 只要人在空中，立刻检测脚下能否塞入方块
        else {
            BlockPos posBelow = BlockPos.containing(mob.getX(), mob.getY() - 0.1, mob.getZ());
            
            if (mob.level().getBlockState(posBelow).canBeReplaced()) {
                mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
                mob.swing(InteractionHand.MAIN_HAND);
                // 瞬间放圆石
                mob.level().setBlock(posBelow, Blocks.COBBLESTONE.defaultBlockState(), 3);
                mob.restoreMainHandItem();
                
                // 赋予微小的向上推力，确保实体稳稳踩在方块上而不是卡进方块里
                mob.setDeltaMovement(mob.getDeltaMovement().x, 0.3, mob.getDeltaMovement().z);
            }
        }
    }
}
