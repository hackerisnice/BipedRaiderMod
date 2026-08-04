package com.yourmod.block;

import com.yourmod.util.IEntityDataSaver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class HeartBlock extends Block {

    public HeartBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof Player player) {
            // ★ 修改为强转
            ((IEntityDataSaver) player).getPersistentData().putLong("PlacedHeartBlockPos", pos.asLong());
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }
}
