package org.codeberg.illusionclient.feature.module.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.input.InputState;

@ModuleInfo(name = "Instant Stop", category = Category.MOVEMENT)
@Description("Stops you as soon as you let go of your movement keys by tapping the opposite keys.")
public class InstantStop extends Module {
	private static final float INPUT_SCALE = 0.98F;
	private static final float SNEAK_SLOWDOWN = 0.3F;
	private static final float ITEM_USE_SLOWDOWN = 0.2F;
	private static final float GROUND_ACCELERATION = 0.16277136F;
	private static final float GROUND_FRICTION = 0.91F;
	private final Minecraft minecraft = Minecraft.getInstance();

	@Description("Allows tapping the opposite of a key you just let go of. Turn off if it feels like your input is being reversed.")
	@Check("Reverse Input")
	public boolean reverse = true;

	private float lastForward;
	private float lastSideways;
	private float releasedForward;
	private float releasedSideways;
	private boolean stopping;

	@Override
	public void onDisable() {
		lastForward = lastSideways = releasedForward = releasedSideways = 0.0F;
		stopping = false;
	}

	@Listen
	public void onInput(InputEvent event) {
		InputState input = IllusionClient.INPUT.player;
		float forward = input.forwardAxis();
		float sideways = input.sidewaysAxis();
		if (forward != 0.0F || sideways != 0.0F) {
			stopping = false;
		} else if (lastForward != 0.0F || lastSideways != 0.0F) {
			stopping = true;
			releasedForward = lastForward;
			releasedSideways = lastSideways;
		}
		lastForward = forward;
		lastSideways = sideways;

		LocalClientPlayerEntity player = minecraft.player;
		InputState client = IllusionClient.INPUT.client;
		if (!stopping || player == null || minecraft.screen != null || client.forwardAxis() != 0.0F || client.sidewaysAxis() != 0.0F) return;

		double radians = Math.toRadians(player.yaw);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		float acceleration = acceleration(player, client);
		float counterForward = counter(player.velocityZ * cos - player.velocityX * sin, acceleration, releasedForward);
		float counterSideways = counter(player.velocityX * cos + player.velocityZ * sin, acceleration, releasedSideways);
		if (counterForward == 0.0F && counterSideways == 0.0F) {
			stopping = false;
			return;
		}

		client.forward = counterForward > 0.0F;
		client.back = counterForward < 0.0F;
		client.left = counterSideways > 0.0F;
		client.right = counterSideways < 0.0F;
	}

	private float counter(double velocity, float acceleration, float released) {
		if (Math.abs(velocity) * 2.0D <= acceleration) return 0.0F;

		float key = velocity > 0.0D ? -1.0F : 1.0F;
		return !reverse && key == -released ? 0.0F : key;
	}

	private float acceleration(LocalClientPlayerEntity player, InputState client) {
		float scale = INPUT_SCALE;
		if (client.sneak) scale *= SNEAK_SLOWDOWN;
		if (player.hasItemInUse()) scale *= ITEM_USE_SLOWDOWN;
		if (!player.onGround) return player.speedInAir * scale;

		float friction = minecraft.world.getBlockState(new BlockPos(player).down()).getBlock().slipperiness * GROUND_FRICTION;
		return player.getSpeed() * GROUND_ACCELERATION / (friction * friction * friction) * scale;
	}
}
