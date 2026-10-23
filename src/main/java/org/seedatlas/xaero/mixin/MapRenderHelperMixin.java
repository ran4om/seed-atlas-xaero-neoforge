package org.seedatlas.xaero.mixin;

import java.util.function.Supplier;
import net.minecraft.client.renderer.ShaderInstance;
import org.seedatlas.xaero.integration.ExploredTerrainRenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import xaero.lib.client.graphics.shader.LibShaders;
import xaero.map.graphics.MapRenderHelper;

/** Zoomed-out textures average only explored texels. */
@Mixin(value = MapRenderHelper.class, remap = false)
abstract class MapRenderHelperMixin {
    @ModifyArg(method = "renderBranchUpdate", at = @At(
        value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShader(Ljava/util/function/Supplier;)V"
    ))
    private static Supplier<ShaderInstance> seedAtlas$exploredOnlyAverage(Supplier<ShaderInstance> shader) {
        return () -> ExploredTerrainRenderTypes.branchShader(shader.get());
    }

    // Later quadrants draw without re-applying the shader, so the program is bound by id.
    @ModifyArg(method = "renderBranchUpdate", at = @At(
        value = "INVOKE",
        target = "Lcom/mojang/blaze3d/shaders/ProgramManager;glUseProgram(I)V"
    ))
    private static int seedAtlas$exploredOnlyProgram(int program) {
        ShaderInstance shader = ExploredTerrainRenderTypes.branchShader(LibShaders.WORLD_MAP_BRANCH);
        return shader != null ? shader.getId() : program;
    }
}
