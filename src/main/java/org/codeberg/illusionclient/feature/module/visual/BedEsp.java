package org.codeberg.illusionclient.feature.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Box;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.*;
import org.codeberg.illusionclient.feature.module.api.*;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.util.bed.Bed;
import org.codeberg.illusionclient.util.render.EspRenderer;

import java.awt.*;
import java.util.Collection;
import java.util.List;

@Description("Draws boxes around beds.")
@ObsHidden
@ModuleInfo(name = "Bed ESP", category = Category.VISUAL)
public class BedEsp extends Module {
	private final EspRenderer<Bed> renderer = new EspRenderer<>();

	@Description("2D draws flat boxes on screen, 3D draws boxes in the world.")
	@Combo(value = "Mode", options = {"2D", "3D"})
	public String mode = "2D";

	@Description("Draws the faces of the 3D box.")
	@Requires(value = "mode", is = "3D")
	@Check("Faces")
	public boolean faces = true;

	@Description("Draws the edges of the 3D box.")
	@Requires(value = "mode", is = "3D")
	@Check("Outline")
	public boolean outline = false;

	@Description("Thickness of the edges of the 3D box.")
	@Requires(value = "mode", is = "3D")
	@Slider(value = "Outline Width", min = 1.0F, max = 10.0F)
	public float outlineWidth = 2.0F;

	@Description("The color.")
	@ColorPicker("Color")
	public Color color = new Color(0xA31600);

	@Description("Use the full block width")
	@Check("Full Width")
	public boolean fullWidth = false;

	@Description("Render each half of the bed separately.")
	@Check("Separate Parts")
	public boolean separateParts = false;

	@Override
	public void onDisable() {
		renderer.clear();
	}

	@Listen
	public void onWorldLoad(WorldLoadEvent event) {
		renderer.clear();
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		renderer.clear();

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.world == null) return;

		Collection<Bed> beds = IllusionClient.BEDS.beds();
		if (beds.isEmpty()) return;

		renderer.begin();
		boolean flat = mode.equals("2D");
		for (Bed bed : beds) {
			Collection<Box> boxes = separateParts
				? List.of(box(bed.x, bed.y, bed.z), box(bed.footX, bed.y, bed.footZ))
				: List.of(box(bed));

			for (Box box : boxes) {
				if (flat) {
					renderer.add(bed, box);
					continue;
				}

				if (faces) renderer.box3D(box, color.getRGB());
				if (outline) renderer.outline3D(box, color.getRGB(), outlineWidth);
			}
		}
		renderer.end3D();
	}

	@Listen
	public void onRender2D(Render2DEvent.Pre event) {
		if (!mode.equals("2D")) return;
		renderer.render2D(event, (bed, left, top, right, bottom) -> EspRenderer.box(left, top, right, bottom, color.getRGB()));
	}

	private Box box(Bed bed) {
		double minX = Math.min(bed.x, bed.footX);
		double maxX = Math.max(bed.x, bed.footX) + 1.0D;

		double minZ = Math.min(bed.z, bed.footZ);
		double maxZ = Math.max(bed.z, bed.footZ) + 1.0D;

		return new Box(
			minX, bed.y, minZ,
			maxX, bed.y + (fullWidth ? 1.0D : 0.5625D), maxZ
		);
	}

	private Box box(int x, int y, int z) {
		return new Box(
			x, y, z,
			x + 1.0D, y + (fullWidth ? 1.0D : 0.5625D), z + 1.0D
		);
	}
}
