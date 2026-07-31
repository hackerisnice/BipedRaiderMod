package com.yourmod.entity;

import com.yourmod.BipedRaiderMod;
import com.yourmod.entity.ai.*;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.HolderLookup;

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
        this.goalSelector.addGoal(2, new EscapeBoatAndBuildUpGoal(this, 1.2D));
        
        // ★ 加入全新的放岩浆坑人 AI
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
