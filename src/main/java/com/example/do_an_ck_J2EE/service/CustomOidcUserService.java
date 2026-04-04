package com.example.do_an_ck_J2EE.service;

import com.example.do_an_ck_J2EE.entity.User;
import com.example.do_an_ck_J2EE.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUserService delegate = new OidcUserService();
        OidcUser oidcUser = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());

        String email = getClaimAsString(claims, StandardClaimNames.EMAIL);
        String fullName = getClaimAsString(claims, StandardClaimNames.NAME);
        String providerId = getClaimAsString(claims, StandardClaimNames.SUB);

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email"),
                    "Không lấy được email từ tài khoản " + registrationId
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewSocialUser(email, fullName, registrationId, providerId));

        checkUserStatus(user);

        claims.put("localUsername", user.getUsername());
        claims.put("localEmail", user.getEmail());
        claims.put("localFullName", user.getFullName());
        claims.put("localRole", user.getRole());

        OidcUserInfo userInfo = new OidcUserInfo(claims);

        return new DefaultOidcUser(
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole())),
                oidcUser.getIdToken(),
                userInfo,
                "localUsername"
        );
    }

    private void checkUserStatus(User user) {
        if (Boolean.TRUE.equals(user.getLocked())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("locked"),
                    "Tài khoản social của bạn đang bị khóa"
            );
        }

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("disabled"),
                    "Tài khoản social của bạn đã bị vô hiệu hóa"
            );
        }
    }

    private User createNewSocialUser(String email, String fullName, String registrationId, String providerId) {
        String username = generateUniqueUsername(email, registrationId, providerId);

        User newUser = User.builder()
                .fullName((fullName != null && !fullName.isBlank()) ? fullName : username)
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("ROLE_USER")
                .enabled(true)
                .locked(false)
                .provider(registrationId.toUpperCase())
                .build();

        return userRepository.save(newUser);
    }

    private String generateUniqueUsername(String email, String registrationId, String providerId) {
        String base = email.split("@")[0]
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase();

        if (base.isBlank()) {
            base = registrationId + "user";
        }

        String username = base;
        int count = 1;

        while (userRepository.existsByUsername(username)) {
            if (providerId != null && !providerId.isBlank()) {
                username = base + "_" + registrationId + "_" + providerId.substring(0, Math.min(6, providerId.length()));
            } else {
                username = base + "_" + count;
                count++;
            }
        }

        return username;
    }

    private String getClaimAsString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value != null ? value.toString() : null;
    }
}