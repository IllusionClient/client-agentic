package org.codeberg.illusionclient.feature.module.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.input.InputState;
import org.codeberg.illusionclient.util.rotation.Rotator;

public abstract class RotatingModule extends Module {
	private static final float AIMED = 2.0F;
	private static final float RETURNING = 3.0F;
	private static final int MAX_AIM_TICKS = 40;
	private static final int SERVER_SLACK_TICKS = 2;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Rotator rotator = new Rotator();

	@Section("Rotation")
	@Description("Silent only turns what the server sees, Camera turns your view.")
	@Combo(value = "Rotations", options = {"Silent", "Camera"})
	public String rotations = "Silent";

	@Description("Regular eases in, Linear is steady, Lock-on snaps.")
	@Combo(value = "Turn Mode", options = {"Regular", "Linear", "Lock-on"})
	public String turnMode = "Lock-on";

	@Description("Horizontal turn speed.")
	@Requires(value = "turnMode", is = {"Regular", "Linear"})
	@Slider(value = "Horizontal Speed", min = 1.0F, max = 100.0F)
	public float horizontalSpeed = 60.0F;

	@Description("Vertical turn speed.")
	@Requires(value = "turnMode", is = {"Regular", "Linear"})
	@Slider(value = "Vertical Speed", min = 1.0F, max = 100.0F)
	public float verticalSpeed = 40.0F;

	@Description("How your movement follows the rotation. Silent keeps you moving where you look, Strict moves you along the rotation like vanilla, Off ignores it.")
	@Requires(value = "rotations", is = "Silent")
	@Combo(value = "Rotation Strafe", options = {"Silent", "Strict", "Off"})
	public String strafe = "Silent";

	private float[] goal;
	private boolean rotating;
	private float originYaw;
	private float originPitch;
	private int aimTicks;
	private long lastFrame;

	protected final void aim(float[] rotation) {
		if (goal == null) aimTicks = 0;
		if (!rotating) {
			originYaw = minecraft.player.yaw;
			originPitch = minecraft.player.pitch;
			rotating = true;
		}
		goal = rotation;
	}

	protected final void release() {
		goal = null;
	}

	protected final void stopRotating() {
		goal = null;
		rotating = false;
	}

	protected final void snapBack() {
		LocalClientPlayerEntity player = minecraft.player;
		if (rotating && !silent()) {
			float yawDiff = MathHelper.wrapDegrees(originYaw - player.yaw);
			float pitchDiff = originPitch - player.pitch;
			rotator.clearRemainder();
			rotator.turnCamera(player, yawDiff, pitchDiff, yawDiff, pitchDiff);
		}
		stopRotating();
	}

	protected final boolean rotating() {
		return rotating;
	}

	protected final boolean aimed() {
		return goal != null && Math.hypot(MathHelper.wrapDegrees(goal[0] - aimYaw()), goal[1] - aimPitch()) <= AIMED;
	}

	protected final boolean aimTimedOut() {
		return aimTicks > MAX_AIM_TICKS;
	}

	protected final int serverTicks() {
		return Math.max(0, Math.round(IllusionClient.STATS.ping() / MS_PER_TICK)) + SERVER_SLACK_TICKS;
	}

	protected final float aimYaw() {
		return silent() ? IllusionClient.ROTATION.sentYaw() : minecraft.player.yaw;
	}

	protected final float aimPitch() {
		return silent() ? IllusionClient.ROTATION.sentPitch() : minecraft.player.pitch;
	}

	protected final float originYaw() {
		return rotating && !silent() ? originYaw : minecraft.player.yaw;
	}

	protected final float originPitch() {
		return rotating && !silent() ? originPitch : minecraft.player.pitch;
	}

	protected final HitResult aimedHit() {
		LocalClientPlayerEntity player = minecraft.player;
		Vec3d eye = player.getEyePosition(1.0F);
		Vec3d look = RotationUtils.look(aimYaw(), aimPitch());
		double reach = minecraft.interactionManager.getReach();
		return minecraft.world.rayTrace(eye, eye.add(look.x * reach, look.y * reach, look.z * reach));
	}

	protected final void useHeldItem(LocalClientPlayerEntity player) {
		float yaw = player.yaw;
		float pitch = player.pitch;
		player.yaw = aimYaw();
		player.pitch = aimPitch();
		minecraft.interactionManager.useItem(player, minecraft.world, player.inventory.getSelectedItem());
		player.yaw = yaw;
		player.pitch = pitch;
	}

	private boolean silent() {
		return rotations.equals("Silent");
	}

	@Listen(priority = -1)
	public void turnOnTick(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || !rotating) return;

		aimTicks = aimed() ? 0 : aimTicks + 1;
		if (!silent()) keepMoving(player);
		else if (goal != null || !IllusionClient.ROTATION.active()) rotating = rotator.turnSilently(goal, horizontalSpeed, verticalSpeed, turnMode, strafe);
	}

	private void keepMoving(LocalClientPlayerEntity player) {
		InputState input = IllusionClient.INPUT.client;
		float forward = input.forwardAxis();
		float sideways = input.sidewaysAxis();
		if (forward == 0.0F && sideways == 0.0F) return;

		double radians = Math.toRadians(originYaw - player.yaw);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		long newForward = Math.round(forward * cos + sideways * sin);
		long newSideways = Math.round(sideways * cos - forward * sin);
		input.forward = newForward > 0;
		input.back = newForward < 0;
		input.left = newSideways > 0;
		input.right = newSideways < 0;
	}

	@Listen
	public void turnOnFrame(Render3DEvent event) {
		long now = System.nanoTime();
		float delta = Math.min(0.1F, (now - lastFrame) / 1.0E9F);
		lastFrame = now;
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || !rotating || silent()) return;

		float gcd = RotationUtils.mouseScale() * 0.15F;
		int mouseY = minecraft.options.invertMouseY ? -minecraft.mouse.y : minecraft.mouse.y;
		originYaw += minecraft.mouse.x * gcd;
		originPitch = Math.clamp(originPitch - mouseY * gcd, -90.0F, 90.0F);

		float yawDiff = MathHelper.wrapDegrees((goal == null ? originYaw : goal[0]) - player.yaw);
		float pitchDiff = (goal == null ? originPitch : goal[1]) - player.pitch;
		double distance = Math.hypot(yawDiff, pitchDiff);
		if (goal == null && distance < gcd) {
			rotating = false;
			return;
		}

		String mode = goal == null && distance < RETURNING ? "Linear" : turnMode;
		float[] steps = rotator.steps(yawDiff, pitchDiff, horizontalSpeed, verticalSpeed, delta, mode);
		rotator.turnCamera(player, steps[0], steps[1], yawDiff, pitchDiff);
	}
}
