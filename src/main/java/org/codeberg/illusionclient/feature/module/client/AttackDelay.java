package org.codeberg.illusionclient.feature.module.client;

import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

@Description("Sets how long combat modules like Hit Select, Criticals and Sprint Reset wait between attacks.")
@ModuleInfo(name = "Attack Delay", category = Category.CLIENT, bindable = false, toggleable = false)
public class AttackDelay extends Module {
	@Description("The time between attacks that combat modules plan around.")
	@Slider(value = "Delay (ms)", min = 0.0F, max = 1000.0F)
	public float delay = 500.0F;

	public static float ms() {
		return IllusionClient.MODULES.get(AttackDelay.class).delay;
	}
}
