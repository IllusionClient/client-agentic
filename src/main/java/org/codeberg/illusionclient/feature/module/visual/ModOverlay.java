package org.codeberg.illusionclient.feature.module.visual;

import org.codeberg.illusionclient.event.api.Listen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.platform.GlStateManager;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.profiles.ProfileModule;
import org.codeberg.illusionclient.feature.module.tags.TagModule;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.feature.ui.Font;
import org.codeberg.illusionclient.util.HiddenLayer;

import java.awt.*;
import java.util.List;
import java.util.Locale;

@ModuleInfo(name = "Mod Overlay", category = Category.VISUAL, aliases = {"Arraylist"})
@Description("Lists enabled modules in the corner.")
@ObsHidden
public class ModOverlay extends Module {
	private final List<Row> rows = new ObjectArrayList<>();
	private Row[] all;

	@Description("How large the text is.")
	@Slider(value = "Text Size", min = 0.5F, max = 3.0F)
	public float textSize = 1.0F;

	@Description("Space to the left and right of each name.")
	@Slider(value = "Padding X", min = 0.0F, max = 10.0F)
	public float paddingX = 3.0F;

	@Description("Space above and below each name.")
	@Slider(value = "Padding Y", min = 0.0F, max = 10.0F)
	public float paddingY = 2.0F;

	@Description("How rounded the background corners are.")
	@Slider(value = "Rounding", min = 0.0F, max = 10.0F)
	public float rounding = 3.0F;

	@Description("The color of module names.")
	@ColorPicker("Text Color")
	public Color textColor = new Color(41, 227, 143);

	@Description("Shows mode or main setting beside each name.")
	@Check("Show Details")
	public boolean showDetails = true;

	@Description("The color of the details.")
	@Requires("showDetails")
	@ColorPicker("Details Color")
	public Color detailsColor = new Color(0xAAAAAA);

	@Description("Writes the list in lowercase.")
	@Check("Lowercase")
	public boolean lowercase = true;

	@Description("Only lists modules that have a keybind.")
	@Check("Only Bound Modules")
	public boolean onlyBoundModules = false;

	@Listen
	public void onRender2D(Render2DEvent.Post event) {
		int gap = Draw.width(" ");

		if (all == null) {
			all = IllusionClient.MODULES.modules.stream()
				.filter(module -> module != this && !(module instanceof ProfileModule || module instanceof TagModule))
				.map(Row::new)
				.toArray(Row[]::new);
		}

		rows.clear();
		for (Row row : all) {
			Module module = row.module;
			if (!module.isEnabled()) continue;
			if (onlyBoundModules && module.getBind() == 0) continue;

			boolean restyled = row.lowercase != lowercase || row.font != Draw.font();
			if (restyled) {
				row.lowercase = lowercase;
				row.font = Draw.font();
				row.name = style(module.name);
				row.nameWidth = Draw.width(row.name);
			}
			if (restyled || row.showDetails != showDetails || row.detailsVersion != module.detailsVersion()) {
				row.showDetails = showDetails;
				row.detailsVersion = module.detailsVersion();
				String details = showDetails ? module.details() : null;
				row.details = details == null ? null : style(details);
				row.detailsWidth = details == null ? 0 : Draw.width(row.details);
			}
			float textWidth = row.nameWidth + (row.details == null ? 0 : gap + row.detailsWidth);
			row.width = Math.round(textWidth * textSize + paddingX * 2.0F);
			rows.add(row);
		}
		if (rows.isEmpty()) return;
		rows.sort((a, b) -> b.width - a.width);
		HiddenLayer.begin();

		int right = event.window().getWidth();
		int height = Math.round(9 * textSize + paddingY * 2.0F);

		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			float radius = i + 1 < rows.size() ? Math.min(rounding, row.width - rows.get(i + 1).width) : rounding;
			int overhang = (int) Math.ceil(radius);
			Draw.rounded(right - row.width, i * height, row.width + overhang, height, 0.0F, radius, Draw.HUD_BACKGROUND);
		}
		Draw.flush();

		GlStateManager.pushMatrix();
		GlStateManager.scalef(textSize, textSize, 1.0F);
		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			float x = (right - row.width + paddingX) / textSize;
			float y = (i * height + paddingY) / textSize + 0.5F;

			Draw.text(row.name, x, y, textColor.getRGB());
			if (row.details != null) Draw.text(row.details, x + row.nameWidth + gap, y, detailsColor.getRGB());
		}
		Draw.flush();
		GlStateManager.popMatrix();

		GlStateManager.enableBlend();
		HiddenLayer.end();
	}

	private String style(String text) {
		return lowercase ? text.toLowerCase(Locale.ROOT) : text;
	}

	private static final class Row {
		final Module module;
		Font font;
		boolean lowercase;
		String name;
		boolean showDetails;
		int detailsVersion = -1;
		String details;
		int nameWidth;
		int detailsWidth;
		int width;

		Row(Module module) {
			this.module = module;
		}
	}
}
