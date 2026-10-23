package org.seedatlas.xaero.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xaero.map.region.texture.BranchTextureRenderer;

/** Keep unknown quadrants transparent when Xaero builds zoomed-out textures. */
@Mixin(value = BranchTextureRenderer.class, remap = false)
abstract class BranchTextureRendererMixin {
    // A new texture is cleared before its quadrants are drawn. The map shader discards RGBA zero;
    // opaque black would hide the seed background where a quadrant has no explored terrain.
    @ModifyArg(method = "render", at = @At(
        value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clearColor(FFFF)V",
        ordinal = 1
    ), index = 3)
    private float seedAtlas$transparentUnknownTerrain(float alpha) {
        return 0.0F;
    }
}
