package org.codeberg.illusionclient.feature.ui;

import org.codeberg.illusionclient.feature.module.api.Description;

import java.util.List;

public final class Tooltip {
	private static final int MAX_WIDTH = 160;
	private static final int PADDING = 6;
	private static final int OFFSET = 10;

	private static String hovered, shown;
	private static boolean pinned;
	private static List<String> lines;
	private static int textWidth;
	private static Font shownFont;

	private Tooltip() {
	}

	public static void show(Description description) {
		if (!pinned) hovered = description == null ? null : description.value();
	}

	public static void pin(String text) {
		hovered = text;
		pinned = true;
	}

	public static void hide() {
		hovered = null;
		pinned = false;
	}

	public static void render(int mouseX, int mouseY, int screenWidth, int screenHeight) {
		String text = hovered;
		hovered = null;
		pinned = false;
		if (text == null) return;

		if (!text.equals(shown) || Draw.font() != shownFont) {
			shown = text;
			shownFont = Draw.font();
			lines = Draw.split(text, MAX_WIDTH, Draw.Weight.REGULAR);
			textWidth = 0;
			for (String line : lines) textWidth = Math.max(textWidth, Draw.width(line, Draw.Weight.REGULAR));
		}

		int width = textWidth + PADDING * 2;
		int height = (lines.size() - 1) * Draw.lineHeight() + Math.round(Draw.textHeight()) + PADDING * 2;
		int x = Math.clamp(mouseX + OFFSET, 0, Math.max(0, screenWidth - width));
		int y = mouseY + OFFSET + height > screenHeight ? Math.max(0, mouseY - OFFSET - height) : mouseY + OFFSET;

		Draw.rounded(x, y, width, height, 4.0F, Draw.TOOLTIP_INK);
		for (int i = 0; i < lines.size(); i++) {
			Draw.text(lines.get(i), x + PADDING, y + PADDING + i * Draw.lineHeight(), Draw.MUTED, Draw.Weight.REGULAR);
		}
	}
}
