    @Override
    public void die(DamageSource cause) {
        if (!this.level().isClientSide) {
            Player player = this.level().getNearestPlayer(this, 16.0D);
            if (player != null) {
                
                // 扫描 1：身上有没有心脏方块？
                boolean hasItem = false;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.is(BipedRaiderMod.HEART_BLOCK_ITEM.get())) {
                        hasItem = true;
                        break;
                    }
                }
                
                // 扫描 2：世界里有没有放下的心脏方块？
                boolean hasPlaced = false;
                long posLong = player.getPersistentData().getLong("PlacedHeartBlockPos");
                if (posLong != 0) {
                    BlockPos p = BlockPos.of(posLong);
                    if (player.level().getBlockState(p).is(BipedRaiderMod.HEART_BLOCK.get())) {
                        hasPlaced = true;
                    } else {
                        // 兜底：如果方块被苦力怕炸了，清除无效数据
                        player.getPersistentData().remove("PlacedHeartBlockPos");
                    }
                }

                // 扫描 3：世界里有没有你活着的保镖？
                boolean hasAiko = false;
                if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    for (net.minecraft.world.entity.Entity e : serverLevel.getAllEntities()) {
                        if (e instanceof FriendlyBipedEntity aiko && player.getUUID().equals(aiko.getOwnerUUID())) {
                            hasAiko = true;
                            break;
                        }
                    }
                }

                // ★ 命运裁决：如果有其中任何一样，掉落珍贵的信标；如果你一无所有，掉落心脏方块。
                if (hasItem || hasPlaced || hasAiko) {
                    this.spawnAtLocation(Items.BEACON);
                } else {
                    this.spawnAtLocation(BipedRaiderMod.HEART_BLOCK_ITEM.get());
                }
            } else {
                // 如果附近没玩家，默认掉落心脏
                this.spawnAtLocation(BipedRaiderMod.HEART_BLOCK_ITEM.get());
            }
        }
        
        // 执行原版死亡逻辑（移除强制召唤 Aiko）
        super.die(cause);
    }
