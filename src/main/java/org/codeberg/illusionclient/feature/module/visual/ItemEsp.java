package org.codeberg.illusionclient.feature.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.render.EspRenderer;

import java.awt.*;

@Description("Draws boxes around dropped items.")
@ObsHidden
@ModuleInfo(name = "Item ESP", category = Category.VISUAL)
public class ItemEsp extends Module {
	private static final double RADIUS = 0.2D;
	private static final double HEIGHT = 0.45D;

	private final EspRenderer<ItemEntity> renderer = new EspRenderer<>();

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

	@Description("How far away dropped items are drawn.")
	@Slider(value = "Range", min = 4.0F, max = 128.0F, step = 1.0F)
	public float range = 64.0F;

	@Description("Shows how many items are in a stack. Single items show no count.")
	@Check("Show Count")
	public boolean showCount = true;

	@Section("Items")
	@Description("Highlights iron ingots.")
	@Check("Iron")
	public boolean iron = true;

	@Description("The color of iron boxes.")
	@Requires("iron")
	@ColorPicker("Iron Color")
	public Color ironColor = new Color(0xE0E0E0);

	@Description("Highlights gold ingots.")
	@Check("Gold")
	public boolean gold = true;

	@Description("The color of gold boxes.")
	@Requires("gold")
	@ColorPicker("Gold Color")
	public Color goldColor = new Color(0xFFD54F);

	@Description("Highlights diamonds.")
	@Check("Diamond")
	public boolean diamond = true;

	@Description("The color of diamond boxes.")
	@Requires("diamond")
	@ColorPicker("Diamond Color")
	public Color diamondColor = new Color(0x4DD0E1);

	@Description("Highlights emeralds.")
	@Check("Emerald")
	public boolean emerald = true;

	@Description("The color of emerald boxes.")
	@Requires("emerald")
	@ColorPicker("Emerald Color")
	public Color emeraldColor = new Color(0x66BB6A);

	@Description("Highlights golden apples.")
	@Check("Golden Apple")
	public boolean goldenApple = true;

	@Description("The color of golden apple boxes.")
	@Requires("goldenApple")
	@ColorPicker("Golden Apple Color")
	public Color goldenAppleColor = new Color(0xFFA000);

	@Description("Highlights ender pearls.")
	@Check("Ender Pearl")
	public boolean enderPearl = true;

	@Description("The color of ender pearl boxes.")
	@Requires("enderPearl")
	@ColorPicker("Ender Pearl Color")
	public Color enderPearlColor = new Color(0x26A69A);

	@Description("Highlights every other dropped item.")
	@Check("Highlight All")
	public boolean highlightAll = false;

	@Description("The color of default item boxes.")
	@Requires("highlightAll")
	@ColorPicker("Default Color")
	public Color defaultColor = new Color(0xFFFFFF);

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
		if (minecraft.world == null || minecraft.player == null) return;

		renderer.begin();
		boolean flat = mode.equals("2D");
		float tickDelta = event.partialTicks();
		double maxDistance = range * range;
		for (Entity entity : minecraft.world.entities) {
			if (!(entity instanceof ItemEntity item) || item.getItem() == null) continue;
			if (item.squaredDistanceTo(minecraft.player) > maxDistance) continue;

			int color = color(item.getItem().getItem());
			if (color == 0) continue;

			double x = item.prevX + (item.x - item.prevX) * tickDelta;
			double y = item.prevY + (item.y - item.prevY) * tickDelta;
			double z = item.prevZ + (item.z - item.prevZ) * tickDelta;
			Box box = new Box(x - RADIUS, y, z - RADIUS, x + RADIUS, y + HEIGHT, z + RADIUS);

			if (flat || showCount && item.getItem().size > 1) renderer.add(item, box);
			if (!flat) {
				if (faces) renderer.box3D(box, color);
				if (outline) renderer.outline3D(box, color, outlineWidth);
			}
		}
		renderer.end3D();
	}

	@Listen
	public void onRender2D(Render2DEvent.Pre event) {
		boolean flat = mode.equals("2D");
		int scale = event.window().getScale();

		renderer.render2D(event, (item, left, top, right, bottom) -> {
			ItemStack stack = item.getItem();
			if (flat) EspRenderer.box(left, top, right, bottom, color(stack.getItem()));
			if (showCount && stack.size > 1) count(String.valueOf(stack.size), (left + right) / 2.0F / scale, top / (float) scale, scale);
		});
	}

	private static void count(String text, float centerX, float top, int scale) {
		Draw.flush();
		GlStateManager.pushMatrix();
		GlStateManager.scaled(scale, scale, 1.0D);
		Draw.text(text, centerX - Draw.width(text) / 2.0F, top - Draw.textHeight() - 1.0F, 0xFFFFFFFF);
		Draw.flush();
		GlStateManager.popMatrix();
	}

	private int color(Item item) {
		if (item == Items.IRON_INGOT) return iron ? ironColor.getRGB() : 0;
		if (item == Items.GOLD_INGOT) return gold ? goldColor.getRGB() : 0;
		if (item == Items.DIAMOND) return diamond ? diamondColor.getRGB() : 0;
		if (item == Items.EMERALD) return emerald ? emeraldColor.getRGB() : 0;
		if (item == Items.GOLDEN_APPLE) return goldenApple ? goldenAppleColor.getRGB() : 0;
		if (item == Items.ENDER_PEARL) return enderPearl ? enderPearlColor.getRGB() : 0;
		return highlightAll ? defaultColor.getRGB() : 0;
	}
}
