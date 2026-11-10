/**
 * Who is acting, and what they may do: the Permissions, the signed-in Account as the other
 * modules see it, and the annotation that guards a method with a Permission.
 *
 * <p>This module knows nothing about how Accounts are stored or signed in; that is the
 * {@code accounts} module. Keeping the two apart lets any module check Permissions without
 * depending on {@code accounts}.
 */
package io.github.oliverjuhasz.boardkeeper.access;
