package com.yourmod.entity.ai;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class CompanionHandleCrystalGoal extends Goal {

    private final FriendlyBipedEntity mob;
    private EndCrystal targetCrystal = null;
    private int attackCooldown = 0;
    
    // 状态机：0=准备射击, 1=瞬移上柱子, 2=拆铁栅栏
    private int phase = 0; 
    private boolean isCaged = false;

    public CompanionHandleCrystalGoal(FriendlyBipedEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (attackCooldown > 0) {
            attackCooldown--;
            return false;
        }

        List<EndCrystal> crystals = mob.level().getEntitiesOfClass(EndCrystal.class, mob.getBoundingBox().inflate(96.0D));
        if (crystals.isEmpty()) return false;

        double closestDist = Double.MAX_VALUE;
        EndCrystal closest = null;
        for (EndCrystal crystal : crystals) {
            double dist = mob.distanceToSqr(crystal);
            if (dist < closestDist) {
                closestDist = dist;
                closest = crystal;
            }
        }

        if (closest != null) {
            targetCrystal = closest;
            isCaged = checkCaged(targetCrystal);
            return true;
        }
        return false;
    }

    private boolean checkCaged(EndCrystal crystal) {
        BlockPos pos = crystal.blockPosition();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (mob.level().getBlockState(pos.offset(x, y, z)).is(Blocks.IRON_BARS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void start() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        phase = 0; // 永远先尝试射击
    }

    @Override
    public void tick() {
        if (targetCrystal == null || !targetCrystal.isAlive()) return;

        mob.getLookControl().setLookAt(targetCrystal, 30.0F, 30.0F);
        mob.getNavigation().stop();

        if (phase == 0) { 
            Vec3 eyePos = mob.getEyePosition();
            // 瞄准水晶靠上的位置
            Vec3 crystalTarget = new Vec3(targetCrystal.getX(), targetCrystal.getY() + 1.0, targetCrystal.getZ());
            
            // ★ 核心修复：发射物理射线预判，检查直达水晶的路线中间有没有黑曜石挡路
            BlockHitResult hitResult = mob.level().clip(new ClipContext(
                    eyePos, 
                    crystalTarget, 
                    ClipContext.Block.COLLIDER, 
                    ClipContext.Fluid.NONE, 
                    mob
            ));

            // 如果打到了方块（视线被黑曜石边缘遮挡），直接放弃射击，转入瞬移模式！
            if (hitResult.getType() == BlockHitResult.Type.BLOCK) {
                phase = 1;
                return;
            }

            // 视线清晰，开火！
            if (attackCooldown <= 0) {
                mob.switchMainHandItem(new ItemStack(Items.BOW));
                mob.swing(InteractionHand.MAIN_HAND);
                Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), mob.getMainHandItem());
                
                Vec3 aim = crystalTarget.subtract(eyePos).normalize();
                arrow.setPos(eyePos.x, eyePos.y, eyePos.z);
                
                arrow.shoot(aim.x, aim.y, aim.z, 5.0F, 0.0F); 
                arrow.setNoGravity(true); 
                arrow.setBaseDamage(10.0);
                
                mob.level().addFreshEntity(arrow);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                
                attackCooldown = 30; 
            } else {
                attackCooldown--;
            }
        } 
        else if (phase == 1) { 
            // 瞬移到柱子顶端，距离水晶 2 格的位置
            mob.teleportTo(targetCrystal.getX() + 2.0, targetCrystal.getY(), targetCrystal.getZ());
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
            
            // 如果有笼子就去拆，没笼子就回到 phase 0 (由于已经在柱子上了，下一次检测必定无遮挡，直接贴脸射爆)
            phase = isCaged ? 2 : 0;
            attackCooldown = 10;
        } 
        else if (phase == 2) { 
            if (attackCooldown <= 0) {
                mob.switchMainHandItem(new ItemStack(Items.NETHERITE_PICKAXE));
                mob.swing(InteractionHand.MAIN_HAND);
                BlockPos center = targetCrystal.blockPosition();
                boolean brokeAny = false;
                
                // 瞬间拆掉所有铁栅栏
                for (int x = -2; x <= 2; x++) {
                    for (int y = -1; y <= 3; y++) {
                        for (int z = -2; z <= 2; z++) {
                            BlockPos p = center.offset(x, y, z);
                            if (mob.level().getBlockState(p).is(Blocks.IRON_BARS)) {
                                mob.level().destroyBlock(p, true, mob);
                                brokeAny = true;
                            }
                        }
                    }
                }
                if (brokeAny) {
                    mob.level().playSound(null, mob.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.NEUTRAL, 1.0F, 1.0F);
                }
                
                // 拆完笼子，重置回 phase 0 贴脸射爆
                phase = 0;
                attackCooldown = 10;
            } else {
                attackCooldown--;
            }
        } 
    }

    @Override
    public boolean canContinueToUse() {
        return targetCrystal != null && targetCrystal.isAlive() && mob.distanceToSqr(targetCrystal) <= 9216.0D;
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        mob.restoreMainHandItem();
        targetCrystal = null;
        attackCooldown = 10;
        phase = 0;
        isCaged = false;
    }
}
