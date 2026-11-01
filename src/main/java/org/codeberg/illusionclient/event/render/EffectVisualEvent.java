package org.codeberg.illusionclient.event.render;

import net.minecraft.entity.living.effect.StatusEffect;

public final class EffectVisualEvent {
	private static final EffectVisualEvent INSTANCE = new EffectVisualEvent();
	public StatusEffect effect;
	public boolean visible;

	private EffectVisualEvent() {
	}

	public static EffectVisualEvent of(StatusEffect effect, boolean visible) {
		INSTANCE.effect = effect;
		INSTANCE.visible = visible;
		return INSTANCE;
	}
}
