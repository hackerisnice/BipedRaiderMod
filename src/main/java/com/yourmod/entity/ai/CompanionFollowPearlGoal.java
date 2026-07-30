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
        // 主人不存在、冷却未好、或者自己正处于战斗中时，不传送
        if (owner == null || cooldown > 0 || mob.getTarget() != null) {
            if (cooldown > 0) cooldown--;
            return false;
        }
        
        // 距离主人超过 15 格 (15 * 15 = 225)，且双方在同一个维度，开始丢珍珠追赶
        return mob.distanceToSqr(owner) > 225.0 && mob.level().dimension() == owner.level().dimension();
    }

    @Override
    public void start() {
        LivingEntity owner = mob.getOwner();
        if (owner == null) return;

        mob.switchMainHandItem(new ItemStack(Items.ENDER_PEARL));
        mob.swing(InteractionHand.MAIN_HAND);

        // 计算瞄准主人的高抛物线
        Vec3 ownerCenter = owner.position();
        Vec3 throwVec = ownerCenter.subtract(mob.position()).normalize();
        throwVec = throwVec.scale(1.5).add(0, 0.4, 0); // 增加向上分量，丢得更远

        ThrownEnderpearl pearl = new ThrownEnderpearl(mob.level(), mob);
        pearl.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.1, mob.getEyePosition().z);
        pearl.shoot(throwVec.x, throwVec.y, throwVec.z, 1.5F, 0.0F);
        
        mob.level().addFreshEntity(pearl);
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 1.0F, 1.0F);

        mob.restoreMainHandItem();
        cooldown = 100; // 5秒冷却，防止一直乱丢
    }
}
