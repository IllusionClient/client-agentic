package org.codeberg.illusionclient.feature.module.visual;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.module.client.Tags;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.HiddenLayer;
import org.codeberg.illusionclient.util.render.EspRenderer;
import org.lwjgl.opengl.GL11;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Description("Replaces player name tags with ones that show more info.")
@ObsHidden
@ModuleInfo(name = "Name Tags", category = Category.VISUAL)
public class Nametags extends Module {
	private static final int BACKGROUND = 0x40000000;
	private static final int ICON = 16;

	private final Minecraft minecraft = Minecraft.getInstance();
	private final List<Tag> tags = new ObjectArrayList<>();
	private final List<ItemStack> equipment = new ObjectArrayList<>();

	@Description("Players farther than this get no name tag.")
	@Slider(value = "Range", min = 4.0F, max = 256.0F, step = 1.0F)
	public float range = 64.0F;

	@Description("The size of the name tags.")
	@Slider(value = "Scale", min = 0.5F, max = 3.0F)
	public float scale = 1.0F;

	@Description("Shows your own name tag in third person.")
	@Check("Show Own")
	public boolean self = false;

	@Description("Keeps name tags the same size no matter how far away the player is.")
	@Check("Distance Scaling")
	public boolean distanceScaling = true;

	@Description("Draws a background behind the text, like vanilla name tags.")
	@Check("Background")
	public boolean background = true;

	@Description("Draws the text with a shadow.")
	@Check("Text Shadow")
	public boolean textShadow = true;

	@Section("Extra Info")
	@Description("Shows the player's health before their name.")
	@Check("Health")
	public boolean health = true;

	@Description("HP counts 0 to 20, Hearts counts 0.0 to 10.0.")
	@Requires("health")
	@Combo(value = "Health Format", options = {"HP", "Hearts"})
	public String healthFormat = "HP";

	@Description("Shows how far away the player is after their name.")
	@Check("Distance")
	public boolean distance = false;

	@Description("Shows the tags you gave the player after their name.")
	@Check("Tags")
	public boolean showTags = true;

	@Description("Shows the player's armor and held item above the name tag.")
	@Check("Equipment")
	public boolean showEquipment = true;

	@Description("Shows the enchantments on each item. Some servers spoof these, so they can be missing or wrong.")
	@Requires("showEquipment")
	@Check("Enchantments")
	public boolean enchantments = true;

	@Override
	public void onDisable() {
		tags.clear();
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		tags.clear();
		if (minecraft.world == null || minecraft.player == null) return;

		EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
		float[] clip = Frustum.getInstance().clipMatrix;
		float tickDelta = event.partialTicks();
		for (PlayerEntity player : minecraft.world.players) {
			if (!valid(player)) continue;

			float x = (float) (player.prevX + (player.x - player.prevX) * tickDelta - dispatcher.cameraX);
			float y = (float) (player.prevY + (player.y - player.prevY) * tickDelta + player.height + 0.5D - dispatcher.cameraY);
			float z = (float) (player.prevZ + (player.z - player.prevZ) * tickDelta - dispatcher.cameraZ);
			Tag tag = new Tag(player, minecraft.player.distanceTo(player));
			if (EspRenderer.project(clip, x, y, z, minecraft.width / 2.0F, minecraft.height / 2.0F, tag.screen)) tags.add(tag);
		}
		tags.sort(Comparator.comparingDouble((Tag tag) -> tag.distance).reversed());
	}

	@Listen
	public void onRender2D(Render2DEvent.Pre event) {
		if (tags.isEmpty()) return;

		HiddenLayer.begin();
		int guiScale = event.window().getScale();
		for (Tag tag : tags) {
			float size = distanceScaling ? scale : scale * Math.clamp(6.0F / tag.distance, 0.25F, 1.0F);
			GlStateManager.pushMatrix();
			GlStateManager.translatef(tag.screen[0] / guiScale, tag.screen[1] / guiScale, 0.0F);
			GlStateManager.scalef(size, size, 1.0F);
			render(tag);
			GlStateManager.popMatrix();
		}
		GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
		GlStateManager.enableBlend();
		HiddenLayer.end();
	}

	private void render(Tag tag) {
		PlayerEntity player = tag.player;
		TextRenderer font = minecraft.textRenderer;
		String text = label(player, tag.distance);
		int half = font.getWidth(text) / 2;

		if (background) {
			Draw.fill(-half - 2, -10, half + 2, 0, BACKGROUND);
			Draw.flush();
		}
		GlStateManager.enableTexture();
		GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
		font.draw(text, -half, -9, 0xFFFFFFFF, textShadow);

		if (!showEquipment) return;
		equipment.clear();
		for (int slot = 3; slot >= 0; slot--) {
			if (player.getArmor(slot) != null) equipment.add(player.getArmor(slot));
		}
		if (player.getItemInHand() != null) equipment.add(player.getItemInHand());
		if (equipment.isEmpty()) return;

		int left = -equipment.size() * ICON / 2;
		int top = -12 - ICON;
		ItemRenderer items = minecraft.getItemRenderer();
		GlStateManager.enableRescaleNormal();
		Lighting.turnOnGui();
		for (int i = 0; i < equipment.size(); i++) {
			ItemStack stack = equipment.get(i);
			items.renderGuiItem(stack, left + i * ICON, top);
			items.renderGuiItemDecoration(font, stack, left + i * ICON, top);
		}
		Lighting.turnOff();
		GlStateManager.disableRescaleNormal();

		if (!enchantments) return;
		GlStateManager.disableDepthTest();
		GlStateManager.pushMatrix();
		GlStateManager.scalef(0.5F, 0.5F, 1.0F);
		for (int i = 0; i < equipment.size(); i++) {
			NbtList list = equipment.get(i).getEnchantments();
			if (list == null) continue;

			int y = top * 2;
			for (int j = 0; j < list.size(); j++) {
				NbtCompound entry = list.getCompound(j);
				Enchantment enchantment = Enchantment.byId(entry.getShort("id"));
				if (enchantment == null) continue;

				y -= 9;
				font.draw(abbreviate(enchantment) + entry.getShort("lvl"), (left + i * ICON) * 2, y, 0xFFFFFFFF, textShadow);
			}
		}
		GlStateManager.popMatrix();
		GlStateManager.enableDepthTest();
	}

	private String label(PlayerEntity player, float distance) {
		StringBuilder text = new StringBuilder();
		if (health) {
			float hp = player.getHealth();
			float fraction = hp / player.getMaxHealth();
			text.append(fraction > 0.66F ? "§a" : fraction > 0.33F ? "§e" : "§c")
				.append(healthFormat.equals("Hearts") ? String.format("%.1f", hp / 2.0F) : String.valueOf(Math.round(hp)))
				.append("§r ");
		}
		text.append(player.getDisplayName().getFormattedString());
		Set<String> tags = Tags.of(player);
		if (showTags && !tags.isEmpty()) text.append(" §7[").append(String.join(", ", tags)).append(']');
		if (this.distance) text.append(" §7").append(Math.round(distance)).append('m');
		return text.toString();
	}

	private static String abbreviate(Enchantment enchantment) {
		String[] words = I18n.translate(enchantment.getTranslationKey()).split(" ");
		if (words.length == 1) return words[0].substring(0, Math.min(2, words[0].length()));

		StringBuilder initials = new StringBuilder();
		for (String word : words) {
			if (!word.isEmpty()) initials.append(word.charAt(0));
		}
		return initials.toString();
	}

	private boolean valid(PlayerEntity player) {
		if (!player.isAlive() || player.isInvisibleTo(minecraft.player)) return false;
		if (player == minecraft.player) return self && minecraft.options.perspective != 0;
		return minecraft.player.distanceTo(player) <= range;
	}

	private static final class Tag {
		final PlayerEntity player;
		final float distance;
		final float[] screen = new float[2];

		Tag(PlayerEntity player, float distance) {
			this.player = player;
			this.distance = distance;
		}
	}
}
