package org.codeberg.illusionclient.feature.module.player;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.input.InputEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.player.UseItemEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Section;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.ItemUtils;
import org.codeberg.illusionclient.util.MoveUtils;
import org.codeberg.illusionclient.util.NoiseUtils;
import org.codeberg.illusionclient.util.SimulatedPlayer;
import org.codeberg.illusionclient.util.input.InputState;

import java.util.Random;

@ModuleInfo(name = "Bridge Assist", category = Category.PLAYER)
@Description("Sneaks for you at block edges.")
public class BridgeAssist extends Module {
	private static final float LOOKING_DOWN_PITCH = 65.0F;
	private static final int HOLD_GRACE_TICKS = 2;
	private static final int PLACEMENT_TICKS = 20;
	private static final int LANDING_GRACE_TICKS = 2;
	private static final double EDGE_TOLERANCE = 0.1D;
	private static final double LATCH_RADIUS = 0.15D;
	private static final float MIN_AXIS_SHARE = 0.38F;
	private static final float OFFSET_STEP = 0.01F;
	private static final float RANDOM_OFFSET_SHIFT = 0.075F;
	private static final int MIN_SAMPLES = 10;
	private static final int MAX_RECORDED_TICKS = 20;
	private static final int MIN_JUMP_SAMPLES = 3;
	private static final float MAX_JUMP_HEIGHT = 1.25F;
	private static final float BRIDGE_ANGLE = 45.0F;
	private static final float BRIDGE_ANGLE_TOLERANCE = 7.5F;
	private static final float MIN_TURN = 30.0F;
	private static final int STILL_TICKS = 3;
	private static final float DIAGONAL_TOLERANCE = 10.0F;
	private static final double ALIGN_START = 0.2D;
	private static final double ALIGN_STOP = 0.1D;
	private static final float INPUT_SCALE = 0.98F;
	private static final float SNEAK_SCALE = 0.3F;
	private static final double AIR_SPEED = 0.02D;
	private static final double AIR_DRAG = 0.91D;
	private static final double GROUND_DRAG = 0.6D * AIR_DRAG;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();

	@Section("General")
	@Description("How far past the edge before sneaking.")
	@Slider(value = "Edge Offset", min = 0.0F, max = 0.3F, step = 0.01F)
	public float edgeOffset = 0.24F;

	@Description("Sneak time after you're back on the block.")
	@Slider(value = "Unsneak Delay", min = 0.0F, max = 300.0F)
	public float unsneakDelay = 85.0F;

	@Description("Varies the edge offset and unsneak delay.")
	@Check("Randomize")
	public boolean randomize = true;

	@Description("Watches you bridge without helping, then sets the options to match and saves them as a new profile.")
	@Check("Record")
	public boolean record = false;

	@Section("Misc. Features")
	@Description("Swaps to hotbar blocks.")
	@Combo(value = "Select Blocks", options = {"No", "On depletion", "Always"})
	public String selectBlocks = "On depletion";

	@Description("Sneaks as you turn to bridge near an edge, until edge sneaking takes over.")
	@Check("Pre-Sneak")
	public boolean preSneak = true;

	@Description("How close to an open edge you need to be for pre-sneak.")
	@Requires("preSneak")
	@Slider(value = "Pre-Sneak Distance", min = 0.0F, max = 0.5F)
	public float preSneakDistance = 0.3F;

	@Description("Strafes you onto the block diagonal when bridging diagonally backwards.")
	@Check("Strafe Align")
	public boolean strafeAlign = true;

	@Description("Blocks placements that would leave you at the edge and need a second sneak.")
	@Check("Avoid Double-Sneaking")
	public boolean avoidDoubleSneaking = true;

	@Description("Blocks placements until you've backed up to the edge on every axis you're moving along.")
	@Check("Wait For Edge")
	public boolean waitForEdge = false;

	@Description("Lets you place the next block without sneaking first.")
	@Check("Skip Sneak")
	public boolean skipSneak = false;

	@Section("Sneak On Jump")
	@Description("Also sneaks at edges while rising from a jump, and on the way down below the jump sneak height.")
	@Check("Sneak On Jump")
	public boolean sneakOnJump = true;

	@Description("How high above your takeoff point to start sneaking on the way down.")
	@Requires("sneakOnJump")
	@Slider(value = "Jump Sneak Height", min = 0.0F, max = MAX_JUMP_HEIGHT, step = 0.01F)
	public float jumpSneakHeight = 0.0F;

	@Description("Only sneaks in the air while you hold a movement key.")
	@Requires("sneakOnJump")
	@Check("Only While Moving")
	public boolean onlyWhileMoving = true;

	@Section("Conditions")
	@Description("Only works while you hold sneak, and lets go of it for you away from edges.")
	@Check("Sneak Key Pressed")
	public boolean sneakKeyPressed = false;

	@Description("Only works while you hold blocks.")
	@Check("Holding Blocks")
	public boolean holdingBlocks = true;

	@Description("Only works while you look down.")
	@Check("Looking Down")
	public boolean lookingDown = true;

	@Description("Only works when not pressing forward.")
	@Check("Not Moving Forward")
	public boolean notMovingForward = true;

	private boolean sneaking;
	private boolean armed;
	private boolean sneakedSincePlace;
	private boolean pendingSneak;
	private boolean holdSneak;
	private int delay;
	private int unsneakTicks;
	private int placementTicks;
	private int nonBlockTicks;
	private int groundTicks;
	private int latchedPlacements;
	private double latchX;
	private double latchZ;
	private Block lastBlock;
	private int lastSlot = -1;
	private FloatArrayList recordedOffsets;
	private FloatArrayList recordedDelays;
	private FloatArrayList recordedHeights;
	private double groundY;
	private boolean tracking;
	private float lastYaw;
	private float lastPitch;
	private float turned;
	private int stillTicks;
	private boolean preSneaking;
	private boolean bridged;
	private int alignDirection;
	private boolean wasSneaking;
	private float sampleOffset = -1.0F;
	private int backTicks;

	@Override
	public String details() {
		if (recordedOffsets != null) return recordedDelays.size() + " sneaks";
		return number(unsneakDelay) + "ms";
	}

	@Override
	public void onDisable() {
		syncRecording();
		sneaking = armed = sneakedSincePlace = pendingSneak = holdSneak = tracking = preSneaking = bridged = false;
		unsneakTicks = placementTicks = nonBlockTicks = groundTicks = latchedPlacements = stillTicks = alignDirection = 0;
		turned = 0.0F;
		lastBlock = null;
		lastSlot = -1;
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		InputState input = IllusionClient.INPUT.player;
		syncRecording();
		if (player.onGround) groundY = player.y;
		if (recordedOffsets != null) {
			recordSneak(player, input);
			return;
		}
		nonBlockTicks = ItemUtils.isBlock(player.inventory.getSelectedItem()) ? 0 : nonBlockTicks + 1;
		if (!armed) latchedPlacements = 0;
		armed = false;
		trackRotation(player);
		if (!conditionsMet(player, input)) {
			sneaking = pendingSneak = holdSneak = preSneaking = bridged = false;
			unsneakTicks = alignDirection = 0;
			return;
		}
		if (strafeAlign) align(player, input);
		if (placementTicks == 0) holdSneak = false;
		if (pendingSneak) {
			pendingSneak = false;
			if (!sneaking) {
				sneaking = true;
				delay = nextDelay();
				unsneakTicks = delay;
			}
		}
		if (player.onGround && edgeAhead(player, player.width / 2.0F)) {
			sneaking = armed = sneakedSincePlace = true;
			delay = nextDelay();
			unsneakTicks = delay;
			IllusionClient.INPUT.client.sneak = true;
			return;
		}

		boolean edge = edgeAhead(player, edgeOffset());
		if (preSneak) updatePreSneak(player, input, edge);
		edge |= preSneaking;
		if (!sneaking) {
			sneaking = edge && (sneakKeyPressed || player.onGround || jumpSneak(player, input));
			if (sneaking) {
				sneakedSincePlace = true;
				delay = nextDelay();
				unsneakTicks = delay;
			}
		} else if (edge || holdSneak) {
			unsneakTicks = delay;
		} else if (unsneakTicks-- <= 0) {
			sneaking = false;
		}

		armed = sneaking;
		InputState client = IllusionClient.INPUT.client;
		if (!sneakKeyPressed) {
			if (sneaking) client.sneak = true;
		} else if (!sneaking && input.forwardAxis() < 0.0F && placementTicks > 0) {
			client.sneak = false;
		}
	}

	@Listen
	public void onUseItem(UseItemEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		HitResult target = minecraft.crosshairTarget;
		if (target == null || target.type != HitResult.Type.BLOCK || !ItemUtils.isBlock(player.inventory.getSelectedItem())) return;
		if (waitForEdge && armed && player.onGround && groundTicks >= LANDING_GRACE_TICKS && !IllusionClient.INPUT.player.jump) {
			if (latchedPlacements > 0 && Math.abs(player.x - latchX) < LATCH_RADIUS && Math.abs(player.z - latchZ) < LATCH_RADIUS) {
				latchedPlacements--;
			} else {
				int axes = edgeAxes(player);
				if (axes == 0) {
					event.cancelled = true;
					return;
				}
				latchedPlacements = axes - 1;
				latchX = player.x;
				latchZ = player.z;
			}
		}
		if (avoidDoubleSneaking && armed && player.onGround) {
			int reach = edgeReach(player, target);
			if (reach < 0) {
				event.cancelled = true;
				return;
			}
			holdSneak = reach > 0;
		}
		placementTicks = PLACEMENT_TICKS;
		if (event.click && !skipSneak && !sneakedSincePlace && player.onGround && edgeReach(player, target) >= 0) pendingSneak = true;
		sneakedSincePlace = false;
		bridged = true;
		preSneaking = false;
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (placementTicks > 0) placementTicks--;
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null) return;
		groundTicks = player.onGround ? groundTicks + 1 : 0;
		if (minecraft.screen != null) return;

		PlayerInventory inventory = player.inventory;
		int slot = -1;
		if (selectBlocks.equals("Always")) {
			slot = ItemUtils.bestBlockSlot(inventory, null);
		} else if (selectBlocks.equals("On depletion") && lastBlock != null
			&& inventory.selectedSlot == lastSlot && inventory.getSelectedItem() == null) {
			slot = ItemUtils.bestBlockSlot(inventory, lastBlock);
		}
		if (slot != -1 && slot != inventory.selectedSlot && IllusionClient.MODULES.claimSlot(this)) {
			inventory.selectedSlot = slot;
			IllusionClient.MODULES.releaseSlot(this);
		}

		lastBlock = ItemUtils.block(inventory.getSelectedItem());
		lastSlot = inventory.selectedSlot;
	}

	private void syncRecording() {
		boolean recording = isEnabled() && record;
		if (recording == (recordedOffsets != null)) return;

		if (recording) {
			recordedOffsets = new FloatArrayList();
			recordedDelays = new FloatArrayList();
			recordedHeights = new FloatArrayList();
			sampleOffset = -1.0F;
			return;
		}
		if (recordedDelays.size() >= MIN_SAMPLES) {
			float offset = mean(recordedOffsets);
			float delay = mean(recordedDelays);
			randomize = deviation(recordedDelays, delay) >= 0.5F || deviation(recordedOffsets, offset) >= RANDOM_OFFSET_SHIFT / 2.0F;
			if (randomize) offset += RANDOM_OFFSET_SHIFT;
			edgeOffset = Math.round(Math.clamp(offset, 0.0F, 0.3F) / OFFSET_STEP) * OFFSET_STEP;
			unsneakDelay = Math.round(Math.clamp(delay * MS_PER_TICK, 0.0F, 300.0F));
			if (recordedHeights.size() >= MIN_JUMP_SAMPLES) {
				sneakOnJump = true;
				jumpSneakHeight = Math.round(Math.clamp(mean(recordedHeights), 0.0F, MAX_JUMP_HEIGHT) / OFFSET_STEP) * OFFSET_STEP;
			}
			record = false;
			IllusionClient.CONFIG.createProfile("Bridging");
		}
		recordedOffsets = null;
		recordedDelays = null;
		recordedHeights = null;
		detailsChanged();
	}

	private void recordSneak(LocalClientPlayerEntity player, InputState input) {
		boolean pressed = input.sneak && !wasSneaking;
		boolean released = !input.sneak && wasSneaking;
		wasSneaking = input.sneak;

		if (sampleOffset >= 0.0F) {
			backTicks = edgeAhead(player, sampleOffset) ? 0 : backTicks + 1;
			if (backTicks > MAX_RECORDED_TICKS) {
				sampleOffset = -1.0F;
			} else if (released) {
				recordedOffsets.add(sampleOffset);
				recordedDelays.add(backTicks);
				sampleOffset = -1.0F;
				detailsChanged();
			}
		}
		if (!pressed || !ItemUtils.isBlock(player.inventory.getSelectedItem())) return;
		if (player.onGround) {
			sampleOffset = pastEdge(player);
			backTicks = 0;
		} else if (player.velocityY <= 0.0D) {
			recordedHeights.add((float) (player.y - groundY));
		}
	}

	private float pastEdge(LocalClientPlayerEntity player) {
		float offset = -1.0F;
		for (float step = OFFSET_STEP; step <= player.width / 2.0F && edgeAhead(player, step); step += OFFSET_STEP) offset = step;
		return offset;
	}

	private static float mean(FloatArrayList values) {
		float sum = 0.0F;
		for (int i = 0; i < values.size(); i++) sum += values.getFloat(i);
		return sum / values.size();
	}

	private static float deviation(FloatArrayList values, float mean) {
		float sum = 0.0F;
		for (int i = 0; i < values.size(); i++) sum += (values.getFloat(i) - mean) * (values.getFloat(i) - mean);
		return (float) Math.sqrt(sum / values.size());
	}

	private boolean conditionsMet(LocalClientPlayerEntity player, InputState input) {
		if (!minecraft.focused || minecraft.screen != null) return false;
		if (input.sneak != sneakKeyPressed) return false;
		if (holdingBlocks && nonBlockTicks > HOLD_GRACE_TICKS) return false;
		if (lookingDown && player.pitch < LOOKING_DOWN_PITCH) return false;
		return !notMovingForward || input.forwardAxis() <= 0.0F;
	}

	private boolean jumpSneak(LocalClientPlayerEntity player, InputState input) {
		if (!sneakOnJump || player.velocityY <= 0.0D && player.y - groundY > jumpSneakHeight) return false;
		return !onlyWhileMoving || input.forward || input.back || input.left || input.right;
	}

	private boolean edgeAhead(LocalClientPlayerEntity player, float offset) {
		return minecraft.world.getCollisions(player, feet(player, offset)).isEmpty();
	}

	private Box feet(LocalClientPlayerEntity player, float offset) {
		InputState input = IllusionClient.INPUT.client;
		SimulatedPlayer simulated = SimulatedPlayer.of(player);
		simulated.forward = input.forwardAxis();
		simulated.sideways = input.sidewaysAxis();
		simulated.jumping = input.jump;
		simulated.sneaking = false;
		simulated.tick();

		double inset = player.width / 2.0F - offset;
		return simulated.shape.contract(inset, 0.0D, inset).moved(0.0D, -1.0D, 0.0D);
	}

	private int edgeAxes(LocalClientPlayerEntity player) {
		InputState input = IllusionClient.INPUT.client;
		float forward = input.forwardAxis();
		float sideways = input.sidewaysAxis();
		float length = MathHelper.sqrt(forward * forward + sideways * sideways);
		if (length < 1.0E-4F) return 1;

		float sin = MathHelper.sin(player.yaw * (float) Math.PI / 180.0F);
		float cos = MathHelper.cos(player.yaw * (float) Math.PI / 180.0F);
		float dirX = (sideways * cos - forward * sin) / length;
		float dirZ = (forward * cos + sideways * sin) / length;
		Box shape = player.getShape();
		int axes = 0;
		if (Math.abs(dirX) > MIN_AXIS_SHARE) {
			if (supported(shape.moved(Math.signum(dirX) * EDGE_TOLERANCE, -1.0D, 0.0D))) return 0;
			axes++;
		}
		if (Math.abs(dirZ) > MIN_AXIS_SHARE) {
			if (supported(shape.moved(0.0D, -1.0D, Math.signum(dirZ) * EDGE_TOLERANCE))) return 0;
			axes++;
		}
		return axes;
	}

	private boolean supported(Box box) {
		return !minecraft.world.getCollisions(minecraft.player, box).isEmpty();
	}

	private float edgeOffset() {
		if (!randomize) return edgeOffset;
		return Math.max(0.0F, edgeOffset - NoiseUtils.wander(0.0F, 0.15F, 5.0F, 0));
	}

	private int nextDelay() {
		float ticks = unsneakDelay / MS_PER_TICK;
		if (!randomize) return Math.round(ticks);

		ticks += NoiseUtils.wander(-0.5F, 0.5F, 30.0F, 1);
		int spike = random.nextFloat() < NoiseUtils.wander(0.04F, 0.08F, 60.0F, 2) ? random.nextInt(4) : 0;
		return Math.max(0, Math.round(ticks)) + spike;
	}

	private int edgeReach(LocalClientPlayerEntity player, HitResult target) {
		BlockPos cell = target.getPos().offset(target.face);
		Box feet = feet(player, edgeOffset);
		if (cell.getY() != (int) Math.floor(feet.minY)) return -1;
		double dx = Math.max(cell.getX() - feet.maxX, feet.minX - (cell.getX() + 1));
		double dz = Math.max(cell.getZ() - feet.maxZ, feet.minZ - (cell.getZ() + 1));
		if (dx < 0.0D && dz < 0.0D) return 0;
		return dx < 1.0D && dz < 1.0D && (dx < 0.0D || dz < 0.0D) ? 1 : -1;
	}

	private void trackRotation(LocalClientPlayerEntity player) {
		float yawDelta = MathHelper.wrapDegrees(player.yaw - lastYaw);
		float pitchDelta = player.pitch - lastPitch;
		boolean rotating = tracking && (yawDelta != 0.0F || pitchDelta != 0.0F);
		tracking = true;
		lastYaw = player.yaw;
		lastPitch = player.pitch;

		if (rotating) {
			stillTicks = 0;
			turned += (float) Math.hypot(yawDelta, pitchDelta);
		} else if (++stillTicks >= STILL_TICKS) {
			turned = 0.0F;
		}
	}

	private void updatePreSneak(LocalClientPlayerEntity player, InputState input, boolean edge) {
		if (!player.onGround || input.forward || input.jump || edgeDistance(player) >= preSneakDistance) {
			preSneaking = bridged = false;
			return;
		}
		if (edge || bridged) {
			preSneaking = false;
		} else if (!preSneaking) {
			preSneaking = turned >= MIN_TURN && player.pitch >= LOOKING_DOWN_PITCH
				&& angleFrom(player.yaw, BRIDGE_ANGLE, 0.0F) <= BRIDGE_ANGLE_TOLERANCE;
		}
	}

	private double edgeDistance(LocalClientPlayerEntity player) {
		int x = (int) Math.floor(player.x);
		int y = (int) Math.floor(player.y) - 1;
		int z = (int) Math.floor(player.z);
		double distance = Double.MAX_VALUE;
		if (open(x - 1, y, z)) distance = player.x - x;
		if (open(x + 1, y, z)) distance = Math.min(distance, x + 1 - player.x);
		if (open(x, y, z - 1)) distance = Math.min(distance, player.z - z);
		if (open(x, y, z + 1)) distance = Math.min(distance, z + 1 - player.z);
		return distance;
	}

	private boolean open(int x, int y, int z) {
		return minecraft.world.isAir(new BlockPos(x, y, z));
	}

	private void align(LocalClientPlayerEntity player, InputState input) {
		if (!input.back || input.forward || input.left || input.right
			|| angleFrom(player.yaw, 90.0F, 45.0F) > DIAGONAL_TOLERANCE) {
			alignDirection = 0;
			return;
		}

		double[] back = acceleration(player, -1.0F, 0.0F);
		double[] backLeft = acceleration(player, -1.0F, 1.0F);
		double slope = Math.signum(back[0]) * Math.signum(back[1]);
		double settle = 1.0D / (1.0D - (player.onGround ? GROUND_DRAG : AIR_DRAG));
		double line = player.x - slope * player.z + (player.velocityX + back[0] - slope * (player.velocityZ + back[1])) * settle;
		double error = line - Math.round(line);
		if (alignDirection == 0) {
			if (Math.abs(error) <= ALIGN_START) return;
			alignDirection = error > 0.0D ? 1 : -1;
		}

		double shift = (backLeft[0] - back[0] - slope * (backLeft[1] - back[1])) * settle;
		if (error * alignDirection <= Math.max(ALIGN_STOP, Math.abs(shift) / 2.0D)) {
			alignDirection = 0;
			return;
		}
		InputState client = IllusionClient.INPUT.client;
		client.left = error * shift < 0.0D;
		client.right = error * shift > 0.0D;
	}

	private double[] acceleration(LocalClientPlayerEntity player, float forward, float sideways) {
		float scale = sneaking ? INPUT_SCALE * SNEAK_SCALE : INPUT_SCALE;
		forward *= scale;
		sideways *= scale;
		double speed = (player.onGround ? player.getSpeed() : AIR_SPEED) / Math.max(1.0D, Math.hypot(forward, sideways));
		double[] direction = MoveUtils.inputDirection(player.yaw, forward, sideways);
		return new double[]{direction[0] * speed, direction[1] * speed};
	}

	private static float angleFrom(float yaw, float step, float target) {
		float angle = (yaw % step + step) % step;
		float distance = Math.abs(angle - target);
		return Math.min(distance, step - distance);
	}
}
