package org.codeberg.illusionclient.event.api;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;

final class PubSubImpl<Event> implements PubSub<Event> {

	private static final Object GLOBAL_LISTENERS_BACKING_OBJECT = new Object();
	private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
	private static final MethodType LISTENER_INVOKE_TYPE = MethodType.methodType(void.class, Object.class);
	private final Map<Class<?>, List<Listener<Event>>> eventTypeListenerCache = new HashMap<>();
	private final Map<Object, List<TypedListener<Event>>> subscriberMap = new HashMap<>();
	private final Consumer<String> logger;

	PubSubImpl(Consumer<String> logger) {
		this.logger = logger;
	}

	@Override
	public <T extends Event> void subscribe(
		Class<T> event,
		Listener<T> listener
	) {
		this.register(GLOBAL_LISTENERS_BACKING_OBJECT, event, e -> listener.invoke(event.cast(e)));
	}

	@Override
	public void subscribe(Object subscriber) {
		var subscriberClass = subscriber.getClass();
		MethodType factoryType = null;
		for (var method : subscriberClass.getDeclaredMethods()) {
			if (!method.isAnnotationPresent(Listen.class)) continue;

			int mm = method.getModifiers();
			if (!Modifier.isPublic(mm)) {
				this.error(method, "Methods marked with `@Listen` must be public.");
				continue;
			}
			if (Modifier.isStatic(mm)) {
				this.error(method, "Methods marked with `@Listen` must be non-static.");
				continue;
			}
			if (Modifier.isAbstract(mm)) {
				this.error(method, "Methods marked with `@Listen` must be non-abstract.");
				continue;
			}
			if (Modifier.isNative(mm)) {
				this.error(method, "Methods marked with `@Listen` must be non-native.");
				continue;
			}
			if (method.getReturnType() != void.class) {
				this.error(method, "Methods marked with `@Listen` must return `void`.");
				continue;
			}

			if (method.getParameterCount() != 1) {
				this.error(method, "methods marked with `@Listen` must have only a single parameter.");
				continue;
			}

			var event = method.getParameterTypes()[0];

			try {
				if (factoryType == null) factoryType = MethodType.methodType(RawListener.class, subscriberClass);
				var site = LambdaMetafactory.metafactory(LOOKUP, "invoke",
					factoryType,
					LISTENER_INVOKE_TYPE,
					LOOKUP.unreflect(method),
					MethodType.methodType(void.class, event));
				var listener = (RawListener) site.getTarget().invoke(subscriber);
				this.register(subscriber, event, listener::invoke);
			} catch (IllegalAccessException _) {
				// this should not happen
			} catch (ClassCastException _) {
				this.error(method, "the parameter of methods marked `@Listen` must be a subclass of the PubSub<Event> `Event` type parameter.");
			} catch (Throwable e) {
				this.error("ERROR: " + e.getCause());
			}
		}
	}

	@Override
	public void unsubscribe(Object subscriber) {
		var removed = this.subscriberMap.remove(subscriber);
		if (removed == null) return;

		for (var typedListener : removed) {
			var listeners = this.eventTypeListenerCache.get(typedListener.type());
			if (listeners == null) continue;

			var updated = new ArrayList<>(listeners);
			if (!updated.remove(typedListener.listener())) continue;

			if (updated.isEmpty()) {
				this.eventTypeListenerCache.remove(typedListener.type());
			} else {
				this.eventTypeListenerCache.put(typedListener.type(), updated);
			}
		}
	}

	@Override
	public void publish(Event event) {
		var listeners = this.eventTypeListenerCache.get(event.getClass());

		if (listeners == null) return;

		for (var listener : listeners.reversed()) {
			listener.invoke(event);
		}
	}

	@Override
	public void clear() {
		this.subscriberMap.clear();
		this.eventTypeListenerCache.clear();
	}

	private void register(Object subscriber, Class<?> event, Listener<Event> listener) {
		var updated = new ArrayList<>(this.eventTypeListenerCache.getOrDefault(event, List.of()));
		updated.add(listener);
		this.eventTypeListenerCache.put(event, updated);

		this.subscriberMap.computeIfAbsent(subscriber, _ -> new ArrayList<>()).add(new TypedListener<>(event, listener));
	}

	private void error(Method method, String reason) {
		this.error("ERROR: " + method.getDeclaringClass().getSimpleName() + '#' + method.getName() + ": " + reason);
	}

	private void error(String msg) {
		this.logger.accept(msg);
	}

	@FunctionalInterface
	private interface RawListener {

		void invoke(Object event);
	}

	private record TypedListener<E>(Class<?> type, Listener<E> listener) {
	}
}
