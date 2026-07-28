package com.yourmod.entity.ai;

import com.yourmod.entity.CustomBipedEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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

/**
 * 远距离末影珍珠追击：距离 > 16 格时用附魔弩射击，然后投掷末影珍珠传送接近。
 */
public class EnderPearlTeleportGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private int phase = 0;          // 0:弩射击, 1:珍珠投掷
    private int shootCount = 0;     // 多重射击轮次
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
        // 仅当视线无遮挡且地形开阔时激活（简化：有视线即可）
        if (!mob.getSensing().hasLineOfSight(target)) return false;
        return true;
    }

    @Override
    public void start() {
        phase = 0;
        shootCount = 0;
        tickDelay = 0;
        // 准备弩
        var registryAccess = this.mob.level().registryAccess(); // 如果在此Goal中能拿到mob/实体
        var enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
    
        // 获取对应的 Holder<Enchantment>
        Holder<Enchantment> quickCharge = enchantmentRegistry.getHolderOrThrow(Enchantments.QUICK_CHARGE);
        Holder<Enchantment> power = enchantmentRegistry.getHolderOrThrow(Enchantments.POWER);
        Holder<Enchantment> multishot = enchantmentRegistry.getHolderOrThrow(Enchantments.MULTISHOT);
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        // 附魔：快速装填 V, 力量 V (虽对弩无效但模拟)，多重射击
        crossbow.enchant(quickCharge, 5);
        crossbow.enchant(power, 5);     // 仅用于表示
        crossbow.enchant(multishot, 1);
        // 装填弩（用箭）
        ItemStack arrowStack = new ItemStack(Items.ARROW);
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(arrowStack));
        mob.switchMainHandItem(crossbow);
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (phase == 0) {
            // 弩射击阶段
            if (shootCount < 3) { // 多重射击实际发射3波（模拟）
                if (tickDelay <= 0) {
                    // 发射箭矢
                    ItemStack crossbow = mob.getMainHandItem();
                    if (crossbow.is(Items.CROSSBOW)) {
                        // 播放挥动手臂动画
                        mob.swing(InteractionHand.MAIN_HAND);
                        // 模拟弩射击：直接生成箭矢
                        Arrow arrow = new Arrow(level, mob, new ItemStack(Items.ARROW), crossbow);
                        Vec3 lookVec = mob.getLookAngle();
                        arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
                        arrow.shoot(lookVec.x, lookVec.y, lookVec.z, 3.0F, 1.0F); // 高速
                        arrow.setBaseDamage(5.0); // 模拟力量附魔伤害
                        arrow.setCritArrow(true);
                        level.addFreshEntity(arrow);
                        // 多重射击额外两支
                        for (int i = -1; i <= 1; i += 2) {
                            Arrow extra = new Arrow(level, mob, new ItemStack(Items.ARROW), crossbow);
                            Vec3 spreadVec = lookVec.yRot((float) Math.toRadians(10 * i));
                            extra.setPos(arrow.position());
                            extra.shoot(spreadVec.x, spreadVec.y, spreadVec.z, 3.0F, 1.0F);
                            extra.setBaseDamage(5.0);
                            level.addFreshEntity(extra);
                        }
                        level.playSound(null, mob.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
                        shootCount++;
                        tickDelay = 5; // 快速连射间隔
                    }
                } else {
                    tickDelay--;
                }
            } else {
                // 弩射击完毕，进入珍珠投掷阶段
                phase = 1;
                tickDelay = 10; // 切换武器延迟
                // 切换末影珍珠
                mob.switchMainHandItem(new ItemStack(Items.ENDER_PEARL));
            }
        } else if (phase == 1) {
            // 投掷末影珍珠
            if (tickDelay > 0) {
                tickDelay--;
                return;
            }
            mob.swing(InteractionHand.MAIN_HAND);
            ThrownEnderpearl pearl = new ThrownEnderpearl(level, mob);
            Vec3 targetPos = target.position().add(0, 1, 0); // 稍微抬高
            Vec3 throwVec = targetPos.subtract(mob.getEyePosition());
            double horizontalDist = throwVec.horizontalDistance();
            // 高抛弧线：增加向上的分量
            throwVec = throwVec.normalize().scale(1.5).add(0, horizontalDist * 0.15, 0);
            pearl.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.1, mob.getEyePosition().z);
            pearl.shoot(throwVec.x, throwVec.y, throwVec.z, 1.5F, 0.2F);
            level.addFreshEntity(pearl);
            level.playSound(null, mob.blockPosition(), SoundEvents.ENDER_PEARL_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);
            // 珍珠投掷完成，还原武器，Goal 结束
            mob.restoreMainHandItem();
            phase = 2; // 标记完成
        }
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (phase == 2) return false; // 已完成
        return mob.distanceToSqr(target) > 16.0 * 16.0;
    }

    @Override
    public void stop() {
        phase = 0;
        shootCount = 0;
        tickDelay = 0;
        mob.restoreMainHandItem(); // 无论如何退出都还原
    }
}
