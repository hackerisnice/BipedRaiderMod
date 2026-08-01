package com.yourmod.entity;

import com.yourmod.BipedRaiderMod;
import com.yourmod.entity.ai.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.HolderLookup;

import java.util.List;

public class CustomBipedEntity extends Monster {

    @Nullable
    private ItemStack savedMainHandItem = null;
    private int goldenApplesEaten = 0;
    private static final int MAX_APPLES = 5;

    public CustomBipedEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        return super.finalizeSpawn(level, difficulty, reason, spawnData);
    }

    // ★ 核心机制 1：宁死不屈！拒绝被船和矿车装走，并直接把载具踩碎！
    @Override
    public boolean startRiding(net.minecraft.world.entity.Entity vehicle, boolean force) {
        if (vehicle instanceof Boat || vehicle instanceof Minecart) {
            vehicle.discard(); // 直接删除船
            this.level().playSound(null, this.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 1.0F);
            return false;
        }
        return super.startRiding(vehicle, force);
    }

    @Override
    public void tick() {
        super.tick();
        
        if (!this.level().isClientSide && this.isAlive()) {
            // ★ 核心机制 2：主动踩碎靠近的船只 (防玩家推船)
            List<Boat> boats = this.level().getEntitiesOfClass(Boat.class, this.getBoundingBox().inflate(1.5D));
            for (Boat boat : boats) {
                boat.discard();
                this.level().playSound(null, this.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 1.0F, 1.0F);
            }

            // ★ 核心机制 3：粉碎光环！检测头顶(防沙子铁砧)和脚部(防速搭卡死)的固体方块
            BlockPos headPos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            BlockPos legPos = this.blockPosition();
            
            if (this.level().getBlockState(headPos).blocksMotion()) {
                this.level().destroyBlock(headPos, true, this); // 瞬间粉碎沙子/铁砧
            }
            if (this.level().getBlockState(legPos).blocksMotion()) {
                this.level().destroyBlock(legPos, true, this); // 瞬间粉碎卡住脚的方块
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide) {
            Player nearestPlayer = this.level().getNearestPlayer(this, 16.0D);
            FriendlyBipedEntity friendly = BipedRaiderMod.FRIENDLY_BIPED.get().create(this.level());
            
            if (friendly != null && nearestPlayer != null) {
                friendly.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                friendly.tame(nearestPlayer);
                friendly.setCustomName(net.minecraft.network.chat.Component.literal("Aiko"));
                friendly.setCustomNameVisible(true);
                friendly.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                this.level().addFreshEntity(friendly);
            }
        }
        super.die(cause);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EatEnchantedGoldenAppleGoal(this));
        
        // 我们等下重写它，确保它完美运行
        this.goalSelector.addGoal(2, new EscapeBoatAndBuildUpGoal(this, 1.2D));
        
        this.goalSelector.addGoal(3, new LavaTrapGoal(this));
        this.goalSelector.addGoal(4, new AxeBreakShieldGoal(this));
        this.goalSelector.addGoal(5, new ThrowHarmingPotionAtFeetGoal(this));
        this.goalSelector.addGoal(6, new BreakBlockToReachTargetGoal(this));
        this.goalSelector.addGoal(7, new EnderPearlTeleportGoal(this));
        this.goalSelector.addGoal(8, new MeleeAttackGoal(this, 1.5D, true));
        this.goalSelector.addGoal(9, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(11, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public boolean canEatApple() {
        return goldenApplesEaten < MAX_APPLES;
    }

    public void consumeApple() {
        goldenApplesEaten++;
    }

    public void switchMainHandItem(ItemStack newItem) {
        if (savedMainHandItem == null) {
            savedMainHandItem = this.getMainHandItem().copy();
        }
        this.setItemInHand(InteractionHand.MAIN_HAND, newItem);
    }

    public void restoreMainHandItem() {
        if (savedMainHandItem != null) {
            this.setItemInHand(InteractionHand.MAIN_HAND, savedMainHandItem.copy());
            savedMainHandItem = null;
        }
    }

    public boolean isMainHandSaved() {
        return savedMainHandItem != null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        HolderLookup.Provider provider = this.level().registryAccess();
        if (this.savedMainHandItem != null && !this.savedMainHandItem.isEmpty()) {
            tag.put("SavedMainHandItem", this.savedMainHandItem.saveOptional(provider));
        }
        tag.putInt("GoldenApplesEaten", this.goldenApplesEaten);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        HolderLookup.Provider provider = this.level().registryAccess();
        if (tag.contains("SavedMainHandItem")) {
            this.savedMainHandItem = ItemStack.parseOptional(provider, tag.getCompound("SavedMainHandItem"));
        } else {
            this.savedMainHandItem = ItemStack.EMPTY;
        }
        if (tag.contains("GoldenApplesEaten")) {
            this.goldenApplesEaten = tag.getInt("GoldenApplesEaten");
        }
    }
}
