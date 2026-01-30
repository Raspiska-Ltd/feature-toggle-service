package io.raspiska.featuretoggle.client;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(FeatureToggleAutoConfiguration.class)
public @interface EnableFeatureToggle {
}
