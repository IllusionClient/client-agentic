package org.codeberg.illusionclient.feature.ui.component;

import org.codeberg.illusionclient.IllusionClient;

import java.util.List;

public class ProfileComponent extends Component {
	@Override
	public void render(int mouseX, int mouseY) {
		row("Profile", mouseX, mouseY);
		String active = IllusionClient.CONFIG.profile;
		value(active == null ? "None" : active);
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int button) {
		if (!hovered(mouseX, mouseY) || button != 0 && button != 1) return;

		List<String> profiles = IllusionClient.CONFIG.profiles();
		if (profiles.isEmpty()) return;

		int step = button == 0 ? 1 : -1;
		int next = Math.floorMod(profiles.indexOf(IllusionClient.CONFIG.profile) + step, profiles.size());
		IllusionClient.CONFIG.loadProfile(profiles.get(next));
	}
}
