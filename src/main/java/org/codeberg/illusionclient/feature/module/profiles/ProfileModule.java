package org.codeberg.illusionclient.feature.module.profiles;

import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.ConfigManager;
import org.codeberg.illusionclient.feature.config.api.Combo;
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
	public static final String NO_TAG = "None";
	public static final String BINDS = "Binds";

	@Description("Settings from these categories carry over from your current profile instead of being loaded from this one. Binds keeps your current keybinds.")
	@MultiSelect(value = "Keep", options = {"Client", "Combat", "Movement", "Player", "Visual", BINDS})
	public Set<String> keep = Set.of("Client", "Visual", BINDS);

	@Description("How risky this profile is to play with.")
	@Combo(value = "Tag", options = {NO_TAG, "Closet", "Semi", "Blatant"})
	public String tag = NO_TAG;

	public final String profile;
	public long created, updated;

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
