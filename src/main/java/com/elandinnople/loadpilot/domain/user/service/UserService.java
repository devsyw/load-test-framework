package com.elandinnople.loadpilot.domain.user.service;

import com.amazonaws.services.ecs.model.ResourceNotFoundException;
import com.elandinnople.loadpilot.common.service.KeycloakService;
import com.elandinnople.loadpilot.domain.user.entity.User;
import com.elandinnople.loadpilot.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    @Transactional
    public User findOrCreateUserByKeycloakId(String keycloakId, Jwt jwt) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> createUserFromKeycloak(keycloakId, jwt));
    }

    @Transactional(readOnly = true)
    public User findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private User createUserFromKeycloak(String keycloakId, Jwt jwt) {
        // JWT에서 사용자 정보 추출
        String username = (String) jwt.getClaims().get("preferred_username");
        String email = (String) jwt.getClaims().get("email");

        // Keycloak에서 추가 정보 가져오기
        UserRepresentation userRepresentation = keycloakService.getUserByKeycloakId(keycloakId);
        if (userRepresentation != null) {
            // Keycloak에서 얻은 정보로 업데이트
            if (username == null) {
                username = userRepresentation.getUsername();
            }
            if (email == null) {
                email = userRepresentation.getEmail();
            }
        }

        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(username);
        user.setEmail(email);

        return userRepository.save(user);
    }
}