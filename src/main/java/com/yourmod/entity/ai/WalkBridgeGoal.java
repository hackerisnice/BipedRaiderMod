package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class WalkBridgeGoal extends Goal {

    private final Mob mob;
    private final double speed;
    private int buildCooldown = 0;

    public WalkBridgeGoal(Mob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        // ★ 接管移动和视角，禁止原版寻路干扰
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        // 只有当目标距离较远，且水平距离大于垂直距离时，才判定需要“搭路”
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double dy = Math.abs(target.getY() - mob.getY());
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // 如果前方 1-2 格是空气（悬崖），且目标在对面，触发走搭！
        if (horizontalDist > 3.0 && dy < horizontalDist) {
            Vec3 lookVec = target.position().subtract(mob.position()).normalize();
            BlockPos checkPos = BlockPos.containing(mob.getX() + lookVec.x * 1.5, mob.getY() - 1.0, mob.getZ() + lookVec.z * 1.5);
            return mob.level().getBlockState(checkPos).canBeReplaced();
        }
        return false;
    }

    @Override
    public void start() {
        mob.getNavigation().stop(); // 彻底关闭原版寻路，原版寻路不敢跳崖
        buildCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        // 1. 锁死视角：冷酷地盯着目标
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // 2. 强行物理推进（Override Physics）：无视地形，直线冲刺
        Vec3 dir = target.position().subtract(mob.position()).normalize();
        
        // ★ 核心：给予一个恒定的水平速度，模拟“按住 W 键”
        mob.setDeltaMovement(dir.x * speed, mob.getDeltaMovement().y, dir.z * speed);

        // 3. 神仙走搭逻辑 (Predictive God-Bridging)
        if (buildCooldown > 0) buildCooldown--;

        // 预测怪物未来 1.5 刻的位置（赶在它掉下去之前垫方块）
        double predictX = mob.getX() + dir.x * 1.2;
        double predictZ = mob.getZ() + dir.z * 1.2;
        BlockPos placePos = BlockPos.containing(predictX, mob.getY() - 0.2, predictZ);

        // 如果预测的脚下是空的，瞬间塞方块！
        if (mob.level().getBlockState(placePos).canBeReplaced() && buildCooldown <= 0) {
            
            // 切换手中的物品（为了视觉效果）
            if (mob instanceof CustomBipedEntity) {
                ((CustomBipedEntity) mob).switchMainHandItem(new ItemStack(Items.COBBLESTONE));
            } else if (mob instanceof FriendlyBipedEntity) {
                ((FriendlyBipedEntity) mob).switchMainHandItem(new ItemStack(Items.COBBLESTONE));
            }

            // 放置方块并挥手
            mob.level().setBlock(placePos, Blocks.COBBLESTONE.defaultBlockState(), 3);
            mob.swing(InteractionHand.MAIN_HAND);
            
            // 播放放置音效 (这里使用方块自带的放置音效组)
            mob.level().playSound(null, placePos, Blocks.COBBLESTONE.getSoundType(Blocks.COBBLESTONE.defaultBlockState()).getPlaceSound(), SoundSource.HOSTILE, 1.0F, 0.8F);

            // 恢复武器
            if (mob instanceof CustomBipedEntity) {
                ((CustomBipedEntity) mob).restoreMainHandItem();
            } else if (mob instanceof FriendlyBipedEntity) {
                ((FriendlyBipedEntity) mob).restoreMainHandItem();
            }

            buildCooldown = 2; // 控制手速，防止一瞬间放太多方块卡住自己
        }
    }
}
