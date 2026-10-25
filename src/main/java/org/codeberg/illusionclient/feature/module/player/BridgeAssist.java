package org.codeberg.illusionclient.feature.module.player;

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
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.util.ItemUtils;
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
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();

	@Description("How far past the edge before sneaking.")
	@Slider(value = "Edge Offset", min = 0.0F, max = 0.3F, step = 0.01F)
	public float edgeOffset = 0.2F;

	@Description("Sneak time after you're back on the block.")
	@Slider(value = "Unsneak Delay", min = 0.0F, max = 300.0F)
	public float unsneakDelay = 150.0F;

	@Description("Swaps to hotbar blocks.")
	@Combo(value = "Select Blocks", options = {"No", "On depletion", "Always"})
	public String selectBlocks = "On depletion";

	@Description("Varies the edge offset and unsneak delay.")
	@Check("Randomize")
	public boolean randomize = true;

	@Description("Also sneaks at edges while rising from a jump.")
	@Check("Sneak On Jump")
	public boolean sneakOnJump = false;

	@Description("Only sneaks in the air while you hold a movement key.")
	@Requires("sneakOnJump")
	@Check("Only While Moving")
	public boolean onlyWhileMoving = true;

	@Description("Blocks placements that would leave you at the edge and need a second sneak.")
	@Check("Avoid Double-Sneaking")
	public boolean avoidDoubleSneaking = false;

	@Description("Blocks placements until you've backed up to the edge on every axis you're moving along.")
	@Check("Wait For Edge")
	public boolean waitForEdge = false;

	@Description("Only works while you hold sneak, and lets go of it for you away from edges.")
	@Check("Sneak Key Pressed")
	public boolean sneakKeyPressed = false;

	@Description("Lets you place the next block without sneaking first.")
	@Check("Skip Sneak")
	public boolean skipSneak = false;

	@Description("Only works while you hold blocks.")
	@Check("Holding Blocks")
	public boolean holdingBlocks = false;

	@Description("Only works while you look down.")
	@Check("Looking Down")
	public boolean lookingDown = false;

	@Description("Only works when not pressing forward.")
	@Check("Not Moving Forward")
	public boolean notMovingForward = false;

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

	@Override
	public String details() {
		return number(unsneakDelay) + "ms";
	}

	@Override
	public void onDisable() {
		sneaking = armed = sneakedSincePlace = pendingSneak = holdSneak = false;
		unsneakTicks = placementTicks = nonBlockTicks = groundTicks = latchedPlacements = 0;
		lastBlock = null;
		lastSlot = -1;
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		InputState input = IllusionClient.INPUT.player;
		nonBlockTicks = ItemUtils.isBlock(player.inventory.getSelectedItem()) ? 0 : nonBlockTicks + 1;
		if (!armed) latchedPlacements = 0;
		armed = false;
		if (!conditionsMet(player, input)) {
			sneaking = pendingSneak = holdSneak = false;
			unsneakTicks = 0;
			return;
		}
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
		if (!sneaking) {
			sneaking = edge && (sneakKeyPressed || player.onGround || rising(player, input));
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
			slot = bestSlot(inventory, null);
		} else if (selectBlocks.equals("On depletion") && lastBlock != null
			&& inventory.selectedSlot == lastSlot && inventory.getSelectedItem() == null) {
			slot = bestSlot(inventory, lastBlock);
		}
		if (slot != -1 && slot != inventory.selectedSlot && IllusionClient.MODULES.claimSlot(this)) {
			inventory.selectedSlot = slot;
			IllusionClient.MODULES.releaseSlot(this);
		}

		lastBlock = ItemUtils.block(inventory.getSelectedItem());
		lastSlot = inventory.selectedSlot;
	}

	private boolean conditionsMet(LocalClientPlayerEntity player, InputState input) {
		if (!minecraft.focused || minecraft.screen != null) return false;
		if (input.sneak != sneakKeyPressed) return false;
		if (holdingBlocks && nonBlockTicks > HOLD_GRACE_TICKS) return false;
		if (lookingDown && player.pitch < LOOKING_DOWN_PITCH) return false;
		return !notMovingForward || input.forwardAxis() <= 0.0F;
	}

	private boolean rising(LocalClientPlayerEntity player, InputState input) {
		if (!sneakOnJump || player.velocityY <= 0.0D) return false;
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

	private static int bestSlot(PlayerInventory inventory, Block preferred) {
		int best = -1;
		int bestPriority = -1;
		for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
			if (preferred != null && ItemUtils.block(inventory.items[slot]) == preferred) return slot;
			int priority = ItemUtils.blockPriority(inventory.items[slot]);
			if (priority > bestPriority || priority == bestPriority && priority >= 0 && slot == inventory.selectedSlot) {
				best = slot;
				bestPriority = priority;
			}
		}
		return best;
	}
}
