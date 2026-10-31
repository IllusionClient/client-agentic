package org.codeberg.illusionclient.feature.module.api;

import java.util.Locale;

public enum Category {
	CLIENT,
	COMBAT,
	MOVEMENT,
	PLAYER,
	VISUAL,
	PROFILES;

	public String title() {
		return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
	}
}
