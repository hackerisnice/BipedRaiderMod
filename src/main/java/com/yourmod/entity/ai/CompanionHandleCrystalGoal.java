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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

public class CompanionHandleCrystalGoal extends Goal {

    private final FriendlyBipedEntity mob;
    private EndCrystal targetCrystal = null;
    private int attackCooldown = 0;
    
    // 状态机：0=激光射击，1=瞬移，2=拆铁栅栏，3=砍爆水晶
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
            // 扫描判断是否被铁栅栏包围
            isCaged = checkCaged(targetCrystal);
            return true;
        }
        return false;
    }

    // 扫描周围 5x5x5 是否有铁栅栏
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
        phase = isCaged ? 1 : 0; // 有笼子直接切瞬移，没笼子切射击
    }

    @Override
    public void tick() {
        if (targetCrystal == null || !targetCrystal.isAlive()) return;

        mob.getLookControl().setLookAt(targetCrystal, 30.0F, 30.0F);
        mob.getNavigation().stop();

        // 状态 0：无重力激光射击
        if (phase == 0) { 
            if (attackCooldown <= 0) {
                mob.switchMainHandItem(new ItemStack(Items.BOW));
                mob.swing(InteractionHand.MAIN_HAND);
                Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), mob.getMainHandItem());
                
                Vec3 aim = targetCrystal.getBoundingBox().getCenter().subtract(mob.getEyePosition()).normalize();
                arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
                
                // ★ 极其硬核的改动：射速拉满，且取消重力，指哪打哪的激光弹道！
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
        // 状态 1：瞬移到铁栅栏柱子边缘
        else if (phase == 1) { 
            mob.teleportTo(targetCrystal.getX(), targetCrystal.getY(), targetCrystal.getZ() + 1.0);
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);
            phase = 2;
            attackCooldown = 5;
        } 
        // 状态 2：瞬间清空周围所有铁栅栏
        else if (phase == 2) { 
            if (attackCooldown <= 0) {
                mob.switchMainHandItem(new ItemStack(Items.NETHERITE_PICKAXE));
                mob.swing(InteractionHand.MAIN_HAND);
                BlockPos center = targetCrystal.blockPosition();
                boolean brokeAny = false;
                
                // 瞬间暴力拆解 5x5x5 范围内的全部铁栅栏
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
                phase = 3;
                attackCooldown = 10;
            } else {
                attackCooldown--;
            }
        } 
        // 状态 3：用剑砍爆水晶
        else if (phase == 3) { 
            if (attackCooldown <= 0) {
                mob.switchMainHandItem(new ItemStack(Items.DIAMOND_SWORD));
                mob.swing(InteractionHand.MAIN_HAND);
                targetCrystal.hurt(mob.damageSources().mobAttack(mob), 10.0F);
                attackCooldown = 20;
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
        mob.restoreMainHandItem();
        targetCrystal = null;
        attackCooldown = 10;
        phase = 0;
        isCaged = false;
    }
}
