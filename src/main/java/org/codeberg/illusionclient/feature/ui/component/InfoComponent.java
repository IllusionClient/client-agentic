package org.codeberg.illusionclient.feature.ui.component;

import java.util.function.Supplier;

public class InfoComponent extends Component {
	private final String label;
	private final Supplier<String> value;

	public InfoComponent(String label, Supplier<String> value) {
		this.label = label;
		this.value = value;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		row(label, mouseX, mouseY);
		value(value.get());
	}
}
