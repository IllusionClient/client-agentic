package org.codeberg.illusionclient.feature.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.BookEditScreen;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.game.SignEditScreen;
import net.minecraft.client.gui.screen.game.inventory.AnvilScreen;
import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen;
import net.minecraft.client.render.platform.GlStateManager;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.client.ClickGuiModule;
import org.codeberg.illusionclient.feature.ui.component.KeybindComponent;
import org.codeberg.illusionclient.util.HiddenLayer;
import org.codeberg.illusionclient.util.KeyboardUtils;
import org.codeberg.illusionclient.util.MouseUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public abstract class ClickGui {
	private static final int MIN_WIDTH = 320, MIN_HEIGHT = 240;

	private static ClickGui current;
	private static boolean repeatEvents;

	protected final Minecraft minecraft = Minecraft.getInstance();
	protected int width, height, scale;
	private int displayWidth, displayHeight;

	public static boolean isOpen() {
		return current != null;
	}

	public static void close() {
		ClickGui gui = current;
		if (gui == null) return;

		current = null;
		Keyboard.enableRepeatEvents(repeatEvents);
		gui.removed();
		if (gui.minecraft.screen instanceof Host) gui.minecraft.openScreen(null);
	}

	public static void render(float tickDelta) {
		if (current != null) current.renderOverlay(tickDelta);
	}

	public static void screenChanged(Screen screen) {
		if (screen == null) close();
	}

	public static boolean handleMouse(Screen screen) {
		if (MouseUtils.synthetic()) return current != null;
		if (current != null) {
			current.mouseEvent();
			return true;
		}
		return Mouse.getEventButtonState() && toggleBind(Module.mouseBind(Mouse.getEventButton()), screen);
	}

	public static boolean handleKeyboard(Screen screen) {
		if (KeyboardUtils.synthetic()) return current != null;
		if (current != null) {
			current.keyboardEvent();
			return true;
		}
		return Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent() && toggleBind(Keyboard.getEventKey(), screen);
	}

	private static boolean toggleBind(int key, Screen screen) {
		ClickGuiModule module = IllusionClient.MODULES.get(ClickGuiModule.class);
		if (key != module.getBind() || typing(screen)) return false;

		module.toggle();
		return true;
	}

	private static boolean typing(Screen screen) {
		return screen instanceof ChatScreen || screen instanceof SignEditScreen || screen instanceof BookEditScreen
			|| screen instanceof AnvilScreen || screen instanceof CreativeInventoryScreen;
	}

	private static int scale(Minecraft minecraft) {
		int wanted = IllusionClient.MODULES.get(ClickGuiModule.class).scale();
		if (wanted == 0) return Draw.vanillaScale(minecraft);

		int scale = 1;
		while (scale < wanted && minecraft.width / (scale + 1) >= MIN_WIDTH && minecraft.height / (scale + 1) >= MIN_HEIGHT) scale++;
		return scale;
	}

	public void open() {
		if (current == this) return;
		close();
		if (minecraft.screen == null) minecraft.openScreen(new Host());

		current = this;
		repeatEvents = Keyboard.areRepeatEventsEnabled();
		Keyboard.enableRepeatEvents(true);
		resize();
	}

	protected boolean isCurrent() {
		return current == this;
	}

	private void resize() {
		scale = scale(minecraft);
		displayWidth = minecraft.width;
		displayHeight = minecraft.height;
		width = Math.ceilDiv(displayWidth, scale);
		height = Math.ceilDiv(displayHeight, scale);
		init();
	}

	private void renderOverlay(float tickDelta) {
		if (scale(minecraft) != scale || minecraft.width != displayWidth || minecraft.height != displayHeight) resize();

		HiddenLayer.begin();
		float factor = scale / (float) Draw.vanillaScale(minecraft);
		GlStateManager.pushMatrix();
		GlStateManager.scalef(factor, factor, 1.0F);
		Draw.setPixelScale(scale);

		renderScaled(Mouse.getX() * width / minecraft.width, height - Mouse.getY() * height / minecraft.height - 1, tickDelta);

		Draw.flush();
		Draw.setPixelScale(0);
		GlStateManager.popMatrix();
		HiddenLayer.end();
	}

	private void mouseEvent() {
		int mouseX = Mouse.getEventX() * width / minecraft.width;
		int mouseY = height - Mouse.getEventY() * height / minecraft.height - 1;
		int button = Mouse.getEventButton();
		if (Mouse.getEventButtonState()) mouseClicked(mouseX, mouseY, button);
		else if (button != -1) mouseReleased(mouseX, mouseY, button);

		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) scrolled(mouseX, Integer.signum(wheel));
	}

	private void keyboardEvent() {
		if (Keyboard.getEventKeyState()) {
			int key = Keyboard.getEventKey();
			if (key == IllusionClient.MODULES.get(ClickGuiModule.class).getBind() && !Keyboard.isRepeatEvent() && !KeybindComponent.isListening() && !capturesBind()) close();
			else keyPressed(Keyboard.getEventCharacter(), key);
		}
		minecraft.handleGuiKeyBindings();
	}

	protected boolean capturesBind() {
		return false;
	}

	protected abstract void init();

	protected abstract void renderScaled(int mouseX, int mouseY, float tickDelta);

	protected abstract void mouseClicked(int mouseX, int mouseY, int button);

	protected abstract void mouseReleased(int mouseX, int mouseY, int button);

	protected abstract void scrolled(int mouseX, int direction);

	protected void keyPressed(char chr, int key) {
		if (key == Keyboard.KEY_ESCAPE) close();
	}

	protected abstract void removed();

	private static final class Host extends Screen {
		@Override
		public boolean shouldPauseGame() {
			return false;
		}
	}
}
