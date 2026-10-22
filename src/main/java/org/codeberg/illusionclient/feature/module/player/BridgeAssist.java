package org.codeberg.illusionclient.feature.module.player;

import io.github.nevalackin.radbus.Listen;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.InputEvent;
import org.codeberg.illusionclient.event.PlayerTickEvent;
import org.codeberg.illusionclient.event.UseItemEvent;
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
	private static final float DIAGONAL_MIN = 30.0F;
	private static final float DIAGONAL_MAX = 60.0F;
	private static final double CORNER_MARGIN = 0.15D;
	private static final double OVER_CORNER_MARGIN = 0.25D;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final Random random = new Random();

	@Description("How far past the edge before sneaking.")
	@Slider(value = "Edge Offset", min = 0.0F, max = 0.3F)
	public float edgeOffset = 0.2F;

	@Description("Sneak time after you're back on the block.")
	@Slider(value = "Unsneak Delay", min = 50.0F, max = 300.0F)
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

	@Description("Blocks mid-face placements that cause a double sneak on diagonals.")
	@Check("Avoid Double-Sneaking")
	public boolean avoidDoubleSneaking = false;

	@Description("Only works while you hold sneak, and lets go of it for you away from edges.")
	@Check("Sneak Key Pressed")
	public boolean sneakKeyPressed = false;

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
	private int delay;
	private int unsneakTicks;
	private int placementTicks;
	private int nonBlockTicks;
	private Block lastBlock;
	private int lastSlot = -1;

	@Override
	public String details() {
		return number(unsneakDelay) + "ms";
	}

	@Override
	public void onDisable() {
		sneaking = armed = false;
		unsneakTicks = placementTicks = nonBlockTicks = 0;
		lastBlock = null;
		lastSlot = -1;
	}

	@Listen
	public void onInput(InputEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		InputState input = IllusionClient.INPUT.player;
		nonBlockTicks = ItemUtils.isBlock(player.inventory.getSelectedItem()) ? 0 : nonBlockTicks + 1;
		armed = false;
		if (!conditionsMet(player, input)) {
			sneaking = false;
			unsneakTicks = 0;
			return;
		}

		boolean edge = edgeAhead(player);
		if (!sneaking) {
			sneaking = edge && (sneakKeyPressed || player.onGround || rising(player, input));
			if (sneaking) {
				delay = nextDelay();
				unsneakTicks = delay;
			}
		} else if (edge) {
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
		if (avoidDoubleSneaking && armed && placementTicks != PLACEMENT_TICKS && midFace(player, target)) {
			event.cancelled = true;
			return;
		}
		placementTicks = PLACEMENT_TICKS;
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		if (placementTicks > 0) placementTicks--;
		LocalClientPlayerEntity player = minecraft.player;
		if (player == null || minecraft.screen != null) return;

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

	private boolean edgeAhead(LocalClientPlayerEntity player) {
		InputState input = IllusionClient.INPUT.client;
		SimulatedPlayer simulated = SimulatedPlayer.of(player);
		simulated.forward = input.forwardAxis();
		simulated.sideways = input.sidewaysAxis();
		simulated.jumping = input.jump;
		simulated.sneaking = false;
		simulated.tick();

		double inset = player.width / 2.0F - edgeOffset();
		Box feet = simulated.shape.contract(inset, 0.0D, inset).moved(0.0D, -1.0D, 0.0D);
		return minecraft.world.getCollisions(player, feet).isEmpty();
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
		return Math.max(1, Math.round(ticks)) + spike;
	}

	private boolean midFace(LocalClientPlayerEntity player, HitResult target) {
		if (!target.face.getAxis().isHorizontal()) return false;
		InputState input = IllusionClient.INPUT.player;
		if (!player.onGround || !input.back || input.forward || input.left || input.right) return false;
		float angle = (player.yaw % 90.0F + 90.0F) % 90.0F;
		if (angle < DIAGONAL_MIN || angle > DIAGONAL_MAX) return false;

		Vec3d hit = target.facePos;
		if (Math.abs(hit.x - player.x) > 1.0D || Math.abs(hit.z - player.z) > 1.0D || hit.y > player.y || hit.y < player.y - 1.0D) return false;

		BlockPos cell = target.getPos().offset(target.face);
		boolean over = (int) Math.floor(player.x) == cell.getX() && (int) Math.floor(player.z) == cell.getZ();
		double margin = over ? OVER_CORNER_MARGIN : CORNER_MARGIN;
		double along = target.face.getAxis() == Direction.Axis.X ? hit.z : hit.x;
		double fraction = along - Math.floor(along);
		return fraction >= margin && fraction <= 1.0D - margin;
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
