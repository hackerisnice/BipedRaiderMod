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
    private int buildCooldown = 0;

    public EscapeBoatAndBuildUpGoal(CustomBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        // 搭建时强制占用移动和跳跃控制
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        
        // 目标比自己高出 2.5 格以上，且水平距离在 8 格以内，才会触发垂直搭高
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double horizontalDistSqr = dx * dx + dz * dz;
        
        return target.getY() - mob.getY() > 2.5 && horizontalDistSqr < 64.0;
    }

    @Override
    public void start() {
        buildCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        // 抬头看目标
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.getNavigation().stop(); // 停止乱跑

        if (buildCooldown > 0) buildCooldown--;

        // 1. 如果在地上，起跳！
        if (mob.onGround()) {
            mob.getJumpControl().jump();
            buildCooldown = 4; // 设定一个极短的等待刻，确保实体已经腾空
        } 
        // 2. 如果在空中（Y轴速度向上），且延迟完毕，立刻放方块！
        else if (buildCooldown == 0 && mob.getDeltaMovement().y > 0.05) {
            
            // 精准获取刚刚离开的脚下方块坐标
            BlockPos posBelow = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
            
            if (mob.level().getBlockState(posBelow).canBeReplaced()) {
                mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
                mob.swing(InteractionHand.MAIN_HAND);
                // 瞬间放圆石
                mob.level().setBlock(posBelow, Blocks.COBBLESTONE.defaultBlockState(), 3);
                mob.restoreMainHandItem();
                
                // 强制重置一个垂直小跳，防止卡入方块缝隙
                mob.setDeltaMovement(mob.getDeltaMovement().x, 0.42, mob.getDeltaMovement().z);
                buildCooldown = 15; // 限制搭高速度，太快容易把顶踩空
            }
        }
    }
}
