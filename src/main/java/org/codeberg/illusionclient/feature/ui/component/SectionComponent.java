package org.codeberg.illusionclient.feature.ui.component;

import org.codeberg.illusionclient.feature.ui.Draw;

public class SectionComponent extends Group {
	final String name;

	public SectionComponent(String name) {
		this.name = name;
		this.open = true;
	}

	@Override
	public boolean visible() {
		for (Component child : children) {
			if (child.visible()) return true;
		}
		return false;
	}

	@Override
	protected void renderHeader(int mouseX, int mouseY) {
		Draw.rounded(x, y, width, HEIGHT, 0.0F, headerRadius(), hovered(mouseX, mouseY) ? Draw.CONTAINER_HIGH : Draw.CONTAINER);
		Draw.rect(left(), y, right() - left(), 2, Draw.DIVIDER);
		Draw.text(name, left(), Draw.textY(y, HEIGHT), Draw.PRIMARY, Draw.Weight.MEDIUM);
		if (!open) value("+");
	}

	@Override
	protected void headerClicked(int mouseX, int mouseY, int button) {
		if (button <= 1) open = !open;
	}
}
