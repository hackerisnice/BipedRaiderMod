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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.EnumSet;

/**
 * 暴怒反击战术：
 * 当怪物累计被玩家攻击 3 次后（怒气叠满），会进入“四周走搭”状态（围绕玩家螺旋搭高），
 * 到达合适高度后切出重锤，向玩家发起下落猛击。
 */
public class EscapeBoatAndBuildUpGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    
    // 累计挨打次数系统 (怒气槽)
    private int comboHits = 0;
    private int lastHurtTime = 0;

    // 技能阶段：0=未激活，1=走搭升空，2=重锤下落
    private int phase = 0;
    private int scaffoldTicks = 0;
    
    // 物理状态记录
    private float maxFallDistance = 0f;
    private boolean wasOnGround = true;

    public EscapeBoatAndBuildUpGoal(CustomBipedEntity mob, double speedModifier) {
        this.mob = mob;
        this.level = mob.level();
        // 允许 Goal 接管怪物的移动、视线和跳跃
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        // --- 1. 累计受击检测 ---
        int currentHurt = mob.getLastHurtByMobTimestamp();
        // 如果受击时间戳发生变化，说明挨打了一次
        if (currentHurt != lastHurtTime) {
            lastHurtTime = currentHurt;
            comboHits++; // 只要挨打就无条件累加，不随时间重置！
        }

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        // --- 2. 触发条件：累计挨打达到 3 次 ---
        if (comboHits >= 3) {
            return true;
        }
        
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        // 只要阶段不为 0 (技能未结束)，且目标存活，就强行继续
        return phase > 0 && target != null && target.isAlive();
    }

    @Override
    public void start() {
        comboHits = 0;            // 触发大招后，清空怒气槽
        phase = 1;                // 进入阶段 1：走搭
        scaffoldTicks = 0;
        maxFallDistance = 0f;
        wasOnGround = mob.onGround();
        
        // 切出建筑材料 (圆石)
        mob.switchMainHandItem(new ItemStack(Items.COBBLESTONE));
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        // ================= 阶段 1：四周走搭 =================
        if (phase == 1) {
            scaffoldTicks++;

            // 1. 动态计算玩家周围的环绕坐标 (形成螺旋走位)
            double angle = mob.tickCount * 0.15; // 旋转速度
            double radius = 4.0;                 // 环绕半径 (距玩家 4 格)
            double targetX = target.getX() + Math.cos(angle) * radius;
            double targetZ = target.getZ() + Math.sin(angle) * radius;
            
            // 始终盯着玩家
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            // 2. 强行覆写移动矢量，让怪物在空中也能灵活转向
            Vec3 moveDir = new Vec3(targetX - mob.getX(), 0, targetZ - mob.getZ());
            if (moveDir.lengthSqr() > 0.01) {
                moveDir = moveDir.normalize().scale(0.22); // 走搭的水平移动速度
                mob.setDeltaMovement(moveDir.x, mob.getDeltaMovement().y, moveDir.z);
            }

            // 3. 疯狂跳跃以获取高度
            if (mob.onGround()) {
                mob.jumpFromGround();
            }

            // 4. 精准在脚底铺设方块 (只要脚底变空，立刻塞方块)
            BlockPos posUnderFeet = BlockPos.containing(mob.getX(), mob.getY() - 0.1, mob.getZ());
            if (level.getBlockState(posUnderFeet).canBeReplaced()) {
                level.setBlock(posUnderFeet, Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.playSound(null, posUnderFeet, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                mob.swing(InteractionHand.MAIN_HAND);
            }

            // 5. 阶段转换判断：如果搭得比玩家高 4.5 格以上，且持续了至少 1.5 秒
            if (mob.getY() > target.getY() + 4.5 && scaffoldTicks > 30) {
                phase = 2; // 进入阶段 2：重锤下落
                mob.switchMainHandItem(new ItemStack(Items.MACE));
            }
        } 
        
        // ================= 阶段 2：天降重锤 =================
        else if (phase == 2) {
            if (!mob.onGround()) {
                // 在空中记录最大下落高度
                maxFallDistance = Math.max(maxFallDistance, mob.fallDistance);
                
                // 空中追踪微调：让它在下落时依然能向着玩家滑翔，避免玩家走位躲开
                Vec3 dropDir = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
                if (dropDir.lengthSqr() > 0.01) {
                    dropDir = dropDir.normalize().scale(0.12); // 空中横移速度
                    mob.setDeltaMovement(dropDir.x, mob.getDeltaMovement().y, dropDir.z);
                }
            } else {
                // 落地判定 (!wasOnGround 表示上一刻在空中，当前刻在地面)
                if (!wasOnGround) {
                    // 如果下落超过 1.5 格，且砸在玩家附近 16 格以内
                    if (maxFallDistance > 1.5f && target.distanceToSqr(mob) < 16.0) {
                        mob.swing(InteractionHand.MAIN_HAND);
                        
                        // 计算巨额重击伤害
                        float baseDmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
                        float fallBonus = Math.min(maxFallDistance, 20) * 1.5f; 
                        target.hurt(mob.damageSources().mobAttack(mob), baseDmg + fallBonus + 3.0f);
                        
                        // 播放爆炸般的砸地音效
                        level.playSound(null, mob.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.0F, 1.0F);
                    }
                    
                    // 连招结束，退出 AI 状态
                    phase = 0; 
                }
            }
        }
        
        wasOnGround = mob.onGround();
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem(); // 无论如何都会安全还原之前的武器
        phase = 0;
        comboHits = 0; // 如果因为意外被打断，怒气槽也会清零（你也可以选择不清零）
        scaffoldTicks = 0;
        maxFallDistance = 0f;
    }
}
