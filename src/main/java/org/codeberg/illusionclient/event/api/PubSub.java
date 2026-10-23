package org.codeberg.illusionclient.event.api;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;

public final class PubSub<Event> {

	private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
	private static final MethodType LISTENER_INVOKE_TYPE = MethodType.methodType(void.class, Object.class);
	private final Map<Class<?>, List<Consumer<Object>>> eventTypeListenerCache = new HashMap<>();
	private final Map<Object, List<TypedListener>> subscriberMap = new HashMap<>();
	private final Consumer<String> logger;

	public PubSub(Consumer<String> logger) {
		this.logger = logger;
	}

	@SuppressWarnings("unchecked")
	public void subscribe(Object subscriber) {
		var subscriberClass = subscriber.getClass();
		MethodType factoryType = null;
		for (var method : subscriberClass.getDeclaredMethods()) {
			if (!method.isAnnotationPresent(Listen.class)) continue;

			int mm = method.getModifiers();
			if (!Modifier.isPublic(mm) || Modifier.isStatic(mm) || method.getReturnType() != void.class || method.getParameterCount() != 1) {
				this.error(method, "Methods marked with `@Listen` must be public, non-static, return `void` and take one parameter.");
				continue;
			}

			var event = method.getParameterTypes()[0];

			try {
				if (factoryType == null) factoryType = MethodType.methodType(Consumer.class, subscriberClass);
				var site = LambdaMetafactory.metafactory(LOOKUP, "accept",
					factoryType,
					LISTENER_INVOKE_TYPE,
					LOOKUP.unreflect(method),
					MethodType.methodType(void.class, event));
				this.register(subscriber, event, (Consumer<Object>) site.getTarget().invoke(subscriber));
			} catch (Throwable e) {
				this.error(method, e.toString());
			}
		}
	}

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

	public void publish(Event event) {
		var listeners = this.eventTypeListenerCache.get(event.getClass());

		if (listeners == null) return;

		for (var listener : listeners.reversed()) {
			listener.accept(event);
		}
	}

	private void register(Object subscriber, Class<?> event, Consumer<Object> listener) {
		var updated = new ArrayList<>(this.eventTypeListenerCache.getOrDefault(event, List.of()));
		updated.add(listener);
		this.eventTypeListenerCache.put(event, updated);

		this.subscriberMap.computeIfAbsent(subscriber, _ -> new ArrayList<>()).add(new TypedListener(event, listener));
	}

	private void error(Method method, String reason) {
		this.logger.accept("ERROR: " + method.getDeclaringClass().getSimpleName() + '#' + method.getName() + ": " + reason);
	}

	private record TypedListener(Class<?> type, Consumer<Object> listener) {
	}
}
