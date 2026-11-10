package io.github.oliverjuhasz.boardkeeper.access.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;

/** Switches on security annotations on methods, such as {@code @RequiresPermission}. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class MethodSecurityConfiguration {

    /** Lets an annotation like {@code @RequiresPermission} pass its value into {@code @PreAuthorize}. */
    @Bean
    static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }
}
