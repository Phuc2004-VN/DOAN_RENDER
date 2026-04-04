package com.example.do_an_ck_J2EE.config;

import com.example.do_an_ck_J2EE.service.CustomOAuth2UserService;
import com.example.do_an_ck_J2EE.service.CustomOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/register", "/login",
                                "/forgot-password",
                                "/forgot-password/send-otp",
                                "/forgot-password/verify-otp",
                                "/oauth2/**",
                                "/css/**", "/js/**", "/images/**",
                                "/ws-chat/**", "/app/**", "/topic/**", "/user/**",
                                "/chat/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/cart/**").authenticated()
                        .requestMatchers("/order/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler((request, response, exception) -> {
                            if (exception instanceof LockedException) {
                                response.sendRedirect("/login?locked=true");
                            } else if (exception instanceof DisabledException) {
                                response.sendRedirect("/login?disabled=true");
                            } else {
                                response.sendRedirect("/login?error=true");
                            }
                        })
                        .permitAll()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler((request, response, exception) -> {
                            String errorMessage = getOauth2ErrorCode(exception);
                            response.sendRedirect("/login?oauth2error=" + errorMessage);
                        })
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService)
                        )
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    private String getOauth2ErrorCode(AuthenticationException exception) {
        String message = exception.getMessage();

        if (message != null) {
            String lower = message.toLowerCase();

            if (lower.contains("locked")) {
                return "locked";
            }
            if (lower.contains("disabled")) {
                return "disabled";
            }
            if (lower.contains("email")) {
                return "email";
            }
        }

        return "true";
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}