package org.codeberg.illusionclient.feature.module.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.codeberg.illusionclient.IllusionClient;
import org.codeberg.illusionclient.feature.config.api.Option;
import org.codeberg.illusionclient.feature.module.visual.Notifications;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.List;

public abstract class Module {
	protected static final float MS_PER_TICK = 50.0F;

	public final String name;
	public final String key;
	public final Category category;
	public final List<String> aliases;
	public final boolean bindable;
	public final boolean toggleable;
	public final Description description;
	public final ObsHidden obsHidden = getClass().getAnnotation(ObsHidden.class);
	public final List<Option> options = new ObjectArrayList<>();

	private int bind;
	private int namedBind;
	private String bindName;
	private boolean hold;
	private boolean enabled;
	private int detailsVersion;

	public Module() {
		this(null, null);
	}

	protected Module(String name, String key) {
		ModuleInfo info = getClass().getAnnotation(ModuleInfo.class);
		if (info == null) throw new IllegalStateException(getClass().getName() + " is missing @ModuleInfo");

		this.name = name == null ? info.name() : name;
		this.key = key == null ? getClass().getSimpleName() : key;
		this.category = info.category();
		this.aliases = List.of(info.aliases());
		this.description = getClass().getAnnotation(Description.class);
		this.bindable = info.bindable();
		this.toggleable = info.toggleable();
		this.bind = info.bind();

		for (Field field : getClass().getDeclaredFields()) {
			Option option = Option.of(this, field);
			if (option == null) continue;

			option.link(options);
			options.add(option);
		}
	}

	protected static int ticks(float ms) {
		return Math.round(ms / MS_PER_TICK);
	}

	protected static long secondsFromNow(float seconds) {
		return System.currentTimeMillis() + (long) (seconds * 1000.0F);
	}

	protected static String number(float value) {
		return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
	}

	public static int mouseBind(int button) {
		return button - 100;
	}

	public final int getBind() {
		return bind;
	}

	public void setBind(int bind) {
		if (!bindable) return;
		this.bind = bind;
		if (IllusionClient.CONFIG != null) IllusionClient.CONFIG.edited(this);
	}

	public final String bindName() {
		if (bind != namedBind || bindName == null) {
			namedBind = bind;
			bindName = bind < 0 ? "Mouse " + (bind + 101) : Keyboard.getKeyName(bind);
		}
		return bindName;
	}

	public final boolean isHold() {
		return hold;
	}

	public final void setHold(boolean hold) {
		this.hold = hold;
		if (IllusionClient.CONFIG != null) IllusionClient.CONFIG.edited(this);
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final void setEnabled(boolean enabled) {
		if (!toggleable || this.enabled == enabled) return;
		this.enabled = enabled;

		if (enabled) {
			IllusionClient.BUS.subscribe(this);
			onEnable();
		} else {
			IllusionClient.BUS.unsubscribe(this);
			onDisable();
		}
	}

	public final void toggle() {
		if (!toggleable) return;
		IllusionClient.STATS.toggled(this);
		boolean was = enabled;
		setEnabled(!was);
		if (enabled != was) IllusionClient.CONFIG.edited(this);
		Notifications.toggled(this);
	}

	public String details() {
		return null;
	}

	public final int detailsVersion() {
		return detailsVersion;
	}

	public final void detailsChanged() {
		detailsVersion++;
	}

	public void onEnable() {
	}

	public void onDisable() {
	}
}
