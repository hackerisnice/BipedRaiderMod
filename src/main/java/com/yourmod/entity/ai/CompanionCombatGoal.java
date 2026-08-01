package com.yourmod.entity.ai;

import com.yourmod.entity.FriendlyBipedEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CompanionCombatGoal extends Goal {

    private final FriendlyBipedEntity mob;
    private final double speedModifier;
    
    private int attackCooldown = 0;
    private int cobwebCooldown = 0; 
    
    private float maxFallDistance = 0f;
    private boolean isMaceAttacking = false;
    private boolean waitingForCrit = false; 
    private boolean wasOnGround = true;

    public CompanionCombatGoal(FriendlyBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }
    
    @Override
    public boolean canContinueToUse() {
        if (isMaceAttacking && !wasOnGround && mob.onGround()) return true; 
        return canUse();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        cobwebCooldown = 0;
        maxFallDistance = 0f;
        isMaceAttacking = false;
        waitingForCrit = false;
        wasOnGround = mob.onGround();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        
        // ★ 核心机制：末影龙专项弱点锁定
        boolean isDragon = target instanceof EnderDragon;
        EnderDragon dragon = isDragon ? (EnderDragon) target : null;
        
        // 如果是龙，将所有视线、寻路和距离判定的焦点转移到龙的头部 (dragon.head)
        Entity aimEntity = isDragon ? dragon.head : target;
        double distSqr = mob.distanceToSqr(aimEntity);

        // 死死盯住目标（或龙头）
        mob.getLookControl().setLookAt(aimEntity, 30.0F, 30.0F);

        if (cobwebCooldown > 0) cobwebCooldown--;

        // 跳崖追击：检测目标 (或龙头) 的高度
        if (mob.onGround() && aimEntity.getY() < mob.getY() - 3.0) {
            Vec3 jumpDir = new Vec3(aimEntity.getX() - mob.getX(), 0, aimEntity.getZ() - mob.getZ());
            if (jumpDir.lengthSqr() > 0.01) {
                jumpDir = jumpDir.normalize().scale(0.35); 
                mob.setDeltaMovement(jumpDir.x, mob.getDeltaMovement().y, jumpDir.z);
            }
        }

        // ================= 1. 高空重锤 =================
        if (!mob.onGround() && !waitingForCrit) { 
            maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
            if (maxFallDistance > 1.5f && !isMaceAttacking) {
                mob.switchMainHandItem(new ItemStack(Items.MACE));
                isMaceAttacking = true;
            }
        } else if (!wasOnGround && isMaceAttacking) {
            if (maxFallDistance > 1.5f && distSqr < 25.0) { // 重锤判定范围稍微放宽
                mob.swing(InteractionHand.MAIN_HAND);
                float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                
                // 重锤落地时也针对龙头
                if (isDragon) {
                    dragon.head.hurt(mob.damageSources().mobAttack(mob), baseDmg + (Math.min(maxFallDistance, 20) * 1.5f));
                } else {
                    target.hurt(mob.damageSources().mobAttack(mob), baseDmg + (Math.min(maxFallDistance, 20) * 1.5f));
                }
                
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            mob.restoreMainHandItem();
            isMaceAttacking = false;
            maxFallDistance = 0f;
        }

        if (isMaceAttacking) {
            wasOnGround = mob.onGround();
            return; 
        }

        // ================= 2. 远距离：防空弹道预判 =================
        // 打龙时，由于龙头碰撞箱在中心，近战范围被放大到 256格 (16*16)
        double bowEngageDist = isDragon ? 256.0 : 64.0;
        
        if (distSqr > bowEngageDist) { 
            if (mob.isUsingItem()) mob.releaseUsingItem(); 
            
            // 如果是龙，向龙头寻路；否则向目标寻路
            if (isDragon) {
                mob.getNavigation().moveTo(dragon.head.getX(), dragon.head.getY(), dragon.head.getZ(), speedModifier * 0.8);
            } else {
                mob.getNavigation().moveTo(target, speedModifier * 0.8);
            }
            
            mob.switchMainHandItem(new ItemStack(Items.BOW));
            
            if (attackCooldown <= 0) {
                mob.swing(InteractionHand.MAIN_HAND);
                Arrow arrow = new Arrow(mob.level(), mob, new ItemStack(Items.ARROW), mob.getMainHandItem());
                
                // ★ 核心火控：向量提前量预判
                Vec3 targetCenter = aimEntity.getBoundingBox().getCenter();
                Vec3 targetVelocity = target.getDeltaMovement(); 
                
                // 箭速 1.6F。计算预计飞行时间 (ticks)
                double flightTime = Math.sqrt(distSqr) / 1.6;
                // 将目标的当前速度乘以飞行时间，并附加 0.9 的阻尼，防止过度预判
                Vec3 predictedPos = targetCenter.add(targetVelocity.scale(flightTime * 0.9));
                
                // 朝着预判坐标开火
                Vec3 aim = predictedPos.subtract(mob.getEyePosition()).normalize();
                
                arrow.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.2, mob.getEyePosition().z);
                arrow.shoot(aim.x, aim.y + 0.1, aim.z, 1.6F, 0.0F); 
                arrow.setBaseDamage(isDragon ? 7.0 : 4.0); // 打龙箭矢特攻
                
                mob.level().addFreshEntity(arrow);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                
                attackCooldown = 30; 
            }
        } 
        // ================= 3. 近战：锁头跳劈 =================
        else {
            if (isDragon) {
                // 龙落地时，疯狂冲向龙头
                mob.getNavigation().moveTo(dragon.head.getX(), dragon.head.getY(), dragon.head.getZ(), speedModifier);
            } else {
                mob.getNavigation().moveTo(target, speedModifier);
            }
            
            mob.switchMainHandItem(new ItemStack(Items.DIAMOND_SWORD));
            
            // 蜘蛛网控场 (对龙无效，因为龙会直接破坏方块)
            if (!isDragon && cobwebCooldown <= 0 && distSqr <= 25.0 && target.onGround() && mob.level().getBlockState(target.blockPosition()).canBeReplaced()) {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                mob.switchMainHandItem(new ItemStack(Items.COBWEB));
                mob.level().setBlock(target.blockPosition(), Blocks.COBWEB.defaultBlockState(), 3);
                mob.swing(InteractionHand.MAIN_HAND);
                mob.restoreMainHandItem();
                cobwebCooldown = 200; 
            }

            // 龙的近战触发距离放宽到 36格(6*6)，普通怪 12格
            double meleeTriggerDist = isDragon ? 36.0 : 12.0;

            if (attackCooldown <= 0 && distSqr <= meleeTriggerDist) {
                if (mob.isUsingItem()) mob.releaseUsingItem();
                
                if (mob.onGround() && !waitingForCrit) {
                    mob.jumpFromGround();
                    waitingForCrit = true; 
                } 
                else if (waitingForCrit && mob.getDeltaMovement().y < 0) {
                    mob.swing(InteractionHand.MAIN_HAND);
                    float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                    
                    // ★ 核心斩杀：直接调用龙头的受到伤害方法，并赋予屠龙者 2倍暴击
                    if (isDragon) {
                        dragon.head.hurt(mob.damageSources().mobAttack(mob), baseDmg * 2.0F);
                        mob.level().playSound(null, dragon.head.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    } else {
                        target.hurt(mob.damageSources().mobAttack(mob), baseDmg * 1.5F);
                        mob.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    }
                    
                    waitingForCrit = false;
                    attackCooldown = 20; 
                }
            } 
            else if (attackCooldown > 5 && distSqr <= meleeTriggerDist + 4.0 && !waitingForCrit) {
                if (!mob.isUsingItem()) {
                    mob.startUsingItem(InteractionHand.OFF_HAND);
                }
            } else if (attackCooldown <= 5) {
                if (mob.isUsingItem()) {
                    mob.releaseUsingItem();
                }
            }
        }

        if (attackCooldown > 0) attackCooldown--;
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        if (mob.isUsingItem()) mob.releaseUsingItem();
        mob.restoreMainHandItem();
        isMaceAttacking = false;
        waitingForCrit = false;
        maxFallDistance = 0f;
    }
}
