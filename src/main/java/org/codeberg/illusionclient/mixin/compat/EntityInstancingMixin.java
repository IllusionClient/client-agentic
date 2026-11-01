package org.codeberg.illusionclient.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.codeberg.illusionclient.feature.module.visual.Chams;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.entity.instancing.EntityInstancing", remap = false)
public class EntityInstancingMixin {
	@Dynamic
	@ModifyReturnValue(method = "isBatchActive", at = @At("RETURN"))
	private boolean illusion$drawChamsDirectly(boolean active) {
		return active && !Chams.rendering();
	}
}
