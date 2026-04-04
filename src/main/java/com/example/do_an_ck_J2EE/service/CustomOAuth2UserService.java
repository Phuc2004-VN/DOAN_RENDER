package com.example.do_an_ck_J2EE.service;

import com.example.do_an_ck_J2EE.entity.User;
import com.example.do_an_ck_J2EE.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oauth2User.getAttributes();

        String email = getEmail(attributes);
        String fullName = getName(registrationId, attributes);
        String providerId = getProviderId(attributes);

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email"),
                    "Không lấy được email từ tài khoản " + registrationId
            );
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createNewSocialUser(email, fullName, registrationId, providerId));

        checkUserStatus(user);

        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getRole())
        );

        Map<String, Object> customAttributes = new HashMap<>(attributes);
        customAttributes.put("localUsername", user.getUsername());
        customAttributes.put("localEmail", user.getEmail());
        customAttributes.put("localFullName", user.getFullName());
        customAttributes.put("localRole", user.getRole());

        return new DefaultOAuth2User(authorities, customAttributes, "localUsername");
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

    private String getEmail(Map<String, Object> attributes) {
        Object email = attributes.get("email");
        return email != null ? email.toString() : null;
    }

    private String getName(String registrationId, Map<String, Object> attributes) {
        Object name = attributes.get("name");
        if (name != null) {
            return name.toString();
        }

        Object firstName = attributes.get("first_name");
        Object lastName = attributes.get("last_name");

        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }

        if (firstName != null) {
            return firstName.toString();
        }

        return registrationId + "_user";
    }

    private String getProviderId(Map<String, Object> attributes) {
        Object sub = attributes.get("sub");
        if (sub != null) {
            return sub.toString();
        }

        Object id = attributes.get("id");
        if (id != null) {
            return id.toString();
        }

        return UUID.randomUUID().toString().replace("-", "");
    }
}