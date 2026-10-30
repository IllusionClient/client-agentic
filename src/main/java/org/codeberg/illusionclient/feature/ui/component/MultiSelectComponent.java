package org.codeberg.illusionclient.feature.ui.component;

import org.codeberg.illusionclient.feature.config.api.Option;
import org.codeberg.illusionclient.feature.ui.Draw;

import java.util.LinkedHashSet;
import java.util.Set;

public class MultiSelectComponent extends OptionComponent {
	private boolean open;

	public MultiSelectComponent(Option option) {
		super(option);
	}

	@Override
	public int height() {
		return open ? HEIGHT * (option.choices().length + 1) : HEIGHT;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		row(option.name, mouseX, mouseY);
		Set<String> selected = option.get();
		value(open ? "-" : switch (selected.size()) {
			case 0 -> "None";
			case 1 -> selected.iterator().next();
			default -> selected.size() + " selected";
		});
		if (!open) return;

		String[] choices = option.choices();
		for (int i = 0; i < choices.length; i++) {
			int rowY = y + HEIGHT * (i + 1);
			boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= rowY && mouseY < rowY + HEIGHT;
			Draw.rounded(x, rowY, width, HEIGHT, 0.0F, bottom && i == choices.length - 1 ? RADIUS : 0.0F, hovered ? Draw.CONTAINER_HIGH : Draw.CONTAINER);
			Draw.text(choices[i], left() + indent + PADDING, Draw.textY(rowY, HEIGHT), Draw.MUTED);
			CheckComponent.drawSwitch(right() - CheckComponent.SWITCH_WIDTH, rowY + (HEIGHT - CheckComponent.SWITCH_HEIGHT) / 2, selected.contains(choices[i]));
		}
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int button) {
		if (button > 1 || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height()) return;

		int index = (mouseY - y) / HEIGHT - 1;
		if (index < 0) {
			open = !open;
			return;
		}
		if (button != 0) return;

		String choice = option.choices()[index];
		Set<String> selected = new LinkedHashSet<>(option.<Set<String>>get());
		if (!selected.remove(choice)) selected.add(choice);
		option.set(selected);
	}
}
