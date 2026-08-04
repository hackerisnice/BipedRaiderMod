package com.yourmod;

import com.yourmod.client.renderer.CustomBipedRenderer;
import com.yourmod.client.renderer.FriendlyBipedRenderer;
import com.yourmod.registry.ModEntities; // 导入实体注册类
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class BipedRaiderModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.CUSTOM_BIPED, CustomBipedRenderer::new);
        EntityRendererRegistry.register(ModEntities.FRIENDLY_BIPED, FriendlyBipedRenderer::new);
    }
}
