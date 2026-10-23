package org.codeberg.illusionclient.mixin.compat;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "io.github.axolotlclient.util.FeatureDisabler", remap = false)
public class FeatureDisablerMixin {
	@Dynamic
	@ModifyArg(method = "lambda$registerChannel$0", at = @At(value = "INVOKE", target = "Lio/github/axolotlclient/util/options/ForceableBooleanOption;setForceOff(ZLjava/lang/String;)V"), index = 0)
	private static boolean illusion$keepFreelook(boolean forceOff, @Local String feature) {
		return forceOff && !feature.equals("freelook");
	}
}
