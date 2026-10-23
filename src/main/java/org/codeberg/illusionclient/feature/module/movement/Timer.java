package org.codeberg.illusionclient.feature.module.movement;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.TimerEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

import java.util.Locale;

@ModuleInfo(name = "Timer", category = Category.MOVEMENT)
@Description("Speeds up or slows down the whole game. Most servers catch even a 1% change.")
public class Timer extends Module {
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Game speed, 1.00x is vanilla.")
	@Slider(value = "Speed", min = 0.01F, max = 2.0F, step = 0.01F)
	public float speed = 1.0F;

	@Description("Only changes speed while the attack key is held.")
	@Check("Mouse Pressed")
	public boolean mousePressed = false;

	@Description("Only changes speed while you're in hurt time.")
	@Check("Damage")
	public boolean damage = false;

	@Description("Only changes speed while you're airborne and moving down.")
	@Check("Falling")
	public boolean falling = false;

	@Override
	public String details() {
		return String.format(Locale.ROOT, "%.2fx", speed);
	}

	@Listen
	public void onTimer(TimerEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (minecraft.world != null && player != null && active(player)) event.speed = speed;
	}

	private boolean active(LocalClientPlayerEntity player) {
		if (mousePressed && !IllusionClient.INPUT.held(minecraft.options.attackKey)) return false;
		if (falling && (player.onGround || player.velocityY >= 0.0D)) return false;
		return !damage || minecraft.focused && player.damagedTimer != 0;
	}
}
