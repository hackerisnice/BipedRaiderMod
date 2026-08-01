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

public class HostileCobwebTrapGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private LivingEntity target;
    private int cooldown = 0;

    public HostileCobwebTrapGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        // 这里不占用 Flag.MOVE，允许它一边放蜘蛛网一边继续追杀你
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        // 当玩家进入 5 格 (5*5=25) 的近战危险范围内时触发
        if (mob.distanceToSqr(target) > 25.0) return false;
        
        // 必须确保玩家踩在地上，且脚下的方块可以被替换（比如空气、草丛）
        if (!target.onGround()) return false;
        BlockPos targetPos = target.blockPosition();
        return level.getBlockState(targetPos).canBeReplaced();
    }

    @Override
    public void start() {
        // 切出蜘蛛网
        mob.switchMainHandItem(new ItemStack(Items.COBWEB));
        mob.swing(InteractionHand.MAIN_HAND);
        
        // 瞬间在玩家脚底放置蜘蛛网
        BlockPos targetPos = target.blockPosition();
        level.setBlock(targetPos, Blocks.COBWEB.defaultBlockState(), 3);
        
        // 播放放置音效
        level.playSound(null, targetPos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.HOSTILE, 1.0F, 1.0F);

        // 切回原来的武器（钻石剑）
        mob.restoreMainHandItem();
        
        // 设定冷却时间为 10 秒 (200 ticks)，防止无限放网卡死游戏
        cooldown = 200; 
    }
}
