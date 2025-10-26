/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.idp.cognito.service;

import com.firefly.idp.cognito.client.CognitoClientFactory;
import com.firefly.idp.cognito.properties.CognitoProperties;
import com.firefly.idp.cognito.util.CognitoSecretHashCalculator;
import com.firefly.idp.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for handling AWS Cognito user-related operations.
 * 
 * <p>Implements authentication flows including:
 * <ul>
 *   <li>User login (InitiateAuth)</li>
 *   <li>Token refresh</li>
 *   <li>Logout (GlobalSignOut)</li>
 *   <li>Token introspection</li>
 *   <li>User info retrieval</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CognitoUserService {

    private final CognitoClientFactory clientFactory;
    private final CognitoProperties properties;

    /**
     * Authenticate user with username and password
     */
    public Mono<ResponseEntity<TokenResponse>> login(LoginRequest request) {
        return Mono.<ResponseEntity<TokenResponse>>fromCallable(() -> {
            log.info("Initiating Cognito login for user: {}", request.getUsername());
            
            CognitoIdentityProviderClient client = clientFactory.getClient();
            
            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", request.getUsername());
            authParams.put("PASSWORD", request.getPassword());
            
            // Add SECRET_HASH if client secret is configured
            if (properties.getClientSecret() != null && !properties.getClientSecret().isEmpty()) {
                String secretHash = CognitoSecretHashCalculator.calculateSecretHash(
                        properties.getClientId(),
                        properties.getClientSecret(),
                        request.getUsername()
                );
                authParams.put("SECRET_HASH", secretHash);
            }
            
            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(properties.getClientId())
                    .authParameters(authParams)
                    .build();
            
            InitiateAuthResponse authResponse = client.initiateAuth(authRequest);
            
            AuthenticationResultType authResult = authResponse.authenticationResult();
            
            if (authResult == null) {
                log.error("Authentication failed: No authentication result returned");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            
            TokenResponse tokenResponse = TokenResponse.builder()
                    .accessToken(authResult.accessToken())
                    .refreshToken(authResult.refreshToken())
                    .idToken(authResult.idToken())
                    .tokenType(authResult.tokenType())
                    .expiresIn(authResult.expiresIn().longValue())
                    .build();
            
            log.info("Successfully authenticated user: {}", request.getUsername());
            return ResponseEntity.ok(tokenResponse);
            
        }).onErrorResume(exception -> {
            log.error("Cognito login failed for user: {}", request.getUsername(), exception);
            
            if (exception instanceof NotAuthorizedException) {
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<TokenResponse>build());
            } else if (exception instanceof UserNotFoundException) {
                return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).<TokenResponse>build());
            } else {
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<TokenResponse>build());
            }
        });
    }

    /**
     * Refresh access token using refresh token
     */
    public Mono<ResponseEntity<TokenResponse>> refresh(RefreshRequest request) {
        return Mono.fromCallable(() -> {
            log.debug("Refreshing Cognito token");
            
            CognitoIdentityProviderClient client = clientFactory.getClient();
            
            Map<String, String> authParams = new HashMap<>();
            authParams.put("REFRESH_TOKEN", request.getRefreshToken());
            
            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .clientId(properties.getClientId())
                    .authParameters(authParams)
                    .build();
            
            InitiateAuthResponse authResponse = client.initiateAuth(authRequest);
            AuthenticationResultType authResult = authResponse.authenticationResult();
            
            TokenResponse tokenResponse = TokenResponse.builder()
                    .accessToken(authResult.accessToken())
                    .refreshToken(request.getRefreshToken()) // Cognito doesn't return new refresh token
                    .idToken(authResult.idToken())
                    .tokenType(authResult.tokenType())
                    .expiresIn(authResult.expiresIn().longValue())
                    .build();
            
            log.debug("Successfully refreshed token");
            return ResponseEntity.ok(tokenResponse);
            
        }).onErrorResume(exception -> {
            log.error("Token refresh failed", exception);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        });
    }

    /**
     * Logout user (global sign out)
     */
    public Mono<Void> logout(LogoutRequest request) {
        return Mono.fromRunnable(() -> {
            log.info("Logging out user from Cognito");
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                GlobalSignOutRequest signOutRequest = GlobalSignOutRequest.builder()
                        .accessToken(request.getAccessToken())
                        .build();
                
                client.globalSignOut(signOutRequest);
                
                log.info("Successfully logged out user");
            } catch (Exception e) {
                log.error("Logout failed", e);
                throw new RuntimeException("Logout failed", e);
            }
        });
    }

    /**
     * Introspect access token
     */
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        return Mono.fromCallable(() -> {
            log.debug("Introspecting Cognito token");
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                GetUserRequest getUserRequest = GetUserRequest.builder()
                        .accessToken(accessToken)
                        .build();
                
                GetUserResponse getUserResponse = client.getUser(getUserRequest);
                
                IntrospectionResponse introspection = IntrospectionResponse.builder()
                        .active(true)
                        .username(getUserResponse.username())
                        .scope("openid profile email")
                        .build();
                
                return ResponseEntity.ok(introspection);
                
            } catch (NotAuthorizedException e) {
                log.debug("Token is not active or expired");
                IntrospectionResponse introspection = IntrospectionResponse.builder()
                        .active(false)
                        .build();
                return ResponseEntity.ok(introspection);
            }
        }).onErrorResume(exception -> {
            log.error("Token introspection failed", exception);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        });
    }

    /**
     * Get user information from access token
     */
    public Mono<ResponseEntity<UserInfoResponse>> getUserInfo(String accessToken) {
        return Mono.fromCallable(() -> {
            log.debug("Fetching Cognito user info");
            
            CognitoIdentityProviderClient client = clientFactory.getClient();
            
            GetUserRequest getUserRequest = GetUserRequest.builder()
                    .accessToken(accessToken)
                    .build();
            
            GetUserResponse getUserResponse = client.getUser(getUserRequest);
            
            // Extract user attributes
            Map<String, String> attributes = new HashMap<>();
            for (AttributeType attr : getUserResponse.userAttributes()) {
                attributes.put(attr.name(), attr.value());
            }
            
            UserInfoResponse userInfo = UserInfoResponse.builder()
                    .sub(attributes.getOrDefault("sub", getUserResponse.username()))
                    .preferredUsername(getUserResponse.username())
                    .email(attributes.get("email"))
                    .emailVerified(Boolean.parseBoolean(attributes.getOrDefault("email_verified", "false")))
                    .givenName(attributes.get("given_name"))
                    .familyName(attributes.get("family_name"))
                    .name(attributes.get("name"))
                    .build();
            
            log.debug("Successfully fetched user info for: {}", getUserResponse.username());
            return ResponseEntity.ok(userInfo);
            
        }).onErrorResume(exception -> {
            log.error("Failed to fetch user info", exception);
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        });
    }

    /**
     * Revoke refresh token
     */
    public Mono<Void> revokeRefreshToken(String refreshToken) {
        return Mono.fromRunnable(() -> {
            log.info("Revoking Cognito refresh token");
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                RevokeTokenRequest revokeRequest = RevokeTokenRequest.builder()
                        .token(refreshToken)
                        .clientId(properties.getClientId())
                        .build();
                
                if (properties.getClientSecret() != null) {
                    revokeRequest = revokeRequest.toBuilder()
                            .clientSecret(properties.getClientSecret())
                            .build();
                }
                
                client.revokeToken(revokeRequest);
                
                log.info("Successfully revoked refresh token");
            } catch (Exception e) {
                log.error("Failed to revoke refresh token", e);
                throw new RuntimeException("Token revocation failed", e);
            }
        });
    }
}
