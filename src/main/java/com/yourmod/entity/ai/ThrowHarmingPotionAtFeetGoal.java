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

/**
 * 脚底偷袭投药：当目标玩家龟缩防守时，向脚底投掷瞬间伤害 II 喷溅药水。
 */
public class ThrowHarmingPotionAtFeetGoal extends Goal {

    private final CustomBipedEntity mob;
    private final Level level;
    private int cooldown = 0;
    private static final int COOLDOWN_TICKS = 80; // 4 秒冷却

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
        if (!(target instanceof Player player) || !target.isAlive()) return false;
        
        // 距离判定：必须在 8 格以内
        if (mob.distanceToSqr(target) > 8.0 * 8.0) return false;

        // 放宽条件：不再检查头顶方块。
        // 如果玩家正在举盾，或者单纯只是一直在这个距离内，都可以触发投掷。
        
        // 确保玩家脚底不是被完全封死的方块（防止药水砸不进去）
        BlockPos feetPos = target.blockPosition();
        if (level.getBlockState(feetPos).blocksMotion() &&
            level.getBlockState(feetPos.above()).blocksMotion()) {
            return false;
        }
        
        return true;
    }


    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        // 使用 1.21 数据组件创建瞬间伤害 II 喷溅药水
        ItemStack potionStack = new ItemStack(Items.SPLASH_POTION);
        PotionContents contents = new PotionContents(Potions.STRONG_HARMING);
        potionStack.set(DataComponents.POTION_CONTENTS, contents);

        // 切换主手为药水
        mob.switchMainHandItem(potionStack);

        // 看向目标脚底
        Vec3 targetPos = target.position();
        mob.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z);

        // 挥动手臂
        mob.swing(InteractionHand.MAIN_HAND);

        // 生成 ThrownPotion 实体，计算抛物线投向脚底
        ThrownPotion potionEntity = new ThrownPotion(level, mob);
        potionEntity.setItem(potionStack);

        // 计算投掷向量：从怪物眼睛到目标脚底，并增加预判（这里简单直射）
        Vec3 throwVec = targetPos.subtract(mob.getEyePosition());
        double horizontalDist = throwVec.horizontalDistance();
        double verticalDist = throwVec.y;
        // 设置合适的速度：标准药水投掷参数
        float velocity = 0.5F;
        float inaccuracy = 0.2F; // 微小随机偏差
        potionEntity.shoot(throwVec.x, throwVec.y + horizontalDist * 0.2, throwVec.z, velocity, inaccuracy);
        potionEntity.setPos(mob.getEyePosition().x, mob.getEyePosition().y - 0.1, mob.getEyePosition().z);
        level.addFreshEntity(potionEntity);

        // 播放投掷音效
        level.playSound(null, mob.blockPosition(), SoundEvents.SPLASH_POTION_THROW, SoundSource.HOSTILE, 1.0F, 1.0F);

        // 投掷完毕立即还原主手武器
        mob.restoreMainHandItem();
        cooldown = COOLDOWN_TICKS + mob.getRandom().nextInt(40); // 3~5 秒动态冷却
    }

    @Override
    public void stop() {
        mob.restoreMainHandItem(); // 异常退出时保底还原
    }
}
