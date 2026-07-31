package com.yourmod.entity.ai;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CompanionFollowPearlGoal extends Goal {

    private final FriendlyBipedEntity mob;
    private int cooldown = 0;

    public CompanionFollowPearlGoal(FriendlyBipedEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = mob.getOwner();
        if (owner == null || cooldown > 0 || mob.getTarget() != null) {
            if (cooldown > 0) cooldown--;
            return false;
        }
        
        double dist = mob.distanceToSqr(owner);
        // ★ 核心改动：仅在 15~32 格之间丢珍珠 (225 ~ 1024)。超出 1024 会被底层的瞬移接管。
        return dist > 225.0 && dist <= 1024.0 && mob.level().dimension() == owner.level().dimension();
    }

    @Override
    public void start() {
        LivingEntity owner = mob.getOwner();
        if (owner == null) return;

        mob.switchMainHandItem(new ItemStack(Items.ENDER_PEARL));
        mob.swing(InteractionHand.MAIN_HAND);

        Vec3 ownerCenter = owner.position();
        Vec3 throwVec = ownerCenter.subtract(mob.position()).normalize();
        throwVec = throwVec.scale(1.5).add(0, 0.4, 0); 

        ThrownEnderpearl pearl = new ThrownEnderpearl(mob.level(), mob);
        pearl.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.1, mob.getEyePosition().z);
        pearl.shoot(throwVec.x, throwVec.y, throwVec.z, 1.5F, 0.0F);
        
        mob.level().addFreshEntity(pearl);
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 1.0F, 1.0F);

        mob.restoreMainHandItem();
        cooldown = 100; 
    }
}
