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
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;

        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // ★ 只要距离大于 5 格，直接开启“平地走搭”逼近！
        // 交给战斗 AI 来处理 5 格以内的近战和风弹起飞
        return horizontalDist > 5.0;
    }

    @Override
    public void start() {
        mob.getNavigation().stop(); 
        buildCooldown = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // 强行物理推进，无视地形直线冲刺
        Vec3 dir = target.position().subtract(mob.position()).normalize();
        mob.setDeltaMovement(dir.x * speed, mob.getDeltaMovement().y, dir.z * speed);

        // ★ 平地搭高逻辑：如果自己的高度还没有超过目标 3 格，就连续起跳垫脚
        if (mob.getY() < target.getY() + 3.0) {
            if (mob.onGround()) {
                mob.getJumpControl().jump(); // 起跳
                buildCooldown = 1;
            } else if (buildCooldown > 0) {
                buildCooldown++;
                
                // 跳跃到半空时（第4刻），在脚底放方块
                if (buildCooldown >= 4) {
                    BlockPos placePos = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
                    
                    if (mob.level().getBlockState(placePos).canBeReplaced()) {
                        if (mob instanceof CustomBipedEntity custom) {
                            custom.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
                        } else if (mob instanceof FriendlyBipedEntity friendly) {
                            friendly.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
                        }

                        mob.level().setBlock(placePos, Blocks.COBBLESTONE.defaultBlockState(), 3);
                        mob.swing(InteractionHand.MAIN_HAND);
                        mob.level().playSound(null, placePos, Blocks.COBBLESTONE.getSoundType(Blocks.COBBLESTONE.defaultBlockState()).getPlaceSound(), SoundSource.HOSTILE, 1.0F, 0.8F);

                        if (mob instanceof CustomBipedEntity custom) {
                            custom.restoreMainHandItem();
                        } else if (mob instanceof FriendlyBipedEntity friendly) {
                            friendly.restoreMainHandItem();
                        }
                    }
                    buildCooldown = 0;
                }
            }
        }
    }
}
