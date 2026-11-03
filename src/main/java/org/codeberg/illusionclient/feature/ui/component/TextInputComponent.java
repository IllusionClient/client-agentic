package org.codeberg.illusionclient.feature.ui.component;

import org.codeberg.illusionclient.feature.ui.Draw;
import org.codeberg.illusionclient.feature.ui.TextField;
import org.lwjgl.input.Keyboard;

import java.util.function.Predicate;

public class TextInputComponent extends Component {
	private static final int MAX_LENGTH = 32;
	private static final int BOX_HEIGHT = 16;
	private static TextInputComponent focused;

	private final TextField field = new TextField(MAX_LENGTH);
	private final String label;
	private final String placeholder;
	private final Predicate<String> onSubmit;

	public TextInputComponent(String label, String placeholder, Predicate<String> onSubmit) {
		this.label = label;
		this.placeholder = placeholder;
		this.onSubmit = onSubmit;
	}

	public static void blur() {
		if (focused != null) focused.field.blur();
		focused = null;
	}

	public static boolean typed(char chr, int key) {
		if (focused == null) return false;

		TextInputComponent input = focused;
		if (key == Keyboard.KEY_ESCAPE) blur();
		else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) input.submit();
		else input.field.keyPressed(chr, key);
		return true;
	}

	public void submit() {
		if (!onSubmit.test(field.text())) return;
		field.clear();
		if (focused == this) focused = null;
	}

	private int boxLeft() {
		return left() + Draw.width(label) + PADDING;
	}

	@Override
	public void render(int mouseX, int mouseY) {
		row(label, mouseX, mouseY);
		int boxLeft = boxLeft();
		int boxY = y + (HEIGHT - BOX_HEIGHT) / 2;
		Draw.rounded(boxLeft, boxY, right() - boxLeft, BOX_HEIGHT, 4.0F, Draw.CONTAINER_HIGHEST);
		if (focused == this) Draw.roundedOutline(boxLeft, boxY, right() - boxLeft, BOX_HEIGHT, 4.0F, 1.0F, Draw.accent().getRGB());
		field.render(boxLeft + 5, Draw.textY(y, HEIGHT), right() - boxLeft - 10, placeholder, mouseX);
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int button) {
		if (button != 0 || !hovered(mouseX, mouseY) || mouseX < boxLeft()) return;
		focused = this;
		field.mouseClicked(mouseX, boxLeft() + 5);
	}

	@Override
	public void mouseReleased() {
		field.mouseReleased();
	}
}
