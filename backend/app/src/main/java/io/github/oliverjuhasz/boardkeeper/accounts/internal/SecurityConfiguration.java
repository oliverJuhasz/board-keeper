package io.github.oliverjuhasz.boardkeeper.accounts.internal;

import io.github.oliverjuhasz.boardkeeper.problems.ProblemResponses;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    /**
     * The rules every request passes through before it reaches a controller. Spring Security
     * builds a chain of filters from this.
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository contextRepository,
            CurrentAccounts currentAccounts,
            SignInSessions sessions)
            throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        // Open to everyone: asking who is signed in, signing in, the first-time setup,
                        // and registering with an Invite's link.
                        .requestMatchers("/api/session", "/api/setup", "/api/registration/*")
                        .permitAll()
                        // The pages shown before signing in need the Game Modules' texts too.
                        .requestMatchers(HttpMethod.GET, "/api/game-modules/texts/*")
                        .permitAll()
                        // The rest of the API is for signed-in Accounts only.
                        .requestMatchers("/api/**")
                        .authenticated()
                        // Everything else is open: the frontend's files, the health check, and the
                        // API's description with Swagger UI.
                        .anyRequest()
                        .permitAll())
                // Protection against cross-site request forgery, in the form a single-page app uses:
                // the server hands out a token in the XSRF-TOKEN cookie, and the frontend sends it
                // back in the X-XSRF-TOKEN header on every request that changes something. Another
                // website can make the browser send our cookies, but it cannot read them.
                .csrf(csrf -> csrf.spa())
                .securityContext(context -> context.securityContextRepository(contextRepository))
                // Signing in and out are our own endpoints (SessionController), not Spring's pages.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // An API call without a sign-in gets "401 Unauthorized", not a redirect to a sign-in
                // page, and one without the needed Permission gets "403 Forbidden". Both carry a code,
                // like every other error.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                ProblemResponses.write(response, HttpStatus.UNAUTHORIZED, "access.notSignedIn"))
                        .accessDeniedHandler((request, response, exception) ->
                                ProblemResponses.write(response, HttpStatus.FORBIDDEN, "access.denied")))
                .addFilterBefore(new AccountRefreshFilter(currentAccounts, sessions), AuthorizationFilter.class);
        return http.build();
    }

    /** Where the "who is signed in" of a request is kept: in the request itself and in the session. */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    /**
     * Hashes passwords with Argon2, the current recommendation. Each stored hash starts with the
     * name of the algorithm, such as {@code {argon2}}, so a better one can be added later and
     * old passwords still verify.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        String current = "argon2";
        return new DelegatingPasswordEncoder(
                current, Map.of(current, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    /** Checks a username and password. Used by {@link SessionController}. */
    @Bean
    AuthenticationManager authenticationManager(AccountRepository accounts, PasswordEncoder passwordEncoder) {
        UserDetailsService byUsername = username -> accounts.findCredentials(username)
                .map(credentials -> User.withUsername(credentials.accountId().toString())
                        .password(credentials.passwordHash())
                        .disabled(credentials.disabled())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));

        var provider = new DaoAuthenticationProvider(byUsername);
        provider.setPasswordEncoder(passwordEncoder);
        // By default a disabled Account is reported before the password is even looked at,
        // which would tell a stranger that the Account exists. Check it after the password instead.
        provider.setPreAuthenticationChecks(user -> {});
        provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
        return new ProviderManager(provider);
    }

    /**
     * Writes the session cookie. Spring Session's defaults already make it invisible to scripts
     * (HttpOnly), not sent along from other websites (SameSite=Lax), and HTTPS-only whenever the
     * request came in over HTTPS. On top of that, the cookie outlives the browser being closed
     * when "Stay signed in" was chosen.
     */
    @Bean
    CookieSerializer sessionCookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setRememberMeRequestAttribute(SignInSessions.STAY_SIGNED_IN_ATTRIBUTE);
        return serializer;
    }
}
