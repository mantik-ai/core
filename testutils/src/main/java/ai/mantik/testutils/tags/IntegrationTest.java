package ai.mantik.testutils.tags;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to mark Integration Tests.
 * They are excluded from running by default.
 *
 * See http://www.scalatest.org/user_guide/tagging_your_tests
 */
@org.scalatest.TagAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface IntegrationTest {}
