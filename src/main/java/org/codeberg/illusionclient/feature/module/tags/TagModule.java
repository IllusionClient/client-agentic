package org.codeberg.illusionclient.feature.module.tags;

import org.codeberg.illusionclient.feature.config.api.MultiSelect;
import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;
import org.codeberg.illusionclient.feature.module.client.Tags;

import java.util.Set;
import java.util.TreeSet;

@Description("Click to toggle the friend tag on this player.")
@ModuleInfo(name = "Tag", category = Category.TAGS, bindable = false)
public class TagModule extends Module {
	@Description("The tags this player has. Turn them all off to forget the player.")
	@MultiSelect(value = "Tags", source = "tagNames")
	public Set<String> tags;

	public final String player;

	public TagModule(String player) {
		super(player, "Tag:" + player);
		this.player = player;
		this.tags = Tags.of(player);
	}

	public String[] tagNames() {
		return Tags.names().toArray(String[]::new);
	}

	public void update(Set<String> tags) {
		this.tags = tags;
		setEnabled(tags.contains(Tags.FRIEND));
	}

	public void save() {
		if (!tags.equals(Tags.of(player))) Tags.set(player, new TreeSet<>(tags));
	}

	@Override
	public void onEnable() {
		friend(true);
	}

	@Override
	public void onDisable() {
		friend(false);
	}

	private void friend(boolean friend) {
		if (tags.contains(Tags.FRIEND) == friend) return;
		Set<String> next = new TreeSet<>(tags);
		if (friend) next.add(Tags.FRIEND);
		else next.remove(Tags.FRIEND);
		Tags.set(player, next);
	}
}
