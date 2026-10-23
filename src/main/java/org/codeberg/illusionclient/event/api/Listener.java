package org.codeberg.illusionclient.event.api;

@FunctionalInterface
public interface Listener<Event> {

	void invoke(Event event);
}
