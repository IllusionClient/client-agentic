package org.codeberg.illusionclient.feature.module.client;

import org.codeberg.illusionclient.feature.config.api.ColorPicker;
import org.codeberg.illusionclient.feature.config.api.Combo;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.ui.ClickGui;
import org.codeberg.illusionclient.feature.ui.FramesGui;
import org.codeberg.illusionclient.feature.ui.PanelGui;
import org.lwjgl.input.Keyboard;

import java.awt.*;

@Description("Opens this menu.")
@ObsHidden
@ModuleInfo(name = "ClickGui", category = Category.CLIENT, bind = Keyboard.KEY_RSHIFT)
public class ClickGuiModule extends Module {
	@Description("Panel shows one window with a sidebar, Frames shows a draggable window per category.")
	@Combo(value = "Style", options = {"Panel", "Frames"})
	public String style = "Panel";

	@Description("The font used by this menu and the HUD.")
	@Combo(value = "Font", options = {"Minecraft", "Inter"})
	public String font = "Inter";

	@Description("This menu's own GUI scale. Game follows Minecraft's setting; a smaller scale fits more on small screens.")
	@Combo(value = "Scale", options = {"Game", "1x", "2x", "3x", "4x"})
	public String scale = "Game";

	@Description("The accent color of this menu.")
	@ColorPicker("Color")
	public Color color = new Color(41, 227, 143);

	private FramesGui frames;
	private PanelGui panel;

	public int scale() {
		return scale.equals("Game") ? 0 : scale.charAt(0) - '0';
	}

	@Override
	public void setBind(int bind) {
		super.setBind(bind == Keyboard.KEY_NONE ? Keyboard.KEY_RSHIFT : bind);
	}

	public void resetPositions() {
		frames().resetPositions();
		panel().resetPosition();
	}

	private FramesGui frames() {
		if (frames == null) frames = new FramesGui();
		return frames;
	}

	private PanelGui panel() {
		if (panel == null) panel = new PanelGui();
		return panel;
	}

	@Override
	public void onEnable() {
		if (ClickGui.isOpen()) ClickGui.close();
		else (style.equals("Frames") ? frames() : panel()).open();
		setEnabled(false);
	}
}
