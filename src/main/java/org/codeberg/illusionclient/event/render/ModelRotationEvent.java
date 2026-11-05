package org.codeberg.illusionclient.event.render;

import net.minecraft.entity.living.LivingEntity;

public final class ModelRotationEvent {
	private static final ModelRotationEvent INSTANCE = new ModelRotationEvent();
	public LivingEntity entity;
	public boolean pre;
	public boolean swapped;

	private ModelRotationEvent() {
	}

	public static ModelRotationEvent of(LivingEntity entity, boolean pre) {
		INSTANCE.entity = entity;
		INSTANCE.pre = pre;
		INSTANCE.swapped = false;
		return INSTANCE;
	}
}
