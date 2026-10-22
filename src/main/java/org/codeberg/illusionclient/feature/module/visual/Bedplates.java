package org.codeberg.illusionclient.feature.module.visual;

import io.github.nevalackin.radbus.Listen;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.Render2DEvent;
import org.codeberg.illusionclient.event.Render3DEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.BlockUtils;
import org.codeberg.illusionclient.util.bed.Bed;
import org.codeberg.illusionclient.util.render.EspRenderer;

import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@Description("Shows the blocks defending each bed.")
@ModuleInfo(name = "Bed Plates", category = Category.VISUAL)
public class Bedplates extends Module {
	private static final ItemStack[] NONE = new ItemStack[0];
	private static final int ICON = 16;
	private static final int GAP = 2;
	private static final int PAD = 2;

	private final Minecraft minecraft = Minecraft.getInstance();
	private final Map<Bed, Plate> plates = new WeakHashMap<>();
	private final List<Plate> visible = new ObjectArrayList<>();
	private int scannedHeight;
	private boolean scannedSkip;

	@Description("How many blocks above the bed to list.")
	@Slider(value = "Height", min = 1.0F, max = 8.0F)
	public float height = 4.0F;

	@Description("Leaves out blocks that aren't full cubes, like slabs, stairs and ladders.")
	@Check("Skip Non-Full Blocks")
	public boolean skipNonFull = false;

	@Description("How round the plate corners are.")
	@Slider(value = "Corner Radius", min = 0.0F, max = 8.0F)
	public float cornerRadius = 3.0F;

	@Description("The plate border when a bed face is exposed.")
	@ColorPicker("Exposed Color")
	public Color exposedColor = new Color(0x80EF7E);

	@Override
	public void onDisable() {
		plates.clear();
		visible.clear();
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		visible.clear();
		Collection<Bed> beds = IllusionClient.BEDS.beds();
		if (beds.isEmpty() || minecraft.world == null) return;

		int limit = Math.round(height);
		if (limit != scannedHeight || skipNonFull != scannedSkip) {
			scannedHeight = limit;
			scannedSkip = skipNonFull;
			plates.clear();
		}

		EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
		float[] clip = Frustum.getInstance().clipMatrix;
		for (Bed bed : beds) {
			Plate plate = plates.computeIfAbsent(bed, key -> new Plate());
			if (plate.revision != bed.revision()) {
				plate.revision = bed.revision();
				plate.exposed = bed.exposed();
				plate.blocks = stacks(bed.cover(), limit);
			}
			if (plate.blocks.length == 0) continue;

			float x = (float) ((bed.x + bed.footX) / 2.0D + 0.5D - dispatcher.cameraX);
			float y = (float) (bed.y + 1.0D - dispatcher.cameraY);
			float z = (float) ((bed.z + bed.footZ) / 2.0D + 0.5D - dispatcher.cameraZ);
			if (EspRenderer.project(clip, x, y, z, minecraft.width / 2.0F, minecraft.height / 2.0F, plate.screen)) visible.add(plate);
		}
	}

	@Listen
	public void onRender2D(Render2DEvent.Pre event) {
		if (visible.isEmpty()) return;

		int scale = event.window().getScale();
		int plateHeight = ICON + PAD * 2;
		for (Plate plate : visible) {
			int width = plate.blocks.length * (ICON + GAP) - GAP + PAD * 2;
			int x = plate.plateX = Math.round(plate.screen[0] / scale) - width / 2;
			int y = plate.plateY = Math.round(plate.screen[1] / scale) - plateHeight;
			int border = plate.exposed ? exposedColor.getRGB() : EspRenderer.OUTLINE;

			Draw.rounded(x, y, width, plateHeight, cornerRadius, Draw.HUD_BACKGROUND);
			Draw.roundedOutline(x, y, width, plateHeight, cornerRadius, 1.0F, border);
		}
		Draw.flush();

		ItemRenderer items = minecraft.getItemRenderer();
		GlStateManager.enableRescaleNormal();
		Lighting.turnOnGui();
		for (Plate plate : visible) {
			int x = plate.plateX + PAD;
			for (ItemStack stack : plate.blocks) {
				items.renderGuiItem(stack, x, plate.plateY + PAD);
				items.renderGuiItemDecoration(minecraft.textRenderer, stack, x, plate.plateY + PAD);
				x += ICON + GAP;
			}
		}
		Lighting.turnOff();
		GlStateManager.disableRescaleNormal();
		GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
		GlStateManager.enableBlend();
	}

	private ItemStack[] stacks(int[] cover, int limit) {
		List<ItemStack> stacks = new ObjectArrayList<>();
		for (int i = 0; i < Math.min(limit, cover.length); i++) {
			int state = cover[i];
			int id = BlockUtils.id(state);
			if (id == Lookup.FLOWING_WATER) id = Lookup.WATER;
			else if (id == Lookup.FLOWING_LAVA) id = Lookup.LAVA;
			if (skipNonFull && !Block.byId(id).isCube()) continue;

			ItemStack stack = icon(id, state);
			if (stack == null) continue;
			if (!stacks.isEmpty() && stacks.getFirst().matchesItem(stack)) stacks.getFirst().size++;
			else stacks.addFirst(stack);
		}
		return stacks.toArray(NONE);
	}

	private static ItemStack icon(int id, int state) {
		if (id == Lookup.WATER) return new ItemStack(Items.WATER_BUCKET);
		if (id == Lookup.LAVA) return new ItemStack(Items.LAVA_BUCKET);

		Item item = Item.byBlock(Block.byId(id));
		if (item == null) return null;
		BlockState blockState = BlockUtils.state(state);
		return new ItemStack(item, 1, blockState == null ? 0 : blockState.getBlock().getDropItemMetadata(blockState));
	}

	private static final class Plate {
		ItemStack[] blocks = NONE;
		boolean exposed;
		int revision;
		final float[] screen = new float[2];
		int plateX, plateY;
	}

	private static final class Lookup {
		static final int FLOWING_WATER = Block.getId(Blocks.FLOWING_WATER);
		static final int WATER = Block.getId(Blocks.WATER);
		static final int FLOWING_LAVA = Block.getId(Blocks.FLOWING_LAVA);
		static final int LAVA = Block.getId(Blocks.LAVA);
	}
}
