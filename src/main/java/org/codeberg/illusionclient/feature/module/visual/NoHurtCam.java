package org.codeberg.illusionclient.feature.module.visual;

import net.fabricmc.loader.api.FabricLoader;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;

import java.lang.reflect.Field;

@ModuleInfo(name = "No Hurt Cam", category = Category.VISUAL)
@Description("Stops the camera from shaking when you take damage. Also toggles the matching setting in AxolotlClient and Argentum Extras.")
@ObsHidden(ObsHidden.Level.NO)
public class NoHurtCam extends Module {
	private int argentumStrength = 100;

	@Override
	public void onEnable() {
		sync(true);
	}

	@Override
	public void onDisable() {
		sync(false);
	}

	private void sync(boolean enabled) {
		FabricLoader loader = FabricLoader.getInstance();
		try {
			if (loader.isModLoaded("axolotlclient")) {
				Object config = Class.forName("io.github.axolotlclient.AxolotlClientConfigCommon").getMethod("instance").invoke(null);
				Object option = config.getClass().getField("noHurtCam").get(config);
				option.getClass().getMethod("set", Object.class).invoke(option, enabled);
			}
			if (loader.isModLoaded("argentum-extras")) {
				Object config = Class.forName("dev.rdh.argentum.extras.ArgentumExtras").getField("CONFIG").get(null);
				Field strength = config.getClass().getField("hurtCameraStrength");
				if (enabled) {
					int current = strength.getInt(config);
					if (current != 0) argentumStrength = current;
				}
				strength.setInt(config, enabled ? 0 : argentumStrength);
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			IllusionClient.LOGGER.debug("Could not sync No Hurt Cam with other mods", e);
		}
	}
}
