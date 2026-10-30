package org.codeberg.illusionclient.feature.module.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObsHidden {
	Level value() default Level.YES;

	String unless() default "";

	enum Level {
		YES, PARTIAL, NO
	}
}
