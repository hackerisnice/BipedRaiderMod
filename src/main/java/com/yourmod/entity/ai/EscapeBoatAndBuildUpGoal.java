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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.particles.ParticleTypes;

import java.util.EnumSet;

public class EscapeBoatAndBuildUpGoal extends Goal {

    private final CustomBipedEntity mob;
    private final double speedModifier;
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
        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);

        if (mob.onGround() && jumpTick == 0) {
            mob.setDeltaMovement(0, 1.2, 0);
            
            // ★ 修复：使用 .value() 拆包音效
            mob.level().playSound(null, mob.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
            
            if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.GUST, mob.getX(), mob.getY(), mob.getZ(), 15, 0.5, 0.2, 0.5, 0.1);
            }
            
            jumpTick = 1;
        } 
        else if (jumpTick > 0) {
            jumpTick++;
            
            if (jumpTick >= 4) {
                BlockPos posBelow = BlockPos.containing(mob.getX(), mob.getY() - 0.2, mob.getZ());
                
                if (mob.level().getBlockState(posBelow).canBeReplaced()) {
                    mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
                    mob.swing(InteractionHand.MAIN_HAND);
                    mob.level().setBlock(posBelow, Blocks.COBBLESTONE.defaultBlockState(), 3);
                    mob.restoreMainHandItem();
                }
                jumpTick = 0;
            }
        }
    }
    
    @Override
    public void stop() {
        jumpTick = 0;
    }
}
