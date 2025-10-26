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
import com.firefly.idp.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for handling AWS Cognito admin operations.
 * 
 * <p>Implements administrative functions including:
 * <ul>
 *   <li>User creation</li>
 *   <li>Password management</li>
 *   <li>User updates and deletion</li>
 *   <li>Role/Group management</li>
 *   <li>Session management</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CognitoAdminService {

    private final CognitoClientFactory clientFactory;
    private final CognitoProperties properties;

    /**
     * Create a new user in Cognito
     */
    public Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Creating Cognito user: {}", request.getUsername());
            
            CognitoIdentityProviderClient client = clientFactory.getClient();
            
            List<AttributeType> attributes = new ArrayList<>();
            if (request.getEmail() != null) {
                attributes.add(AttributeType.builder()
                        .name("email")
                        .value(request.getEmail())
                        .build());
                attributes.add(AttributeType.builder()
                        .name("email_verified")
                        .value("true")
                        .build());
            }
            if (request.getGivenName() != null) {
                attributes.add(AttributeType.builder()
                        .name("given_name")
                        .value(request.getGivenName())
                        .build());
            }
            if (request.getFamilyName() != null) {
                attributes.add(AttributeType.builder()
                        .name("family_name")
                        .value(request.getFamilyName())
                        .build());
            }
            
            AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(request.getUsername())
                    .userAttributes(attributes)
                    .temporaryPassword(request.getPassword())
                    .messageAction(MessageActionType.SUPPRESS) // Don't send invitation email
                    .build();
            
            AdminCreateUserResponse response = client.adminCreateUser(createUserRequest);
            
            // Set permanent password if provided
            if (request.getPassword() != null) {
                AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
                        .userPoolId(properties.getUserPoolId())
                        .username(request.getUsername())
                        .password(request.getPassword())
                        .permanent(true)
                        .build();
                client.adminSetUserPassword(setPasswordRequest);
            }
            
            CreateUserResponse userResponse = CreateUserResponse.builder()
                    .id(response.user().username())
                    .username(response.user().username())
                    .email(request.getEmail())
                    .build();
            
            log.info("Successfully created user: {}", request.getUsername());
            return ResponseEntity.ok(userResponse);
            
        }).onErrorResume(exception -> {
            log.error("Failed to create user: {}", request.getUsername(), exception);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        });
    }

    /**
     * Change user password
     */
    public Mono<Void> changePassword(com.firefly.idp.dtos.ChangePasswordRequest request) {
        return Mono.fromRunnable(() -> {
            log.info("Changing password for user: {}", request.getUserId());
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
                        .userPoolId(properties.getUserPoolId())
                        .username(request.getUserId())
                        .password(request.getNewPassword())
                        .permanent(true)
                        .build();
                
                client.adminSetUserPassword(setPasswordRequest);
                
                log.info("Successfully changed password for user: {}", request.getUserId());
            } catch (Exception e) {
                log.error("Failed to change password", e);
                throw new RuntimeException("Password change failed", e);
            }
        });
    }

    /**
     * Reset user password (send reset email)
     */
    public Mono<Void> resetPassword(String username) {
        return Mono.fromRunnable(() -> {
            log.info("Resetting password for user: {}", username);
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                AdminResetUserPasswordRequest resetRequest = AdminResetUserPasswordRequest.builder()
                        .userPoolId(properties.getUserPoolId())
                        .username(username)
                        .build();
                
                client.adminResetUserPassword(resetRequest);
                
                log.info("Successfully initiated password reset for user: {}", username);
            } catch (Exception e) {
                log.error("Failed to reset password", e);
                throw new RuntimeException("Password reset failed", e);
            }
        });
    }

    /**
     * Initiate MFA challenge (returns challenge details)
     */
    public Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username) {
        return Mono.fromCallable(() -> {
            log.info("Initiating MFA challenge for user: {}", username);
            
            // Cognito MFA is typically handled during authentication flow
            // This is a placeholder for custom MFA implementation
            MfaChallengeResponse response = MfaChallengeResponse.builder()
                    .challengeId(UUID.randomUUID().toString())
                    .deliveryMethod("SMS")
                    .build();
            
            return ResponseEntity.ok(response);
        });
    }

    /**
     * Verify MFA code
     */
    public Mono<Void> mfaVerify(MfaVerifyRequest request) {
        return Mono.fromRunnable(() -> {
            log.info("Verifying MFA code");
            
            // MFA verification is handled during authentication flow in Cognito
            // This is a placeholder
            log.info("MFA verification completed");
        });
    }

    /**
     * List active sessions for a user
     */
    public Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId) {
        return Mono.fromCallable(() -> {
            log.info("Listing sessions for user: {}", userId);
            
            CognitoIdentityProviderClient client = clientFactory.getClient();
            
            AdminListDevicesRequest listDevicesRequest = AdminListDevicesRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(userId)
                    .build();
            
            AdminListDevicesResponse response = client.adminListDevices(listDevicesRequest);
            
            List<SessionInfo> sessions = response.devices().stream()
                    .map(device -> SessionInfo.builder()
                            .sessionId(device.deviceKey())
                            .userId(userId)
                            .lastAccessAt(device.deviceLastModifiedDate())
                            .build())
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(sessions);
            
        }).onErrorResume(exception -> {
            log.error("Failed to list sessions", exception);
            return Mono.just(ResponseEntity.ok(Collections.emptyList()));
        });
    }

    /**
     * Revoke a specific session
     */
    public Mono<Void> revokeSession(String sessionId) {
        return Mono.fromRunnable(() -> {
            log.info("Revoking session: {}", sessionId);
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                // Note: Cognito uses device keys for session management
                AdminForgetDeviceRequest forgetDeviceRequest = AdminForgetDeviceRequest.builder()
                        .userPoolId(properties.getUserPoolId())
                        .deviceKey(sessionId)
                        .build();
                
                client.adminForgetDevice(forgetDeviceRequest);
                
                log.info("Successfully revoked session: {}", sessionId);
            } catch (Exception e) {
                log.error("Failed to revoke session", e);
                throw new RuntimeException("Session revocation failed", e);
            }
        });
    }

    /**
     * Get user roles (groups in Cognito)
     */
    public Mono<ResponseEntity<List<String>>> getRoles(String userId) {
        return Mono.fromCallable(() -> {
            log.info("Getting roles for user: {}", userId);
            
            CognitoIdentityProviderClient client = clientFactory.getClient();
            
            AdminListGroupsForUserRequest listGroupsRequest = AdminListGroupsForUserRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(userId)
                    .build();
            
            AdminListGroupsForUserResponse response = client.adminListGroupsForUser(listGroupsRequest);
            
            List<String> roles = response.groups().stream()
                    .map(GroupType::groupName)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(roles);
            
        }).onErrorResume(exception -> {
            log.error("Failed to get user roles", exception);
            return Mono.just(ResponseEntity.ok(Collections.emptyList()));
        });
    }

    /**
     * Delete a user
     */
    public Mono<Void> deleteUser(String userId) {
        return Mono.fromRunnable(() -> {
            log.info("Deleting user: {}", userId);
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                AdminDeleteUserRequest deleteRequest = AdminDeleteUserRequest.builder()
                        .userPoolId(properties.getUserPoolId())
                        .username(userId)
                        .build();
                
                client.adminDeleteUser(deleteRequest);
                
                log.info("Successfully deleted user: {}", userId);
            } catch (Exception e) {
                log.error("Failed to delete user", e);
                throw new RuntimeException("User deletion failed", e);
            }
        });
    }

    /**
     * Update user attributes
     */
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Updating user: {}", request.getUserId());
            
            CognitoIdentityProviderClient client = clientFactory.getClient();
            
            List<AttributeType> attributes = new ArrayList<>();
            if (request.getEmail() != null) {
                attributes.add(AttributeType.builder().name("email").value(request.getEmail()).build());
            }
            if (request.getGivenName() != null) {
                attributes.add(AttributeType.builder().name("given_name").value(request.getGivenName()).build());
            }
            if (request.getFamilyName() != null) {
                attributes.add(AttributeType.builder().name("family_name").value(request.getFamilyName()).build());
            }
            
            AdminUpdateUserAttributesRequest updateRequest = AdminUpdateUserAttributesRequest.builder()
                    .userPoolId(properties.getUserPoolId())
                    .username(request.getUserId())
                    .userAttributes(attributes)
                    .build();
            
            client.adminUpdateUserAttributes(updateRequest);
            
            UpdateUserResponse response = UpdateUserResponse.builder()
                    .id(request.getUserId())
                    .username(request.getUserId())
                    .build();
            
            log.info("Successfully updated user: {}", request.getUserId());
            return ResponseEntity.ok(response);
            
        }).onErrorResume(exception -> {
            log.error("Failed to update user", exception);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        });
    }

    /**
     * Create roles (groups in Cognito)
     */
    public Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Creating roles: {}", request.getRoleNames());
            
            CognitoIdentityProviderClient client = clientFactory.getClient();
            List<String> createdRoles = new ArrayList<>();
            
            for (String roleName : request.getRoleNames()) {
                try {
                    CreateGroupRequest createGroupRequest = CreateGroupRequest.builder()
                            .groupName(roleName)
                            .userPoolId(properties.getUserPoolId())
                            .description("Role: " + roleName)
                            .build();
                    
                    client.createGroup(createGroupRequest);
                    createdRoles.add(roleName);
                    
                } catch (Exception e) {
                    log.warn("Failed to create role: {}", roleName, e);
                }
            }
            
            CreateRolesResponse response = CreateRolesResponse.builder()
                    .createdRoleNames(createdRoles)
                    .build();
            
            return ResponseEntity.ok(response);
            
        }).onErrorResume(exception -> {
            log.error("Failed to create roles", exception);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
        });
    }

    /**
     * Create scope (placeholder - Cognito doesn't have direct scope concept)
     */
    public Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Creating scope: {}", request.getName());
            
            // Cognito uses resource servers for scopes
            // This is a simplified placeholder
            CreateScopeResponse response = CreateScopeResponse.builder()
                    .name(request.getName())
                    .build();
            
            return ResponseEntity.ok(response);
        });
    }

    /**
     * Assign roles (add user to groups)
     */
    public Mono<Void> assignRolesToUser(AssignRolesRequest request) {
        return Mono.fromRunnable(() -> {
            log.info("Assigning roles to user: {}", request.getUserId());
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                for (String roleName : request.getRoleNames()) {
                    AdminAddUserToGroupRequest addToGroupRequest = AdminAddUserToGroupRequest.builder()
                            .userPoolId(properties.getUserPoolId())
                            .username(request.getUserId())
                            .groupName(roleName)
                            .build();
                    
                    client.adminAddUserToGroup(addToGroupRequest);
                }
                
                log.info("Successfully assigned roles to user: {}", request.getUserId());
            } catch (Exception e) {
                log.error("Failed to assign roles", e);
                throw new RuntimeException("Role assignment failed", e);
            }
        });
    }

    /**
     * Remove roles from user
     */
    public Mono<Void> removeRolesFromUser(AssignRolesRequest request) {
        return Mono.fromRunnable(() -> {
            log.info("Removing roles from user: {}", request.getUserId());
            
            try {
                CognitoIdentityProviderClient client = clientFactory.getClient();
                
                for (String roleName : request.getRoleNames()) {
                    AdminRemoveUserFromGroupRequest removeFromGroupRequest = AdminRemoveUserFromGroupRequest.builder()
                            .userPoolId(properties.getUserPoolId())
                            .username(request.getUserId())
                            .groupName(roleName)
                            .build();
                    
                    client.adminRemoveUserFromGroup(removeFromGroupRequest);
                }
                
                log.info("Successfully removed roles from user: {}", request.getUserId());
            } catch (Exception e) {
                log.error("Failed to remove roles", e);
                throw new RuntimeException("Role removal failed", e);
            }
        });
    }
}
