package org.codeberg.illusionclient.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import org.codeberg.illusionclient.feature.module.visual.Chams;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "dev.rdh.argentum.impl.render.terrain.ArgentumWorldRenderer", remap = false)
public class ArgentumWorldRendererMixin {
	@Dynamic
	@ModifyReturnValue(method = "isEntityVisible", at = @At("RETURN"))
	private boolean illusion$skipCullingChams(boolean visible, Entity entity) {
		return visible || Chams.shows(entity);
	}
}
