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
import net.minecraft.world.level.block.SoundType;
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

        Vec3 dir = target.position().subtract(mob.position()).normalize();
        mob.setDeltaMovement(dir.x * speed, mob.getDeltaMovement().y, dir.z * speed);

        if (mob.getY() < target.getY() + 3.0) {
            if (mob.onGround()) {
                mob.getJumpControl().jump();
                buildCooldown = 1;
            } else if (buildCooldown > 0) {
                buildCooldown++;
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
                        
                        // ★ 核心修复：直接调用公共枚举 SoundType.STONE，绕过 protected 限制
                        mob.level().playSound(null, placePos, SoundType.STONE.getPlaceSound(), SoundSource.HOSTILE, 1.0F, 0.8F);

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
