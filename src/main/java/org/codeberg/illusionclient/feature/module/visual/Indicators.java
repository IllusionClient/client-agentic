package org.codeberg.illusionclient.feature.module.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.render.Render2DEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.util.HiddenLayer;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@Description("Points at incoming projectiles around your crosshair.")
@ObsHidden
@ModuleInfo(name = "Indicators", category = Category.VISUAL)
public class Indicators extends Module {
	private static final ItemStack ARROW = new ItemStack(Items.ARROW);
	private static final ItemStack PEARL = new ItemStack(Items.ENDER_PEARL);
	private static final ItemStack FIREBALL = new ItemStack(Items.FIRE_CHARGE);
	private static final int ICON = 16;

	private final Minecraft minecraft = Minecraft.getInstance();

	@Section("Warn For")
	@Description("Warns for arrows in flight.")
	@Check("Arrows")
	public boolean arrows = true;

	@Description("Warns for thrown ender pearls.")
	@Check("Ender Pearls")
	public boolean pearls = true;

	@Description("Warns for fireballs.")
	@Check("Fireballs")
	public boolean fireballs = true;

	@Section("Conditions")
	@Description("Only warns for projectiles moving toward you.")
	@Check("Coming Closer")
	public boolean comingCloser = true;

	@Description("Projectiles farther than this are ignored.")
	@Slider(value = "Range", min = 8.0F, max = 128.0F, step = 1.0F)
	public float range = 48.0F;

	@Section("Display")
	@Description("How far from the crosshair the indicators sit.")
	@Slider(value = "Radius", min = 20.0F, max = 150.0F, step = 1.0F)
	public float radius = 50.0F;

	@Description("Shows how far away each projectile is.")
	@Check("Distance")
	public boolean distance = true;

	@Description("The color of the arrows.")
	@ColorPicker("Color")
	public Color color = new Color(0xF44336);

	@Listen
	public void onRender2D(Render2DEvent.Pre event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.world == null) return;

		float centerX = event.window().getWidth() / 2.0F;
		float centerY = event.window().getHeight() / 2.0F;
		boolean drawn = false;
		ItemRenderer items = minecraft.getItemRenderer();
		for (Entity entity : minecraft.world.entities) {
			ItemStack icon = icon(entity);
			if (icon == null || entity.x == entity.prevX && entity.y == entity.prevY && entity.z == entity.prevZ) continue;

			double dx = entity.x - player.x;
			double dy = entity.y - player.y;
			double dz = entity.z - player.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > range) continue;
			if (comingCloser && dx * (entity.x - entity.prevX) + dy * (entity.y - entity.prevY) + dz * (entity.z - entity.prevZ) >= 0.0D) continue;

			if (!drawn) {
				HiddenLayer.begin();
				drawn = true;
			}
			double angle = Math.atan2(dz, dx) - Math.toRadians(player.yaw + 90.0F);
			float sin = (float) Math.sin(angle);
			float cos = (float) -Math.cos(angle);
			float baseX = centerX + sin * radius;
			float baseY = centerY + cos * radius;
			Draw.triangle(baseX + sin * 8.0F, baseY + cos * 8.0F, baseX - cos * 5.0F, baseY + sin * 5.0F, baseX + cos * 5.0F, baseY - sin * 5.0F, color.getRGB());
			Draw.flush();

			int iconX = Math.round(baseX - sin * (ICON / 2.0F + 4.0F) - ICON / 2.0F);
			int iconY = Math.round(baseY - cos * (ICON / 2.0F + 4.0F) - ICON / 2.0F);
			GlStateManager.enableRescaleNormal();
			Lighting.turnOnGui();
			items.renderGuiItem(icon, iconX, iconY);
			Lighting.turnOff();
			GlStateManager.disableRescaleNormal();
			if (distance) {
				String text = Math.round(dist) + "m";
				minecraft.textRenderer.draw(text, iconX + (ICON - minecraft.textRenderer.getWidth(text)) / 2, iconY + ICON, 0xFFFFFFFF, true);
			}
		}
		if (!drawn) return;

		GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
		GlStateManager.enableBlend();
		HiddenLayer.end();
	}

	private ItemStack icon(Entity entity) {
		if (arrows && entity instanceof ArrowEntity) return ARROW;
		if (pearls && entity instanceof EnderPearlEntity) return PEARL;
		if (fireballs && (entity instanceof FireballEntity || entity instanceof SmallFireballEntity)) return FIREBALL;
		return null;
	}
}
