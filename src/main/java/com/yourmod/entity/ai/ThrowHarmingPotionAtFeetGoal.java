package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class ThrowHarmingPotionAtFeetGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private int cooldown = 0;
    private static final int COOLDOWN_TICKS = 80;

    public ThrowHarmingPotionAtFeetGoal(CustomBipedEntity mob) {
        this.mob = mob;
        this.level = mob.level();
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        LivingEntity target = mob.getTarget();
        if (!(target instanceof Player) || !target.isAlive()) return false;
        
        // 只要在 8 格内就触发
        if (mob.distanceToSqr(target) > 8.0 * 8.0) return false;

        BlockPos feetPos = target.blockPosition();
        if (level.getBlockState(feetPos).blocksMotion() &&
            level.getBlockState(feetPos.above()).blocksMotion()) {
            return false; // 防止脚底是实心方块把药水卡没
        }
        return true;
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        ItemStack potionStack = new ItemStack(Items.SPLASH_POTION);
        PotionContents contents = new PotionContents(Potions.STRONG_HARMING);
        potionStack.set(DataComponents.POTION_CONTENTS, contents);

        mob.switchMainHandItem(potionStack);

        Vec3 targetPos = target.position();
        mob.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z);

        mob.swing(InteractionHand.MAIN_HAND);

        ThrownPotion potionEntity = new ThrownPotion(level, mob);
        potionEntity.setItem(potionStack);

        Vec3 throwVec = targetPos.subtract(mob.getEyePosition());
        double horizontalDist = throwVec.horizontalDistance();
        
        potionEntity.shoot(throwVec.x, throwVec.y + horizontalDist * 0.2, throwVec.z, 0.5F, 0.2F);
        potionEntity.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.1, mob.getEyePosition().z);
        level.addFreshEntity(potionEntity);

        level.playSound(null, mob.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);

        mob.restoreMainHandItem();
        cooldown = COOLDOWN_TICKS + mob.getRandom().nextInt(40);
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem();
    }
}
