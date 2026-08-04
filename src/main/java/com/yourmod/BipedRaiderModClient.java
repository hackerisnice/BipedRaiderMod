package com.yourmod;

import com.yourmod.client.renderer.CustomBipedRenderer;
import com.yourmod.client.renderer.FriendlyBipedRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class BipedRaiderModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(BipedRaiderMod.CUSTOM_BIPED, CustomBipedRenderer::new);
        EntityRendererRegistry.register(BipedRaiderMod.FRIENDLY_BIPED, FriendlyBipedRenderer::new);
    }
}
