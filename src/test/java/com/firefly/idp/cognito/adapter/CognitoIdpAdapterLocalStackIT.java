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

import com.firefly.idp.cognito.client.CognitoClientFactory;
import com.firefly.idp.cognito.properties.CognitoProperties;
import com.firefly.idp.cognito.service.CognitoAdminService;
import com.firefly.idp.cognito.service.CognitoUserService;
import com.firefly.idp.dtos.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.COGNITO;

/**
 * Integration tests for CognitoIdpAdapter using LocalStack
 * 
 * <p>These tests run against a real LocalStack Cognito instance to verify
 * the adapter works with actual AWS SDK calls.
 * 
 * <p><strong>Note:</strong> LocalStack's Cognito emulation has limitations:
 * - Some operations may not be fully supported
 * - Behavior may differ slightly from AWS Cognito
 * - Tests marked as {@code @Disabled} are known to have issues with LocalStack
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
@Disabled("Enable manually for LocalStack integration testing - requires Docker")
class CognitoIdpAdapterLocalStackIT {

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "TestPass123!";
    private static final String TEST_EMAIL = "testuser@example.com";

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(COGNITO);

    private static CognitoIdentityProviderClient cognitoClient;
    private static CognitoIdpAdapter adapter;
    private static String userPoolId;
    private static String clientId;
    private static String testUserId;

    @BeforeAll
    static void setUp() {
        // Create Cognito client pointing to LocalStack
        cognitoClient = CognitoIdentityProviderClient.builder()
                .endpointOverride(localstack.getEndpointOverride(COGNITO))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        localstack.getAccessKey(),
                                        localstack.getSecretKey()
                                )
                        )
                )
                .region(Region.of(localstack.getRegion()))
                .build();

        // Create User Pool
        CreateUserPoolResponse userPoolResponse = cognitoClient.createUserPool(
                CreateUserPoolRequest.builder()
                        .poolName("test-pool")
                        .autoVerifiedAttributes(AttributeType.EMAIL)
                        .policies(UserPoolPolicyType.builder()
                                .passwordPolicy(PasswordPolicyType.builder()
                                        .minimumLength(8)
                                        .requireLowercase(true)
                                        .requireUppercase(true)
                                        .requireNumbers(true)
                                        .requireSymbols(false)
                                        .build())
                                .build())
                        .build()
        );
        userPoolId = userPoolResponse.userPool().id();

        // Create App Client
        CreateUserPoolClientResponse clientResponse = cognitoClient.createUserPoolClient(
                CreateUserPoolClientRequest.builder()
                        .userPoolId(userPoolId)
                        .clientName("test-client")
                        .explicitAuthFlows(
                                ExplicitAuthFlowsType.ALLOW_USER_PASSWORD_AUTH,
                                ExplicitAuthFlowsType.ALLOW_REFRESH_TOKEN_AUTH
                        )
                        .build()
        );
        clientId = clientResponse.userPoolClient().clientId();

        // Configure adapter
        CognitoProperties properties = new CognitoProperties();
        properties.setRegion(localstack.getRegion());
        properties.setUserPoolId(userPoolId);
        properties.setClientId(clientId);

        CognitoClientFactory clientFactory = new CognitoClientFactory(properties) {
            @Override
            public CognitoIdentityProviderClient createCognitoClient() {
                return cognitoClient;
            }
        };

        CognitoUserService userService = new CognitoUserService(clientFactory, properties);
        CognitoAdminService adminService = new CognitoAdminService(clientFactory, properties);
        adapter = new CognitoIdpAdapter(userService, adminService);
    }

    @AfterAll
    static void tearDown() {
        if (cognitoClient != null) {
            cognitoClient.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should create user successfully")
    void testCreateUser() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username(TEST_USERNAME)
                .email(TEST_EMAIL)
                .givenName("Test")
                .familyName("User")
                .temporaryPassword(TEST_PASSWORD)
                .build();

        StepVerifier.create(adapter.createUser(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getId()).isNotEmpty();
                    testUserId = response.getBody().getId();
                })
                .verifyComplete();
    }

    @Test
    @Order(2)
    @DisplayName("Should authenticate user with password")
    @Disabled("LocalStack Cognito may not fully support USER_PASSWORD_AUTH flow")
    void testLogin() {
        LoginRequest request = LoginRequest.builder()
                .username(TEST_USERNAME)
                .password(TEST_PASSWORD)
                .clientId(clientId)
                .build();

        StepVerifier.create(adapter.login(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getAccessToken()).isNotEmpty();
                    assertThat(response.getBody().getRefreshToken()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    @Order(3)
    @DisplayName("Should get user info")
    @Disabled("Requires valid access token from LocalStack")
    void testGetUserInfo() {
        // This test requires a valid access token which is hard to obtain from LocalStack
        String mockAccessToken = "mock-token";

        StepVerifier.create(adapter.getUserInfo(mockAccessToken))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getEmail()).isEqualTo(TEST_EMAIL);
                })
                .verifyComplete();
    }

    @Test
    @Order(4)
    @DisplayName("Should create group (role)")
    void testCreateRoles() {
        CreateRolesRequest request = CreateRolesRequest.builder()
                .roleNames(java.util.List.of("admin", "user"))
                .build();

        StepVerifier.create(adapter.createRoles(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().getRoleNames()).containsExactlyInAnyOrder("admin", "user");
                })
                .verifyComplete();
    }

    @Test
    @Order(5)
    @DisplayName("Should assign roles to user")
    void testAssignRolesToUser() {
        AssignRolesRequest request = AssignRolesRequest.builder()
                .userId(testUserId != null ? testUserId : TEST_USERNAME)
                .roleNames(java.util.List.of("user"))
                .build();

        StepVerifier.create(adapter.assignRolesToUser(request))
                .verifyComplete();
    }

    @Test
    @Order(6)
    @DisplayName("Should get user roles")
    void testGetRoles() {
        String userId = testUserId != null ? testUserId : TEST_USERNAME;

        StepVerifier.create(adapter.getRoles(userId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody()).contains("user");
                })
                .verifyComplete();
    }

    @Test
    @Order(7)
    @DisplayName("Should update user attributes")
    void testUpdateUser() {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .userId(testUserId != null ? testUserId : TEST_USERNAME)
                .email("newemail@example.com")
                .givenName("Updated")
                .familyName("User")
                .build();

        StepVerifier.create(adapter.updateUser(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    @Order(8)
    @DisplayName("Should change user password")
    @Disabled("LocalStack may not fully support password operations")
    void testChangePassword() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .username(TEST_USERNAME)
                .oldPassword(TEST_PASSWORD)
                .newPassword("NewTestPass123!")
                .build();

        StepVerifier.create(adapter.changePassword(request))
                .verifyComplete();
    }

    @Test
    @Order(9)
    @DisplayName("Should remove roles from user")
    void testRemoveRolesFromUser() {
        AssignRolesRequest request = AssignRolesRequest.builder()
                .userId(testUserId != null ? testUserId : TEST_USERNAME)
                .roleNames(java.util.List.of("user"))
                .build();

        StepVerifier.create(adapter.removeRolesFromUser(request))
                .verifyComplete();
    }

    @Test
    @Order(10)
    @DisplayName("Should delete user")
    void testDeleteUser() {
        String userId = testUserId != null ? testUserId : TEST_USERNAME;

        StepVerifier.create(adapter.deleteUser(userId))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle authentication failure")
    @Disabled("LocalStack authentication flow limitations")
    void testLoginFailure() {
        LoginRequest request = LoginRequest.builder()
                .username("nonexistent")
                .password("wrongpassword")
                .clientId(clientId)
                .build();

        StepVerifier.create(adapter.login(request))
                .expectError()
                .verify();
    }
}
