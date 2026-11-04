package org.codeberg.illusionclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.living.player.PlayerEntity;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.combat.AutoBlock;
import org.codeberg.illusionclient.util.PlayerUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
	@ModifyReturnValue(method = "getItemUseTimer", at = @At("RETURN"))
	private int illusion$forceBlockAnimation(int timer) {
		return timer > 0 || !PlayerUtils.isLocalPlayer(this) || !IllusionClient.MODULES.get(AutoBlock.class).forcesBlockAnimation() ? timer : 1;
	}
}
