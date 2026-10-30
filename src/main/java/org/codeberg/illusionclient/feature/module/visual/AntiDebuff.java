package org.codeberg.illusionclient.feature.module.visual;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.entity.living.effect.StatusEffect;
import org.codeberg.illusionclient.event.render.EffectVisualEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;

@ModuleInfo(name = "Anti Debuff", category = Category.VISUAL)
@Description("Hides negative visual effects.")
@ObsHidden(ObsHidden.Level.NO)
public class AntiDebuff extends Module {
	@Description("Removes the dark fog from Blindness.")
	@Check("Blindness")
	public boolean blindness = true;

	@Description("Removes the screen wobble from Nausea.")
	@Check("Nausea")
	public boolean nausea = true;

	@Listen
	public void onEffectVisual(EffectVisualEvent event) {
		if (blindness && event.effect == StatusEffect.BLINDNESS || nausea && event.effect == StatusEffect.NAUSEA) {
			event.visible = false;
		}
	}
}
