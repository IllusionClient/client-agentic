package org.codeberg.illusionclient.feature.module.profiles;

import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.ConfigManager;
import org.codeberg.illusionclient.feature.config.api.MultiSelect;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

import java.util.Objects;
import java.util.Set;

@Description("Switches to this profile.")
@ModuleInfo(name = "Profile", category = Category.PROFILES)
public class ProfileModule extends Module {
	@Description("Settings from these categories carry over from your current profile instead of being loaded from this one.")
	@MultiSelect(value = "Keep", options = {"Client", "Combat", "Movement", "Player", "Visual"})
	public Set<String> keep = Set.of("Client", "Visual");

	public final String profile;

	public ProfileModule(String profile) {
		super(profile, "Profile:" + profile);
		this.profile = profile;
	}

	public boolean active() {
		return profile.equals(Objects.requireNonNullElse(IllusionClient.CONFIG.profile, ConfigManager.DEFAULT_PROFILE));
	}

	@Override
	public void onEnable() {
		if (!active()) IllusionClient.CONFIG.loadProfile(profile);
	}

	@Override
	public void onDisable() {
		if (active()) setEnabled(true);
	}
}
