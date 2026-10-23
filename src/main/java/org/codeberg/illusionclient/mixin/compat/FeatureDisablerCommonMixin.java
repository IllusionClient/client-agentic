package org.codeberg.illusionclient.mixin.compat;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "io.github.axolotlclient.util.FeatureDisablerCommon", remap = false)
public class FeatureDisablerCommonMixin {
	@Dynamic
	@WrapWithCondition(method = "init", at = @At(value = "INVOKE", target = "Lio/github/axolotlclient/util/FeatureDisablerCommon;setServers(Lio/github/axolotlclient/util/options/ForceableBooleanOption;Ljava/util/function/Supplier;[Ljava/lang/String;)V", ordinal = 0))
	private boolean illusion$skipFreelookServers(@Coerce Object disabler, @Coerce Object option, Supplier<Boolean> condition, String[] servers) {
		return false;
	}
}
