package com.elandinnople.loadpilot.common.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class KeycloakService {

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakService(
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.resource}") String clientId,
            @Value("${keycloak.credentials.secret}") String clientSecret) {

        this.realm = realm;
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }

    public UserRepresentation getUserByKeycloakId(String keycloakId) {
        try {
            return keycloak.realm(realm).users().get(keycloakId).toRepresentation();
        } catch (Exception e) {
            log.error("Error fetching user from Keycloak: {}", e.getMessage());
            return null;
        }
    }

    public List<RoleRepresentation> getUserRoles(String keycloakId) {
        try {
            return keycloak.realm(realm).users().get(keycloakId).roles().realmLevel().listAll();
        } catch (Exception e) {
            log.error("Error fetching user roles from Keycloak: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
