package org.codeberg.illusionclient.feature.ui.component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.*;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ObsHidden;
import org.codeberg.illusionclient.feature.module.client.ClickGuiModule;
import org.codeberg.illusionclient.feature.module.client.ObsProof;
import org.codeberg.illusionclient.feature.config.ConfigManager;
import org.codeberg.illusionclient.feature.module.profiles.CreateProfile;
import org.codeberg.illusionclient.feature.module.profiles.GenerateProfile;
import org.codeberg.illusionclient.feature.module.profiles.ProfileModule;
import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.feature.ui.Tooltip;
import org.codeberg.illusionclient.util.SwapHook;
import org.lwjgl.input.Keyboard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ModuleComponent extends Group {
	public static final int KEY_HEIGHT = 14;
	public static final String HOLD = "Hold";
	private static final String OBS = "OBS";
	private static final int OBS_HIDDEN = 0xFF2E7D4F, OBS_PARTIAL = 0xFFB58B1E, OBS_VISIBLE = 0xFFB3453C;
	private static final int OBS_TEXT = 0xFFFFFFFF, OBS_PARTIAL_TEXT = 0xFF2B1D00;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

	final Module module;
	private int keyLeft, keyRight, holdLeft, holdRight;

	public ModuleComponent(Module module) {
		this.module = module;
		this.description = module.description;
		children.addAll(options(module).stream().filter(option -> !(option instanceof KeybindComponent)).toList());
	}

	public static boolean showHold(Module module, boolean hovered) {
		return module.getBind() != Keyboard.KEY_NONE && !(module instanceof ClickGuiModule) && !KeybindComponent.listening(module) && (module.isHold() || hovered);
	}

	public static String keyLabel(Module module, boolean hovered, boolean listening) {
		if (!module.bindable) return null;
		if (listening) return "Press a key...";
		if (module.getBind() != Keyboard.KEY_NONE) return module.bindName();
		return hovered ? "None" : null;
	}

	public static int fill(Module module, boolean hovered) {
		if (module.isEnabled()) return hovered ? Draw.PRIMARY_CONTAINER_HOVER : Draw.PRIMARY_CONTAINER;
		return hovered ? Draw.CONTAINER_HIGHEST : Draw.CONTAINER_HIGH;
	}

	public static int keyWidth(String label) {
		return Draw.width(label) + 10;
	}

	public static void drawKey(int x, int y, String label, boolean listening, boolean bound, boolean hovered) {
		int fill, color;
		if (listening) {
			fill = Draw.PRIMARY_CONTAINER;
			color = Draw.ON_PRIMARY_CONTAINER;
		} else if (bound) {
			fill = Draw.TERTIARY_CONTAINER;
			color = Draw.ON_TERTIARY_CONTAINER;
		} else {
			fill = hovered ? Draw.CONTAINER_HIGH : Draw.CONTAINER;
			color = hovered ? Draw.TEXT : Draw.FAINT;
		}
		Draw.rounded(x, y, keyWidth(label), KEY_HEIGHT, 4.0F, fill);
		Draw.text(label, x + 5, Draw.textY(y, KEY_HEIGHT), color);
	}

	public static void drawTag(int x, int y, String tag) {
		boolean partial = tag.equals("Semi");
		Draw.rounded(x, y, keyWidth(tag), KEY_HEIGHT, 4.0F, switch (tag) {
			case "Closet" -> OBS_HIDDEN;
			case "Semi" -> OBS_PARTIAL;
			default -> OBS_VISIBLE;
		});
		Draw.text(tag, x + 5, Draw.textY(y, KEY_HEIGHT), partial ? OBS_PARTIAL_TEXT : OBS_TEXT);
	}

	public static int obsMarkerSpace(Module module, int gap) {
		return module.obsHidden == null || !SwapHook.SUPPORTED || !IllusionClient.MODULES.get(ObsProof.class).isEnabled() ? 0 : keyWidth(OBS) + gap;
	}

	public static int drawObsMarker(Module module, int right, int y, int mouseX, int mouseY, int gap) {
		if (obsMarkerSpace(module, gap) == 0) return right;

		ObsHidden info = module.obsHidden;
		int width = keyWidth(OBS);
		int left = right - width;
		Draw.rounded(left, y, width, KEY_HEIGHT, 4.0F, switch (info.value()) {
			case YES -> OBS_HIDDEN;
			case PARTIAL -> OBS_PARTIAL;
			case NO -> OBS_VISIBLE;
		});
		Draw.text(OBS, left + 5, Draw.textY(y, KEY_HEIGHT), info.value() == ObsHidden.Level.PARTIAL ? OBS_PARTIAL_TEXT : OBS_TEXT);
		if (mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + KEY_HEIGHT) {
			Tooltip.pin(switch (info.value()) {
				case YES -> "Hidden from OBS while OBS Proof is on.";
				case PARTIAL -> "Hidden from OBS while OBS Proof is on, unless " + info.unless() + " is enabled.";
				case NO -> "OBS Proof can't hide this module. It still shows up on stream.";
			});
		}
		return left - gap;
	}

	private static String date(long millis) {
		return millis == 0 ? "Unknown" : DATE.format(Instant.ofEpochMilli(millis));
	}

	public static List<Component> options(Module module) {
		List<Component> components = new ObjectArrayList<>();
		SectionComponent section = null;
		for (Option option : module.options) {
			Component component = switch (option.info) {
				case Check _ -> new CheckComponent(option);
				case Slider _ -> new SliderComponent(option);
				case RangeSlider _ -> new RangeSliderComponent(option);
				case Combo _ -> new ComboComponent(option);
				case MultiSelect _ -> new MultiSelectComponent(option);
				case null, default -> new ColorComponent(option);
			};
			if (option.section() == null) {
				components.add(component);
				continue;
			}
			if (section == null || !section.name.equals(option.section())) components.add(section = new SectionComponent(option.section()));
			section.children.add(component);
		}
		if (module instanceof ClickGuiModule gui) {
			components.add(new ButtonComponent("Positions", "Reset", gui::resetPositions));
			components.add(new ButtonComponent("Config Folder", "Open", IllusionClient.CONFIG::openFolder));
		}
		if (module instanceof CreateProfile) {
			TextInputComponent name = new TextInputComponent("Name", "Optional", IllusionClient.CONFIG::createNamedProfile);
			components.add(name);
			components.add(new ButtonComponent("Profile", "Create", name::submit));
		}
		if (module instanceof GenerateProfile generator) {
			TextInputComponent name = new TextInputComponent("Name", "Optional", generator::generate);
			components.add(name);
			components.add(new ButtonComponent("Profile", "Generate", name::submit));
		}
		if (module instanceof ProfileModule profile && !profile.profile.equals(ConfigManager.DEFAULT_PROFILE)) {
			components.add(new TextInputComponent("Rename", profile.profile, name -> IllusionClient.CONFIG.renameProfile(profile.profile, name)));
			components.add(new InfoComponent("Created", () -> date(profile.created)));
			components.add(new InfoComponent("Updated", () -> date(profile.updated)));
			components.add(new ButtonComponent("Current Settings", "Save", () -> IllusionClient.CONFIG.saveProfile(profile.profile)));
			components.add(new ButtonComponent("Profile", "Delete", () -> IllusionClient.CONFIG.deleteProfile(profile.profile)));
		}
		if (module.bindable) components.add(new KeybindComponent(module));
		return components;
	}

	@Override
	protected void renderHeader(int mouseX, int mouseY) {
		boolean hovered = hovered(mouseX, mouseY);
		if (hovered) Tooltip.show(description);

		Draw.rounded(x, y, width, HEIGHT, RADIUS, headerRadius(), fill(module, hovered));

		int textRight = drawObsMarker(module, right(), y + (HEIGHT - KEY_HEIGHT) / 2, mouseX, mouseY, 6);
		boolean listening = KeybindComponent.listening(module);
		String key = keyLabel(module, hovered, listening);
		keyLeft = keyRight = textRight;
		if (key != null) {
			keyLeft = keyRight - keyWidth(key);
			boolean keyHovered = hovered && mouseX >= keyLeft && mouseX < keyRight;
			drawKey(keyLeft, y + (HEIGHT - KEY_HEIGHT) / 2, key, listening, module.getBind() != Keyboard.KEY_NONE, keyHovered);
			textRight = keyLeft - 6;
		}

		holdLeft = holdRight = textRight;
		if (showHold(module, hovered)) {
			holdLeft = holdRight - keyWidth(HOLD);
			boolean holdHovered = hovered && mouseX >= holdLeft && mouseX < holdRight;
			drawKey(holdLeft, y + (HEIGHT - KEY_HEIGHT) / 2, HOLD, false, module.isHold(), holdHovered);
			textRight = holdLeft - 6;
		}

		String name = Draw.ellipsize(module.name, textRight - x - PADDING, Draw.Weight.MEDIUM);
		Draw.text(name, x + PADDING, Draw.textY(y, HEIGHT), module.isEnabled() ? Draw.ON_PRIMARY_CONTAINER : Draw.TEXT);
	}

	@Override
	protected void headerClicked(int mouseX, int mouseY, int button) {
		if (button == 2 || button == 0 && mouseX >= keyLeft && mouseX < keyRight) KeybindComponent.listen(module);
		else if (button == 0 && mouseX >= holdLeft && mouseX < holdRight) module.setHold(!module.isHold());
		else if (button == 0 && module.toggleable) module.toggle();
		else if (button == 1 || button == 0) open = !open;
	}
}
