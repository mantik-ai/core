package ai.mantik.executor.testutils;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

/**
 * An annotation to mark Tests which need kubernetes
 * for running. They are excluded from running by default.
 */
@org.scalatest.TagAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface KubernetesIntegrationTest {}
