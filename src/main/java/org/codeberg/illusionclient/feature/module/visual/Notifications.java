package org.codeberg.illusionclient.feature.module.visual;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.platform.GlStateManager;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.module.client.ClickGuiModule;
import org.codeberg.illusionclient.feature.module.profiles.CreateProfile;
import org.codeberg.illusionclient.feature.module.profiles.ProfileModule;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.HiddenLayer;

import java.util.List;

@ModuleInfo(name = "Notifications", category = Category.VISUAL)
@Description("Shows a popup in the corner when something happens, like a module being toggled or a profile being loaded.")
@ObsHidden
public class Notifications extends Module {
	private static final int MAX_SHOWN = 5;
	private static final long SLIDE_MS = 200L;
	private static final int PADDING = 4;
	private static final int MARGIN = 4;

	private final List<Entry> entries = new ObjectArrayList<>();

	@Description("Notifies when a module is turned on or off.")
	@Check("Module Toggles")
	public boolean moduleToggles = true;

	@Description("Notifies when a profile is loaded.")
	@Check("Profile Loads")
	public boolean profileLoads = true;

	@Description("How long each notification stays, in seconds.")
	@Slider(value = "Duration", min = 0.5F, max = 10.0F)
	public float duration = 2.0F;

	public static void post(String text) {
		Notifications notifications = IllusionClient.MODULES.get(Notifications.class);
		if (!notifications.isEnabled()) return;

		List<Entry> entries = notifications.entries;
		entries.add(new Entry(text, System.currentTimeMillis()));
		if (entries.size() > MAX_SHOWN) entries.removeFirst();
	}

	public static void toggled(Module module) {
		if (module instanceof ClickGuiModule || module instanceof CreateProfile || module instanceof ProfileModule || !IllusionClient.MODULES.get(Notifications.class).moduleToggles) return;
		post(module.name + (module.isEnabled() ? " enabled" : " disabled"));
	}

	public static void profileLoaded(String name) {
		if (IllusionClient.MODULES.get(Notifications.class).profileLoads) post("Loaded profile " + name);
	}

	@Override
	public void onDisable() {
		entries.clear();
	}

	@Listen
	public void onRender2D(Render2DEvent.Post event) {
		long now = System.currentTimeMillis();
		long lifetime = (long) (duration * 1000.0F);
		entries.removeIf(entry -> now - entry.posted >= lifetime);
		if (entries.isEmpty()) return;
		HiddenLayer.begin();

		int right = event.window().getWidth() - MARGIN;
		int height = 9 + PADDING * 2;
		int y = event.window().getHeight() - MARGIN - height;
		int accent = Draw.accent().getRGB();

		for (int i = entries.size() - 1; i >= 0; i--) {
			Entry entry = entries.get(i);
			int width = Draw.width(entry.text) + PADDING * 3;
			long age = now - entry.posted;
			float shown = Math.clamp(Math.min(age, lifetime - age) / (float) SLIDE_MS, 0.0F, 1.0F);
			int x = right - Math.round(width * shown);

			Draw.rounded(x, y, width, height, 3.0F, Draw.HUD_BACKGROUND);
			Draw.rect(x, y + 2, 2, height - 4, accent);
			Draw.text(entry.text, x + PADDING * 2, y + PADDING + 0.5F, 0xFFFFFFFF);
			y -= height + 2;
		}
		Draw.flush();

		GlStateManager.enableBlend();
		HiddenLayer.end();
	}

	private record Entry(String text, long posted) {
	}
}
