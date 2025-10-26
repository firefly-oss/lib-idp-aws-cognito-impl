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

package com.firefly.idp.cognito.adapter;

import com.firefly.idp.cognito.service.CognitoAdminService;
import com.firefly.idp.cognito.service.CognitoUserService;
import com.firefly.idp.dtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CognitoIdpAdapter.
 * 
 * <p>Tests the adapter's delegation to user and admin services
 * without requiring actual AWS Cognito connectivity.
 */
@ExtendWith(MockitoExtension.class)
class CognitoIdpAdapterTest {

    @Mock
    private CognitoUserService userService;

    @Mock
    private CognitoAdminService adminService;

    private CognitoIdpAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CognitoIdpAdapter(userService, adminService);
    }

    @Test
    void testLogin_Success() {
        // Arrange
        LoginRequest request = LoginRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .idToken("id-token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();

        when(userService.login(any(LoginRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(tokenResponse)));

        // Act & Assert
        StepVerifier.create(adapter.login(request))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK &&
                                response.getBody() != null &&
                                response.getBody().getAccessToken().equals("access-token"))
                .verifyComplete();

        verify(userService).login(request);
    }

    @Test
    void testRefresh_Success() {
        // Arrange
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("refresh-token")
                .build();

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600L)
                .build();

        when(userService.refresh(any(RefreshRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(tokenResponse)));

        // Act & Assert
        StepVerifier.create(adapter.refresh(request))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK &&
                                response.getBody() != null &&
                                response.getBody().getAccessToken().equals("new-access-token"))
                .verifyComplete();

        verify(userService).refresh(request);
    }

    @Test
    void testLogout_Success() {
        // Arrange
        LogoutRequest request = LogoutRequest.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();

        when(userService.logout(any(LogoutRequest.class)))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(adapter.logout(request))
                .verifyComplete();

        verify(userService).logout(request);
    }

    @Test
    void testIntrospect_Active() {
        // Arrange
        IntrospectionResponse introspection = IntrospectionResponse.builder()
                .active(true)
                .username("testuser")
                .build();

        when(userService.introspect(anyString()))
                .thenReturn(Mono.just(ResponseEntity.ok(introspection)));

        // Act & Assert
        StepVerifier.create(adapter.introspect("access-token"))
                .expectNextMatches(response ->
                        response.getBody() != null &&
                                response.getBody().isActive())
                .verifyComplete();

        verify(userService).introspect("access-token");
    }

    @Test
    void testGetUserInfo_Success() {
        // Arrange
        UserInfoResponse userInfo = UserInfoResponse.builder()
                .sub("user-123")
                .preferredUsername("testuser")
                .email("test@example.com")
                .emailVerified(true)
                .build();

        when(userService.getUserInfo(anyString()))
                .thenReturn(Mono.just(ResponseEntity.ok(userInfo)));

        // Act & Assert
        StepVerifier.create(adapter.getUserInfo("access-token"))
                .expectNextMatches(response ->
                        response.getBody() != null &&
                                response.getBody().getEmail().equals("test@example.com"))
                .verifyComplete();

        verify(userService).getUserInfo("access-token");
    }

    @Test
    void testCreateUser_Success() {
        // Arrange
        CreateUserRequest request = CreateUserRequest.builder()
                .username("newuser")
                .email("newuser@example.com")
                .password("password123")
                .build();

        CreateUserResponse response = CreateUserResponse.builder()
                .id("newuser")
                .username("newuser")
                .email("newuser@example.com")
                .build();

        when(adminService.createUser(any(CreateUserRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(response)));

        // Act & Assert
        StepVerifier.create(adapter.createUser(request))
                .expectNextMatches(resp ->
                        resp.getBody() != null &&
                                resp.getBody().getUsername().equals("newuser"))
                .verifyComplete();

        verify(adminService).createUser(request);
    }

    @Test
    void testChangePassword_Success() {
        // Arrange
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .userId("testuser")
                .oldPassword("oldpass")
                .newPassword("newpass")
                .build();

        when(adminService.changePassword(any(ChangePasswordRequest.class)))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(adapter.changePassword(request))
                .verifyComplete();

        verify(adminService).changePassword(request);
    }

    @Test
    void testGetRoles_Success() {
        // Arrange
        List<String> roles = Arrays.asList("admin", "user");

        when(adminService.getRoles(anyString()))
                .thenReturn(Mono.just(ResponseEntity.ok(roles)));

        // Act & Assert
        StepVerifier.create(adapter.getRoles("testuser"))
                .expectNextMatches(response ->
                        response.getBody() != null &&
                                response.getBody().contains("admin"))
                .verifyComplete();

        verify(adminService).getRoles("testuser");
    }

    @Test
    void testAssignRolesToUser_Success() {
        // Arrange
        AssignRolesRequest request = AssignRolesRequest.builder()
                .userId("testuser")
                .roleNames(Arrays.asList("admin", "user"))
                .build();

        when(adminService.assignRolesToUser(any(AssignRolesRequest.class)))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(adapter.assignRolesToUser(request))
                .verifyComplete();

        verify(adminService).assignRolesToUser(request);
    }

    @Test
    void testDeleteUser_Success() {
        // Arrange
        when(adminService.deleteUser(anyString()))
                .thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(adapter.deleteUser("testuser"))
                .verifyComplete();

        verify(adminService).deleteUser("testuser");
    }
}
