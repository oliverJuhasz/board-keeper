package io.github.oliverjuhasz.boardkeeper.access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Lets a method be called only by an Account that holds the given Permission. Anyone else
 * gets "403 Forbidden".
 *
 * <p>This is a shorthand for Spring Security's {@code @PreAuthorize}: Spring fills the
 * annotation's value into the expression below, so
 * {@code @RequiresPermission(Permission.CREATE_PLAYERS)} means
 * {@code @PreAuthorize("hasAuthority('CREATE_PLAYERS')")}, without the name being typed as text.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAuthority('{value}')")
public @interface RequiresPermission {

    Permission value();
}
