package org.codeberg.illusionclient.feature.module.visual;

import net.minecraft.block.entity.*;
import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.world.WorldLoadEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.util.render.EspRenderer;

import java.awt.*;
import java.util.List;

@Description("Draws boxes around containers.")
@ObsHidden
@ModuleInfo(name = "Container ESP", category = Category.VISUAL)
public class ContainerEsp extends Module {
	private final EspRenderer<BlockEntity> renderer = new EspRenderer<>();

	@Description("2D draws flat boxes on screen, 3D draws boxes in the world.")
	@Combo(value = "Mode", options = {"2D", "3D"})
	public String mode = "2D";

	@Description("Draws chests.")
	@Check("Chests")
	public boolean chests = true;

	@Description("The color of chest boxes.")
	@Requires("chests")
	@ColorPicker("Chest Color")
	public Color chestColor = new Color(0xFFB74D);

	@Description("Draws trapped chests.")
	@Check("Trapped Chests")
	public boolean trappedChests = true;

	@Description("The color of trapped chest boxes.")
	@Requires("trappedChests")
	@ColorPicker("Trapped Chest Color")
	public Color trappedColor = new Color(0x875100);

	@Description("Draws Droppers.")
	@Check("Droppers")
	public boolean droppers = false;

	@Description("The color of dropper boxes.")
	@Requires("droppers")
	@ColorPicker("Dropper Color")
	public Color dropperColor = new Color(0x343E46);

	@Description("Draws Dispensers.")
	@Check("Dispensers")
	public boolean dispensers = false;

	@Description("The color of dispenser boxes.")
	@Requires("dispensers")
	@ColorPicker("Dispenser Color")
	public Color dispenserColor = new Color(0x343E46);

	@Description("Draws Furnaces.")
	@Check("Droppers")
	public boolean furnaces = false;

	@Description("The color of furnace boxes.")
	@Requires("furnaces")
	@ColorPicker("Furnace Color")
	public Color furnaceColor = new Color(0x343E46);

	@Description("Draws Hoppers.")
	@Check("Droppers")
	public boolean hoppers = false;

	@Description("The color of hopper boxes.")
	@Requires("hoppers")
	@ColorPicker("Hopper Color")
	public Color hopperColor = new Color(0x343E46);

	@Description("Draws ender chests.")
	@Check("Ender Chests")
	public boolean enderChests = true;

	@Description("The color of ender chest boxes.")
	@Requires("enderChests")
	@ColorPicker("Ender Color")
	public Color enderColor = new Color(0xB388FF);

	@Description("Use the Full Width on chests and ender chests")
	@Check("Full Width")
	public boolean fullWidth = false;

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

		renderer.begin();
		boolean flat = mode.equals("2D");
		// updateShape() can create a neighbor chest's block entity and append it to this list, so a for-each would throw
		List<BlockEntity> blockEntities = minecraft.world.blockEntities;
		for (int i = 0; i < blockEntities.size(); i++) {
			BlockEntity blockEntity = blockEntities.get(i);
			if (!shouldRender(blockEntity)) continue;
			Box box = box(blockEntity);
			if (box == null) continue;

			if (flat) renderer.add(blockEntity, box);
			else renderer.box3D(box, color(blockEntity));
		}
		renderer.end3D();
	}

	@Listen
	public void onRender2D(Render2DEvent.Pre event) {
		renderer.render2D(event, (blockEntity, left, top, right, bottom) -> EspRenderer.box(left, top, right, bottom, color(blockEntity)));
	}

	private Box box(BlockEntity blockEntity) {
		BlockPos pos = blockEntity.getPos();
		int width = 1, depth = 1;
		double inset = getInset(blockEntity);
		if (blockEntity instanceof ChestBlockEntity chest) {
			chest.updateShape();
			if (chest.westNeighbor != null || chest.northNeighbor != null) return null;
			if (chest.eastNeighbor != null) width = 2;
			if (chest.southNeighbor != null) depth = 2;
		}

		return new Box(
			pos.getX() + inset, pos.getY(), pos.getZ() + inset,
			pos.getX() + width - inset, pos.getY() + getHeight(blockEntity), pos.getZ() + depth - inset
		);
	}

	private int color(BlockEntity blockEntity) {
		if (blockEntity instanceof ChestBlockEntity chest && !isChestTrapped(chest)) return chestColor.getRGB();
		if (blockEntity instanceof ChestBlockEntity chest && isChestTrapped(chest)) return trappedColor.getRGB();
		if (blockEntity instanceof DispenserBlockEntity) return dispenserColor.getRGB();
		if (blockEntity instanceof DropperBlockEntity) return dropperColor.getRGB();
		if (blockEntity instanceof FurnaceBlockEntity) return furnaceColor.getRGB();
		if (blockEntity instanceof HopperBlockEntity) return hopperColor.getRGB();
		return enderColor.getRGB();
	}

	private boolean shouldRender(BlockEntity blockEntity) {
		if (blockEntity instanceof ChestBlockEntity chest) return isChestTrapped(chest) ? trappedChests : chests;
		if (blockEntity instanceof DispenserBlockEntity) return dispensers;
		if (blockEntity instanceof DropperBlockEntity) return droppers;
		if (blockEntity instanceof FurnaceBlockEntity) return furnaces;
		if (blockEntity instanceof HopperBlockEntity) return hoppers;
		if (blockEntity instanceof EnderChestBlockEntity) return enderChests;
		return false;
	}

	private boolean isChestTrapped(ChestBlockEntity chest) {
		return chest.getChestType() == 1;
	}

	private double getInset(BlockEntity blockEntity) {
		if (!fullWidth && (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity)) return 1.0D / 16.0D;
		return 1.0D;
	}

	private double getHeight(BlockEntity blockEntity) {
		if (!fullWidth && (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity)) return 14.0D / 16.0D;
		return 1.0D;
	}
}
