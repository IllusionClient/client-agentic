package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import org.codeberg.illusionclient.util.HiddenLayer;
import org.codeberg.illusionclient.util.RenderUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class GlStateManagerMixin {
	@ModifyVariable(method = {"blendFunc", "blendFuncSeparate"}, at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private static int illusion$sourceFactor(int factor) {
		return RenderUtils.blendFactor(factor);
	}

	@ModifyVariable(method = {"blendFunc", "blendFuncSeparate"}, at = @At("HEAD"), ordinal = 1, argsOnly = true)
	private static int illusion$destinationFactor(int factor) {
		return RenderUtils.blendFactor(factor);
	}

	@ModifyVariable(method = "blendFuncSeparate", at = @At("HEAD"), ordinal = 2, argsOnly = true)
	private static int illusion$alphaSourceFactor(int factor) {
		return HiddenLayer.drawing() ? GL11.GL_ONE : factor;
	}

	@ModifyVariable(method = "blendFuncSeparate", at = @At("HEAD"), ordinal = 3, argsOnly = true)
	private static int illusion$alphaDestinationFactor(int factor) {
		return HiddenLayer.drawing() ? GL11.GL_ONE_MINUS_SRC_ALPHA : factor;
	}

	@WrapOperation(method = "blendFunc", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glBlendFunc(II)V", remap = false))
	private static void illusion$hiddenLayerBlendFunc(int source, int destination, Operation<Void> original) {
		original.call(source, destination);
		if (HiddenLayer.drawing()) GLX.blendFuncSeparate(source, destination, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	@Inject(method = "disableBlend", at = @At("HEAD"), cancellable = true)
	private static void illusion$keepBlend(CallbackInfo ci) {
		if (RenderUtils.constantAlpha()) ci.cancel();
	}
}
