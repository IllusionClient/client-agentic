package org.codeberg.illusionclient.feature.config.api;

import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;

import java.awt.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Option {
	public final String name;
	public final String key;
	public final Annotation info;
	public final Description description;
	private final Object owner;
	private final Field field;
	private final Method source;
	private final List<Requirement> requirements = new ArrayList<>();
	private String section;
	private int depth;

	private Option(Object owner, Field field, Annotation info, String name, Class<?> type) {
		if (field.getType() != type) {
			throw new IllegalStateException("@" + info.annotationType().getSimpleName() + " on "
				+ field.getDeclaringClass().getName() + "." + field.getName()
				+ " needs a " + type.getSimpleName() + " field, not " + field.getType().getSimpleName());
		}

		this.name = name;
		this.key = field.getName();
		this.info = info;
		this.description = field.getAnnotation(Description.class);
		this.owner = owner;
		this.field = field;
		String source = info instanceof Combo combo ? combo.source() : info instanceof MultiSelect multi ? multi.source() : "";
		this.source = source.isEmpty() ? null : source(owner, source);
		field.setAccessible(true);
	}

	private static Method source(Object owner, String name) {
		try {
			Method method = owner.getClass().getMethod(name);
			if (method.getReturnType() != String[].class) throw new IllegalStateException(name + " must return String[]");
			return method;
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Option source " + owner.getClass().getName() + "." + name + "() not found", e);
		}
	}

	public static Option of(Object owner, Field field) {
		Check check = field.getAnnotation(Check.class);
		if (check != null) return new Option(owner, field, check, check.value(), boolean.class);

		Slider slider = field.getAnnotation(Slider.class);
		if (slider != null) return new Option(owner, field, slider, slider.value(), float.class);

		RangeSlider range = field.getAnnotation(RangeSlider.class);
		if (range != null) return new Option(owner, field, range, range.value(), float[].class);

		Combo combo = field.getAnnotation(Combo.class);
		if (combo != null) return new Option(owner, field, combo, combo.value(), String.class);

		MultiSelect multi = field.getAnnotation(MultiSelect.class);
		if (multi != null) return new Option(owner, field, multi, multi.value(), Set.class);

		ColorPicker picker = field.getAnnotation(ColorPicker.class);
		if (picker != null) return new Option(owner, field, picker, picker.value(), Color.class);

		return null;
	}

	public void link(List<Option> earlier) {
		Section start = field.getAnnotation(Section.class);
		section = start != null ? start.value() : earlier.isEmpty() ? null : earlier.getLast().section;

		for (Requires requires : field.getAnnotationsByType(Requires.class)) {
			List<String> values = List.of(requires.is());
			Option required = null;
			for (Option option : earlier) {
				if (option.key.equals(requires.value())) required = option;
			}

			boolean valid = required != null && (values.isEmpty()
				? required.info instanceof Check || required.info instanceof Slider
				: List.of(required.options()).containsAll(values));
			if (!valid) {
				throw new IllegalStateException("@Requires on " + field.getDeclaringClass().getName() + "." + field.getName()
					+ " needs " + requires.value() + " to be an earlier @Check or @Slider, or an earlier @Combo or @MultiSelect offering " + values);
			}
			requirements.add(new Requirement(required, values));
			depth = Math.max(depth, required.depth + 1);
		}
	}

	public String section() {
		return section;
	}

	public int depth() {
		return depth;
	}

	public boolean visible() {
		for (Requirement requirement : requirements) {
			if (!requirement.met()) return false;
		}
		return true;
	}

	private String[] options() {
		return switch (info) {
			case Combo combo -> combo.options();
			case MultiSelect multi -> multi.options();
			default -> new String[0];
		};
	}

	public String[] choices() {
		if (source == null) return options();
		try {
			return (String[]) source.invoke(owner);
		} catch (IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T get() {
		try {
			return (T) field.get(owner);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public boolean getBoolean() {
		try {
			return field.getBoolean(owner);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public float getFloat() {
		try {
			return field.getFloat(owner);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	public void set(Object value) {
		try {
			field.set(owner, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		changed();
	}

	public void changed() {
		if (owner instanceof Module module) module.detailsChanged();
	}

	private record Requirement(Option option, List<String> values) {
		private boolean met() {
			if (!option.visible()) return false;
			if (option.info instanceof MultiSelect) return values.stream().anyMatch(option.<Set<String>>get()::contains);
			if (!values.isEmpty()) return values.contains(option.<String>get());
			return option.info instanceof Slider ? option.<Float>get() > 0.0F : option.getBoolean();
		}
	}
}
