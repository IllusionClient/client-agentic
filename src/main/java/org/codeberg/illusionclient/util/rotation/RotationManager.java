package org.codeberg.illusionclient.util.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.api.Listen;
import org.codeberg.illusionclient.event.player.JumpYawEvent;
import org.codeberg.illusionclient.event.player.MoveInputEvent;
import org.codeberg.illusionclient.event.player.RotationSentEvent;
import org.codeberg.illusionclient.event.player.ServerPitchEvent;
import org.codeberg.illusionclient.event.player.ServerYawEvent;
import org.codeberg.illusionclient.event.player.StrafeYawEvent;
import org.codeberg.illusionclient.event.player.UpdateSprintEvent;
import org.codeberg.illusionclient.event.render.ModelRotationEvent;
import org.codeberg.illusionclient.feature.module.movement.Sprint;
import org.codeberg.illusionclient.util.MoveUtils;
import org.codeberg.illusionclient.util.RotationUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;

public final class RotationManager {
	public enum Strafe { SILENT, STRICT, OFF }

	private static final double MAX_DRIFT = 0.5D;
	private static final double DRIFT_TOLERANCE = 0.03D;
	private static final double PROGRESS_WEIGHT = 0.001D;
	private static final double MIN_PROGRESS = 0.4D;

	private final Minecraft minecraft = Minecraft.getInstance();
	private boolean active;
	private float yaw;
	private float pitch;
	private Strafe strafe = Strafe.OFF;
	private float sentYaw;
	private float sentPitch;
	private boolean lastActive;
	private boolean model;
	private final float[] modelRotation = new float[6];
	private final float[] savedRotation = new float[6];
	private boolean steering;
	private boolean keepForward;
	private double driftX;
	private double driftZ;
	private double lastX;
	private double lastZ;

	public RotationManager() {
		IllusionClient.BUS.subscribe(this);
	}

	public void rotate(float yaw, float pitch, Strafe strafe) {
		float gcd = RotationUtils.mouseScale() * 0.15F;
		this.yaw = sentYaw + Math.round(MathHelper.wrapDegrees(yaw - sentYaw) / gcd) * gcd;
		this.pitch = Math.clamp(sentPitch + Math.round((pitch - sentPitch) / gcd) * gcd, -90.0F, 90.0F);
		this.strafe = strafe;
		active = true;
	}

	public boolean active() {
		return active;
	}

	public float sentYaw() {
		return sentYaw;
	}

	public float sentPitch() {
		return sentPitch;
	}

	public float yaw(float yaw) {
		return active ? this.yaw : yaw;
	}

	public float pitch(float pitch) {
		return active ? this.pitch : pitch;
	}

	private float moveYaw(float yaw) {
		return active && strafe != Strafe.OFF ? this.yaw : yaw;
	}

	@Listen
	public void onStrafeYaw(StrafeYawEvent event) {
		event.yaw = moveYaw(event.yaw);
	}

	@Listen(priority = 1)
	public void onJumpYaw(JumpYawEvent event) {
		event.yaw = moveYaw(event.yaw);
	}

	@Listen
	public void onServerYaw(ServerYawEvent event) {
		event.yaw = yaw(event.yaw);
	}

	@Listen
	public void onServerPitch(ServerPitchEvent event) {
		event.pitch = pitch(event.pitch);
	}

	@Listen(priority = -1)
	public void onUpdateSprint(UpdateSprintEvent event) {
		if (event.sprinting && !allowsSprint()) event.sprinting = false;
	}

	private boolean allowsSprint() {
		Sprint sprint = IllusionClient.MODULES.get(Sprint.class);
		boolean enabled = sprint.isEnabled();
		boolean backwards = enabled && sprint.backwards;
		boolean sideways = enabled && sprint.sideways;
		keepForward = false;
		if (!active || strafe != Strafe.SILENT) return true;

		keepForward = !backwards && !sideways;
		LocalClientPlayerEntity player = minecraft.player;
		float forward = Math.signum(player.input.movementForward);
		float strafing = Math.signum(player.input.movementSideways);
		double difference = Math.toRadians(player.yaw - yaw);
		long serverForward = Math.round(forward * Math.cos(difference) + strafing * Math.sin(difference));
		if (serverForward > 0) return true;
		return serverForward < 0 ? backwards : sideways;
	}

	@Listen
	public void onMoveInput(MoveInputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		float magnitude = Math.max(Math.abs(player.forwardSpeed), Math.abs(player.sidewaysSpeed));
		if (!active || strafe != Strafe.SILENT || magnitude == 0.0F) {
			steering = false;
			driftX = 0.0D;
			driftZ = 0.0D;
			return;
		}

		double[] heading = MoveUtils.inputDirection(player.yaw, player.forwardSpeed, player.sidewaysSpeed);
		double length = Math.hypot(heading[0], heading[1]);
		double headingX = heading[0] / length;
		double headingZ = heading[1] / length;
		double drift = steering ? Math.clamp(sideways(driftX + player.x - lastX, driftZ + player.z - lastZ, headingX, headingZ), -MAX_DRIFT, MAX_DRIFT) : 0.0D;
		driftX = drift * headingZ;
		driftZ = -drift * headingX;
		steering = true;
		lastX = player.x;
		lastZ = player.z;

		float bestForward = 0.0F;
		float bestSideways = 0.0F;
		double bestCost = Double.MAX_VALUE;
		for (int forward = -1; forward <= 1; forward++) {
			for (int sideways = -1; sideways <= 1; sideways++) {
				if (keepForward && player.isSprinting() && forward <= 0) continue;

				double[] motion = MoveUtils.inputDirection(yaw, forward, sideways);
				double progress = (motion[0] * headingX + motion[1] * headingZ) / Math.max(1.0D, Math.hypot(forward, sideways));
				if (progress < MIN_PROGRESS) continue;

				SimulatedPlayer simulated = SimulatedPlayer.of(player);
				simulated.yaw = yaw;
				simulated.holdSprint = true;
				simulated.forward = forward * magnitude;
				simulated.sideways = sideways * magnitude;
				simulated.tick();
				double error = Math.abs(drift + sideways(simulated.x() - player.x, simulated.z() - player.z, headingX, headingZ));
				double cost = Math.max(error, DRIFT_TOLERANCE) - progress * PROGRESS_WEIGHT;
				if (cost < bestCost) {
					bestCost = cost;
					bestForward = forward * magnitude;
					bestSideways = sideways * magnitude;
				}
			}
		}
		player.forwardSpeed = bestForward;
		player.sidewaysSpeed = bestSideways;
	}

	private static double sideways(double x, double z, double headingX, double headingZ) {
		return x * headingZ - z * headingX;
	}

	@Listen
	public void onRotationSent(RotationSentEvent event) {
		sentYaw = event.yaw;
		sentPitch = event.pitch;
		LocalClientPlayerEntity player = minecraft.player;
		model = active || lastActive;
		lastActive = active;
		modelRotation[0] = modelRotation[1];
		modelRotation[1] = sentYaw;
		modelRotation[2] = modelRotation[3];
		modelRotation[3] = sentYaw + Math.clamp(MathHelper.wrapDegrees(player.bodyYaw - sentYaw), -75.0F, 75.0F);
		modelRotation[4] = modelRotation[5];
		modelRotation[5] = sentPitch;
		if (active) align(player);
		active = false;
	}

	@Listen
	public void onModelRotation(ModelRotationEvent event) {
		if (event.pre) event.swapped = swapModelRotation(event.entity);
		else setModelRotation(event.entity, savedRotation);
	}

	private boolean swapModelRotation(LivingEntity entity) {
		if (!model || entity != minecraft.player) return false;

		savedRotation[0] = entity.lastHeadYaw;
		savedRotation[1] = entity.headYaw;
		savedRotation[2] = entity.lastBodyYaw;
		savedRotation[3] = entity.bodyYaw;
		savedRotation[4] = entity.lastPitch;
		savedRotation[5] = entity.pitch;
		setModelRotation(entity, modelRotation);
		return true;
	}

	private static void setModelRotation(LivingEntity entity, float[] rotation) {
		entity.lastHeadYaw = rotation[0];
		entity.headYaw = rotation[1];
		entity.lastBodyYaw = rotation[2];
		entity.bodyYaw = rotation[3];
		entity.lastPitch = rotation[4];
		entity.pitch = rotation[5];
	}

	private void align(LocalClientPlayerEntity player) {
		float gcd = RotationUtils.mouseScale() * 0.15F;
		float yawShift = sentYaw + Math.round(MathHelper.wrapDegrees(player.yaw - sentYaw) / gcd) * gcd - player.yaw;
		float pitch = Math.clamp(sentPitch + Math.round((player.pitch - sentPitch) / gcd) * gcd, -90.0F, 90.0F);
		player.yaw += yawShift;
		player.lastYaw += yawShift;
		player.easedYaw += yawShift;
		player.lastEasedYaw += yawShift;
		player.lastPitch += pitch - player.pitch;
		player.pitch = pitch;
	}
}
