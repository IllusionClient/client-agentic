package org.codeberg.illusionclient.feature.module.profiles;

import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

@Description("Saves your current settings as a new profile. The Default profile always keeps the default settings, so changing anything on it makes a new profile for you.")
@ModuleInfo(name = "Create Profile", category = Category.PROFILES, bindable = false)
public class CreateProfile extends Module {
	@Override
	public void onEnable() {
		setEnabled(false);
		IllusionClient.CONFIG.createProfile();
	}
}
