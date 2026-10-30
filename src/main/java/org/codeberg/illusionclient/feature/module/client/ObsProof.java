package org.codeberg.illusionclient.feature.module.client;

import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.HiddenLayer;

@ModuleInfo(name = "OBS Proof", category = Category.CLIENT)
@Description("Experimental. Hides green OBS modules from OBS Game Capture and obs-vkcapture. Yellow ones depend on their settings, red ones always show. Window and Display Capture still see everything.")
public class ObsProof extends Module {
	@Description("Stops hidden visuals from drawing over screens like chests and inventories.")
	@Check("Hide In Screen")
	public boolean hideInScreen = true;

	@Override
	public void onEnable() {
		HiddenLayer.setActive(true);
	}

	@Override
	public void onDisable() {
		HiddenLayer.setActive(false);
	}
}
