package org.codeberg.illusionclient.feature.module.combat;

import org.codeberg.illusionclient.event.api.Listen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.options.KeyBinding;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.event.input.KeyPressEvent;
import org.codeberg.illusionclient.event.player.PlayerTickEvent;
import org.codeberg.illusionclient.event.render.Render3DEvent;
import org.codeberg.illusionclient.event.input.ScreenInputEvent;
import org.codeberg.illusionclient.feature.config.api.Check;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.config.api.Requires;
import org.codeberg.illusionclient.feature.config.api.Slider;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.Weapons;
import org.codeberg.illusionclient.feature.ui.ClickGui;
import org.codeberg.illusionclient.mixin.ScreenInvoker;
import org.codeberg.illusionclient.util.ClickPattern;
import org.codeberg.illusionclient.util.ClickProfile;
import org.codeberg.illusionclient.util.ClickRecorder;
import org.codeberg.illusionclient.util.MouseUtils;
import org.codeberg.illusionclient.util.PlayerUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.time.Instant;

@ModuleInfo(name = "Auto Clicker", category = Category.COMBAT)
@Description("Clicks for you while the attack key is held.")
public class AutoClicker extends Module {
	private static final float MAX_FRAME_MS = 100.0F;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final ClickPattern pattern = new ClickPattern();

	@Description("Jitter clicks evenly, Butterfly in bursts of two, Recorded replays your own clicks.")
	@Combo(value = "Click Pattern", options = {"Jitter", "Butterfly", "Recorded"})
	public String clickPattern = "Jitter";

	@Description("Which recording to replay, from config/illusion/clicks.")
	@Requires(value = "clickPattern", is = "Recorded")
	@Combo(value = "Profile", source = "profiles")
	public String profile = "";

	@Description("Saves your own clicks to a new profile instead of clicking.")
	@Requires(value = "clickPattern", is = "Recorded")
	@Check("Record")
	public boolean record = false;

	@Description("How many clicks per second to aim for.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Slider(value = "Target CPS", min = 1.0F, max = 25.0F)
	public float targetCps = 13.2F;

	@Description("Varies click timing to avoid a fixed rhythm.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Check("Randomize")
	public boolean randomize = true;

	@Description("How much click timing varies. 1 is the default.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Requires("randomize")
	@Slider(value = "Timing Variance", min = 0.0F, max = 2.0F)
	public float timingVariance = 1.0F;

	@Description("How much the CPS wanders up and down over time. 1 is the default.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Requires("randomize")
	@Slider(value = "CPS Drift", min = 0.0F, max = 2.0F)
	public float cpsDrift = 1.0F;

	@Description("How much the time each click is held varies. 1 is the default.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Requires("randomize")
	@Slider(value = "Hold Variance", min = 0.0F, max = 2.0F)
	public float holdVariance = 1.0F;

	@Description("How much the gap between the two Butterfly clicks varies. 1 is the default.")
	@Requires(value = "clickPattern", is = "Butterfly")
	@Requires("randomize")
	@Slider(value = "Gap Variance", min = 0.0F, max = 2.0F)
	public float gapVariance = 1.0F;

	@Description("How often Butterfly clicks once instead of twice. 1 is the default.")
	@Requires(value = "clickPattern", is = "Butterfly")
	@Requires("randomize")
	@Slider(value = "Single Clicks", min = 0.0F, max = 3.0F)
	public float singleClicks = 1.0F;

	@Description("How long clicking takes to reach full speed. 1 is the default, 0 starts at full speed.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Slider(value = "Ramp Up", min = 0.0F, max = 3.0F)
	public float rampUp = 1.0F;

	@Description("Sometimes slows down briefly, like a tired hand.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Check("Simulate Exhaust")
	public boolean simulateExhaust = true;

	@Description("How often slowdowns happen. 1 is the default.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Requires("simulateExhaust")
	@Slider(value = "Drop Frequency", min = 0.0F, max = 3.0F)
	public float dropFrequency = 1.0F;

	@Description("How long each slowdown lasts. 1 is the default.")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Requires("simulateExhaust")
	@Slider(value = "Drop Length", min = 0.0F, max = 3.0F)
	public float dropLength = 1.0F;

	@Description("Pauses on blocks so you can mine.")
	@Check("Allow Breaking Blocks")
	public boolean allowBreakingBlocks = true;

	@Description("Clicks while Shift-clicking in inventories.")
	@Check("Inventory")
	public boolean inventory = false;

	@Description("Uses the click pattern in inventories, not max speed.")
	@Requires("inventory")
	@Requires(value = "clickPattern", is = {"Jitter", "Butterfly"})
	@Check("Randomize Inventory")
	public boolean randomizeInventorySpeed = true;

	@Description("Only clicks while holding a weapon.")
	@Check("Weapons Only")
	public boolean weaponsOnly = true;

	@Description("Pauses while you use an item.")
	@Check("Not Using Item")
	public boolean notUsingItem = false;

	private long lastFrame;
	private boolean inScreen;
	private int inventoryClicks;
	private ClickProfile loaded;
	private String loadedName;
	private ClickRecorder recorder;
	private String session;

	@Override
	public String details() {
		if (recorder != null) return recorder.clicks() + " clicks";
		if (clickPattern.equals("Recorded")) return profile.isEmpty() ? "No profile" : profile;
		return clickPattern + " " + number(targetCps) + "cps";
	}

	@Override
	public void onDisable() {
		syncRecording();
		stop();
	}

	public String[] profiles() {
		return ClickProfile.names();
	}

	@Listen
	public void onKeyPress(KeyPressEvent event) {
		syncRecording();
		int key = minecraft.options.attackKey.getKeyCode();
		if (recorder == null || key == 0 || event.key() != key) return;

		long nanos = key < 0 ? Mouse.getEventNanoseconds() : Keyboard.getEventNanoseconds();
		if (fighting() ? recorder.input(event.down(), nanos) : recorder.interrupt()) recorder.profile.save(session);
	}

	@Listen
	public void onPlayerTick(PlayerTickEvent event) {
		LocalClientPlayerEntity player = minecraft.player;
		if (player != null) pattern.tick(player.yaw, player.pitch);
	}

	@Listen
	public void onScreenInput(ScreenInputEvent event) {
		if (!activeInInventory()) {
			inventoryClicks = 0;
			return;
		}
		if (!pacedInventory()) inventoryClicks = 1;

		Screen screen = minecraft.screen;
		int x = Mouse.getX() * screen.width / minecraft.width;
		int y = screen.height - Mouse.getY() * screen.height / minecraft.height - 1;
		ScreenInvoker invoker = (ScreenInvoker) screen;
		for (; inventoryClicks > 0; inventoryClicks--) {
			invoker.illusion$mouseClicked(x, y, 0);
			invoker.illusion$mouseReleased(x, y, 0);
			IllusionClient.STATS.autoClicks++;
		}
	}

	@Listen
	public void onRender3D(Render3DEvent event) {
		long now = System.nanoTime();
		float delta = Math.min(MAX_FRAME_MS, (now - lastFrame) / 1.0E6F);
		lastFrame = now;

		syncRecording();
		ClickProfile profile = clickPattern.equals("Recorded") ? profile() : null;
		boolean idle = recorder != null || clickPattern.equals("Recorded") && profile == null;

		LocalClientPlayerEntity player = minecraft.player;
		boolean screen = minecraft.screen != null;
		if (player == null || idle || screen != inScreen || !(screen ? activeInInventory() : activeInGame(player))) {
			inScreen = screen;
			stop();
			return;
		}
		if (screen && !pacedInventory()) return;

		pattern.variance = timingVariance;
		pattern.dropFrequency = dropFrequency;
		pattern.dropLength = dropLength;
		pattern.drift = cpsDrift;
		pattern.holdVariance = holdVariance;
		pattern.gapVariance = gapVariance;
		pattern.singles = singleClicks;
		pattern.rampUp = rampUp;
		int edges = pattern.advance(delta, profile, targetCps, randomize, simulateExhaust, clickPattern.equals("Butterfly"));
		if (screen) {
			inventoryClicks += pattern.presses();
			return;
		}
		IllusionClient.STATS.autoClicks += pattern.presses();

		int key = minecraft.options.attackKey.getKeyCode();
		if (key < 0) {
			for (; edges > 0; edges--) MouseUtils.toggle(key + 100);
		} else {
			for (int presses = pattern.presses(); presses > 0; presses--) KeyBinding.click(key);
		}
	}

	private void stop() {
		pattern.stop();
		MouseUtils.resync(minecraft.options.attackKey.getKeyCode() + 100);
	}

	private ClickProfile profile() {
		if (!profile.equals(loadedName)) {
			loadedName = profile;
			loaded = profile.isEmpty() ? null : ClickProfile.load(profile);
		}
		return loaded;
	}

	private void syncRecording() {
		boolean recording = isEnabled() && record && clickPattern.equals("Recorded");
		if (recording == (recorder != null)) return;

		if (recording) {
			recorder = new ClickRecorder();
			session = String.valueOf(Instant.now().getEpochSecond());
			return;
		}
		if (recorder.interrupt()) recorder.profile.save(session);
		if (!recorder.profile.streaks.isEmpty()) {
			profile = session;
			loadedName = null;
		}
		recorder = null;
	}

	private boolean fighting() {
		LocalClientPlayerEntity player = minecraft.player;
		return player != null && player.isAlive() && minecraft.screen == null
			&& (!weaponsOnly || Weapons.isHolding(player)) && !PlayerUtils.aimingAtBlock();
	}

	private boolean activeInGame(LocalClientPlayerEntity player) {
		if (!IllusionClient.INPUT.held(minecraft.options.attackKey) || !player.isAlive()) return false;
		if (weaponsOnly && !Weapons.isHolding(player)) return false;
		if (notUsingItem && player.getItemInUse() != null) return false;
		return !allowBreakingBlocks || !PlayerUtils.canBreakAimedBlock();
	}

	private boolean pacedInventory() {
		return randomizeInventorySpeed || clickPattern.equals("Recorded");
	}

	private boolean activeInInventory() {
		return inventory && minecraft.screen instanceof InventoryMenuScreen && !ClickGui.isOpen()
			&& Mouse.isButtonDown(0) && Screen.isShiftDown();
	}
}
