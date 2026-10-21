package org.codeberg.illusionclient.feature.ui.component;

public class ButtonComponent extends Component {
	private final String label;
	private final String action;
	private final Runnable onClick;

	public ButtonComponent(String label, String action, Runnable onClick) {
		this.label = label;
		this.action = action;
		this.onClick = onClick;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		row(label, mouseX, mouseY);
		ModuleComponent.drawKey(right() - ModuleComponent.keyWidth(action), y + (HEIGHT - ModuleComponent.KEY_HEIGHT) / 2, action, false, false, hovered(mouseX, mouseY));
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0 && hovered(mouseX, mouseY)) onClick.run();
	}
}
