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

import com.firefly.idp.adapter.IdpAdapter;
import com.firefly.idp.cognito.service.CognitoAdminService;
import com.firefly.idp.cognito.service.CognitoUserService;
import com.firefly.idp.dtos.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * AWS Cognito implementation of the Firefly IDP Adapter.
 * 
 * <p>This adapter provides a unified interface to AWS Cognito Identity Provider,
 * implementing all authentication and user management operations defined in the
 * IdpAdapter interface.
 * 
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>User authentication (username/password)</li>
 *   <li>Token refresh and revocation</li>
 *   <li>User management (CRUD operations)</li>
 *   <li>Role/Group management</li>
 *   <li>Session management</li>
 *   <li>Password operations</li>
 * </ul>
 * 
 * @see IdpAdapter
 * @see CognitoUserService
 * @see CognitoAdminService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CognitoIdpAdapter implements IdpAdapter {

    private final CognitoUserService userService;
    private final CognitoAdminService adminService;


    @Override
    public Mono<ResponseEntity<TokenResponse>> login(LoginRequest request) {
        log.debug("Delegating login to CognitoUserService");
        return userService.login(request);
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> refresh(RefreshRequest request) {
        log.debug("Delegating token refresh to CognitoUserService");
        return userService.refresh(request);
    }

    @Override
    public Mono<Void> logout(LogoutRequest request) {
        log.debug("Delegating logout to CognitoUserService");
        return userService.logout(request);
    }

    @Override
    public Mono<ResponseEntity<IntrospectionResponse>> introspect(String accessToken) {
        log.debug("Delegating token introspection to CognitoUserService");
        return userService.introspect(accessToken);
    }

    @Override
    public Mono<ResponseEntity<UserInfoResponse>> getUserInfo(String accessToken) {
        log.debug("Delegating getUserInfo to CognitoUserService");
        return userService.getUserInfo(accessToken);
    }

    @Override
    public Mono<ResponseEntity<CreateUserResponse>> createUser(CreateUserRequest request) {
        log.debug("Delegating createUser to CognitoAdminService");
        return adminService.createUser(request);
    }

    @Override
    public Mono<Void> changePassword(ChangePasswordRequest request) {
        log.debug("Delegating changePassword to CognitoAdminService");
        return adminService.changePassword(request);
    }

    @Override
    public Mono<Void> resetPassword(String username) {
        log.debug("Delegating resetPassword to CognitoAdminService");
        return adminService.resetPassword(username);
    }

    @Override
    public Mono<ResponseEntity<MfaChallengeResponse>> mfaChallenge(String username) {
        log.debug("Delegating mfaChallenge to CognitoAdminService");
        return adminService.mfaChallenge(username);
    }

    @Override
    public Mono<Void> mfaVerify(MfaVerifyRequest request) {
        log.debug("Delegating mfaVerify to CognitoAdminService");
        return adminService.mfaVerify(request);
    }

    @Override
    public Mono<Void> revokeRefreshToken(String refreshToken) {
        log.debug("Delegating revokeRefreshToken to CognitoUserService");
        return userService.revokeRefreshToken(refreshToken);
    }

    @Override
    public Mono<ResponseEntity<List<SessionInfo>>> listSessions(String userId) {
        log.debug("Delegating listSessions to CognitoAdminService");
        return adminService.listSessions(userId);
    }

    @Override
    public Mono<Void> revokeSession(String sessionId) {
        log.debug("Delegating revokeSession to CognitoAdminService");
        return adminService.revokeSession(sessionId);
    }

    @Override
    public Mono<ResponseEntity<List<String>>> getRoles(String userId) {
        log.debug("Delegating getRoles to CognitoAdminService");
        return adminService.getRoles(userId);
    }

    @Override
    public Mono<Void> deleteUser(String userId) {
        log.debug("Delegating deleteUser to CognitoAdminService");
        return adminService.deleteUser(userId);
    }

    @Override
    public Mono<ResponseEntity<UpdateUserResponse>> updateUser(UpdateUserRequest request) {
        log.debug("Delegating updateUser to CognitoAdminService");
        return adminService.updateUser(request);
    }

    @Override
    public Mono<ResponseEntity<CreateRolesResponse>> createRoles(CreateRolesRequest request) {
        log.debug("Delegating createRoles to CognitoAdminService");
        return adminService.createRoles(request);
    }

    @Override
    public Mono<ResponseEntity<CreateScopeResponse>> createScope(CreateScopeRequest request) {
        log.debug("Delegating createScope to CognitoAdminService");
        return adminService.createScope(request);
    }

    @Override
    public Mono<Void> assignRolesToUser(AssignRolesRequest request) {
        log.debug("Delegating assignRolesToUser to CognitoAdminService");
        return adminService.assignRolesToUser(request);
    }

    @Override
    public Mono<Void> removeRolesFromUser(AssignRolesRequest request) {
        log.debug("Delegating removeRolesFromUser to CognitoAdminService");
        return adminService.removeRolesFromUser(request);
    }
}
