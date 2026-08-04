package com.yourmod.mixin;

import com.yourmod.util.IEntityDataSaver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements IEntityDataSaver {
    private CompoundTag persistentData;

    @Override
    public CompoundTag getPersistentData() {
        if (this.persistentData == null) this.persistentData = new CompoundTag();
        return this.persistentData;
    }

    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    protected void injectSaveMethod(CompoundTag nbt, CallbackInfoReturnable<CompoundTag> info) {
        if (persistentData != null) nbt.put("bipedraidermod_data", persistentData);
    }

    @Inject(method = "load", at = @At("HEAD"))
    protected void injectLoadMethod(CompoundTag nbt, CallbackInfo info) {
        if (nbt.contains("bipedraidermod_data", 10)) {
            persistentData = nbt.getCompound("bipedraidermod_data");
        }
    }
}
