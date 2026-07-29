package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.component.ChargedProjectiles;

import java.util.EnumSet;

public class EnderPearlTeleportGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private int phase = 0;
    private int shootCount = 0;
    private int tickDelay = 0;

    public EnderPearlTeleportGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (mob.distanceToSqr(target) <= 16.0 * 16.0) return false;
        if (!mob.getSensing().hasLineOfSight(target)) return false;
        return true;
    }

    @Override
    public void start() {
        phase = 0;
        shootCount = 0;
        tickDelay = 0;
        
        var registryAccess = this.mob.level().registryAccess();
        var enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> quickCharge = enchantmentRegistry.getHolderOrThrow(Enchantments.QUICK_CHARGE);
        Holder<Enchantment> multishot = enchantmentRegistry.getHolderOrThrow(Enchantments.MULTISHOT);
        
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        crossbow.enchant(quickCharge, 5);
        crossbow.enchant(multishot, 1);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
        mob.switchMainHandItem(crossbow);
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        
        // 锁定身体中心
        Vec3 targetCenter = target.getBoundingBox().getCenter();

        if (phase == 0) {
            if (shootCount < 3) {
                if (tickDelay <= 0) {
                    ItemStack crossbow = mob.getMainHandItem();
                    if (crossbow.is(Items.CROSSBOW)) {
                        mob.swing(InteractionHand.MAIN_HAND);
                        
                        Vec3 shootVec = targetCenter.subtract(mob.getX(), mob.getEyeY(), mob.getZ()).normalize();
                        
                        Arrow arrow = new Arrow(level, mob, new ItemStack(Items.ARROW), crossbow);
                        arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
                        arrow.shoot(shootVec.x, shootVec.y, shootVec.z, 3.0F, 0.0F); // 0.0F 精准无偏
                        arrow.setBaseDamage(5.0);
                        level.addFreshEntity(arrow);
                        
                        level.playSound(null, mob.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
                        shootCount++;
                        tickDelay = 5;
                    }
                } else {
                    tickDelay--;
                }
            } else {
                phase = 1;
                tickDelay = 10;
                mob.switchMainHandItem(new ItemStack(Items.ENDER_PEARL));
            }
        } else if (phase == 1) {
            if (tickDelay > 0) {
                tickDelay--;
                return;
            }
            mob.swing(InteractionHand.MAIN_HAND);
            ThrownEnderpearl pearl = new ThrownEnderpearl(level, mob);
            
            Vec3 pearlThrowVec = targetCenter.subtract(mob.getX(), mob.getEyeY(), mob.getZ()).normalize();
            pearlThrowVec = pearlThrowVec.scale(1.5).add(0, 0.2, 0); // 抛物线修正
            
            pearl.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.1, mob.getEyePosition().z);
            pearl.shoot(pearlThrowVec.x, pearlThrowVec.y, pearlThrowVec.z, 1.5F, 0.0F);
            level.addFreshEntity(pearl);
            level.playSound(null, mob.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
            
            mob.restoreMainHandItem();
            phase = 2;
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (phase == 2) return false;
        return mob.distanceToSqr(target) > 16.0 * 16.0;
    }

    @Override
    public void stop() {
        phase = 0;
        shootCount = 0;
        tickDelay = 0;
        mob.restoreMainHandItem();
    }
}
