# Firefly OpenCore Banking Platform — lib-idp-aws-cognito-impl

Identity Provider (IdP) adapter that implements the Firefly lib-idp port using AWS Cognito. It exposes a reactive (Spring WebFlux) interface for authentication and authorization and provides an administrative surface for user, role (group), session, and password management.

---

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Requirements](#requirements)
- [Configuration](#configuration)
  - [Key properties](#key-properties)
  - [Profiles](#profiles)
  - [Example application.yaml](#example-applicationyaml)
- [Build and Run](#build-and-run)
- [How the Adapter Works](#how-the-adapter-works)
  - [Authentication Flow](#authentication-flow)
  - [Token Management](#token-management)
  - [User Management](#user-management)
  - [Role/Group Management](#rolegroup-management)
  - [Session Management](#session-management)
- [Testing](#testing)
  - [Unit Testing](#unit-testing)
  - [Testing Strategy](#testing-strategy)
- [Integration with Security Center](#integration-with-security-center)
- [API Operations](#api-operations)
- [Security Considerations](#security-considerations)
- [Development Notes](#development-notes)
- [Troubleshooting](#troubleshooting)
- [Versioning](#versioning)
- [Contributing](#contributing)
- [License](#license)

---

## Overview
This repository provides the AWS Cognito-backed implementation of the Firefly lib-idp adapter. Within a hexagonal (ports-and-adapters) architecture, it delegates identity operations to a configured AWS Cognito User Pool and presents a consistent interface for Firefly services.

**What this project is:**
- An adapter of the Firefly lib-idp port backed by AWS Cognito
- A focused library exposing consistent IdP APIs for Firefly components
- Reactive (non-blocking) using Spring WebFlux and Project Reactor
- Fully integrated with the Firefly Security Center

**What this project is not:**
- A user-facing identity UI (use AWS Cognito Hosted UI or build your own)
- A replacement for AWS Cognito console/operations
- A standalone microservice (it is a library dependency)

## Architecture
- **Ports**: Provided by the upstream dependency `com.firefly:lib-idp-adapter` (DTOs and `IdpAdapter` interface)
- **Adapter/Implementation**: This repository implements `IdpAdapter` using AWS SDK for Java v2 (Cognito Identity Provider client)
- **Transport**: Reactive (Mono/Flux) via Spring WebFlux
- **Config**: Strongly-typed via `CognitoProperties` bound from `application.yaml`

**Packages of interest:**
- `com.firefly.idp.cognito.adapter` — Main `CognitoIdpAdapter` implementation
- `com.firefly.idp.cognito.service` — Business logic services (CognitoUserService, CognitoAdminService)
- `com.firefly.idp.cognito.client` — AWS Cognito client factory
- `com.firefly.idp.cognito.properties` — Configuration properties binding
- `com.firefly.idp.cognito.config` — Spring configuration
- `com.firefly.idp.cognito.util` — Utility classes (e.g., SECRET_HASH calculator)

## Requirements
- Java 21+
- Maven 3.9+
- Access to AWS Cognito User Pool (development, staging, or production)
- AWS credentials configured (via environment variables, ~/.aws/credentials, or IAM role)

## Configuration
Application uses `application.yaml` with environment-variable overrides. All Cognito properties are under the `firefly.security-center.idp.cognito` prefix.

### Key properties
| Property | Environment Variable | Description | Default |
|----------|---------------------|-------------|---------|
| `firefly.security-center.idp.cognito.region` | `COGNITO_REGION` | AWS region for User Pool | `us-east-1` |
| `firefly.security-center.idp.cognito.user-pool-id` | `COGNITO_USER_POOL_ID` | Cognito User Pool ID | *Required* |
| `firefly.security-center.idp.cognito.client-id` | `COGNITO_CLIENT_ID` | App Client ID | *Required* |
| `firefly.security-center.idp.cognito.client-secret` | `COGNITO_CLIENT_SECRET` | App Client Secret | Optional |
| `firefly.security-center.idp.cognito.domain` | `COGNITO_DOMAIN` | Cognito domain for hosted UI | Optional |
| `firefly.security-center.idp.cognito.connection-timeout` | `COGNITO_CONNECTION_TIMEOUT` | Connection timeout (ms) | `30000` |
| `firefly.security-center.idp.cognito.request-timeout` | `COGNITO_REQUEST_TIMEOUT` | Request timeout (ms) | `60000` |

### Profiles
- `dev` — Developer-friendly logs, detailed debugging
- `testing` — Test profile with mocked services
- `prod` — Production-lean logging

### Example application.yaml
```yaml
firefly:
  security-center:
    idp:
      provider: cognito  # Important: must be 'cognito' to activate this adapter
      cognito:
        region: ${COGNITO_REGION:us-east-1}
        user-pool-id: ${COGNITO_USER_POOL_ID:us-east-1_XXXXXXXXX}
        client-id: ${COGNITO_CLIENT_ID:your-client-id}
        client-secret: ${COGNITO_CLIENT_SECRET:your-client-secret}
        connection-timeout: ${COGNITO_CONNECTION_TIMEOUT:30000}
        request-timeout: ${COGNITO_REQUEST_TIMEOUT:60000}
```

## Build and Run
This is a library module, not a standalone application. It's meant to be included as a dependency in the Firefly Security Center.

**Build:**
```bash
mvn clean install
```

**Run tests:**
```bash
mvn test
```

**Include in your project:**
```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>lib-idp-aws-cognito-impl</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## How the Adapter Works

### Authentication Flow
1. **Login Request** → `CognitoIdpAdapter.login(LoginRequest)` → `CognitoUserService.login()`
   - Calls `InitiateAuth` API with `USER_PASSWORD_AUTH` flow
   - Computes SECRET_HASH if client secret is configured
   - Returns `TokenResponse` with access_token, refresh_token, id_token, and expires_in

2. **Token Refresh** → `CognitoIdpAdapter.refresh(RefreshRequest)` → `CognitoUserService.refresh()`
   - Calls `InitiateAuth` API with `REFRESH_TOKEN_AUTH` flow
   - Returns new access and ID tokens

3. **Logout** → `CognitoIdpAdapter.logout(LogoutRequest)` → `CognitoUserService.logout()`
   - Calls `GlobalSignOut` API to invalidate all tokens for the user

### Token Management
- **Introspection**: Uses `GetUser` API to validate tokens and retrieve user attributes
- **User Info**: Retrieves user profile information from token claims and Cognito attributes
- **Token Revocation**: Uses `RevokeToken` to invalidate refresh tokens

### User Management
AWS Cognito uses the following mappings for user operations:

| Firefly DTO Field | Cognito User Attribute |
|------------------|------------------------|
| `username` | `USERNAME` |
| `email` | `email` |
| `givenName` | `given_name` |
| `familyName` | `family_name` |
| `phoneNumber` | `phone_number` |
| `id` (in responses) | `sub` (Cognito user UUID) |

**Create User:**
- Calls `AdminCreateUser` API
- Sets temporary password if provided
- Assigns default attributes (email, name, etc.)
- Returns user ID (`sub`)

**Update User:**
- Calls `AdminUpdateUserAttributes` API
- Updates mutable attributes (email, name, phone, custom attributes)

**Delete User:**
- Calls `AdminDeleteUser` API

### Role/Group Management
Cognito uses **Groups** to model roles. This adapter maps:
- Firefly "roles" → Cognito "groups"
- Firefly "scopes" → Cognito custom attributes or group metadata (limited support)

**Create Roles:**
- Calls `CreateGroup` API for each role
- Group name = role name
- Group description and precedence can be set

**Assign Roles:**
- Calls `AdminAddUserToGroup` API

**Remove Roles:**
- Calls `AdminRemoveUserFromGroup` API

**Get User Roles:**
- Calls `AdminListGroupsForUser` API
- Returns list of group names

### Session Management
Cognito does not expose traditional session IDs like Keycloak. Instead:
- **List Sessions**: Returns devices associated with a user via `AdminListDevices` API
- **Revoke Session**: Uses `AdminUserGlobalSignOut` to sign out the user from all devices

**Note**: Session management in Cognito is device-based, not session-based. The adapter provides a compatible interface but the semantics differ slightly from Keycloak.

## Testing

### Unit Testing
Unit tests use **Mockito** to mock AWS SDK clients. No LocalStack or real AWS infrastructure is required for unit tests.

Key test classes:
- `CognitoIdpAdapterTest`: Tests the main adapter implementation with mocked services
- `CognitoUserServiceTest`: Tests authentication flows (login, refresh, logout)
- `CognitoAdminServiceTest`: Tests user and role management operations

**Run unit tests:**
```bash
mvn test
```

### Testing Strategy

**Unit Tests (Default)**
- Use **Mockito** to mock AWS SDK clients
- No external dependencies (LocalStack, Docker, or AWS)
- Run with `mvn test`
- Fast execution, no flakiness
- Test class: `CognitoIdpAdapterTest`

**Integration Tests with LocalStack (Optional)**
- Test against real LocalStack Cognito emulation
- Requires Docker running
- Run with `mvn verify -Dgroups=integration` or remove `@Disabled` annotation
- Test class: `CognitoIdpAdapterLocalStackIT`

**To run LocalStack integration tests:**

1. Ensure Docker is running
2. Remove or comment out the `@Disabled` annotation in `CognitoIdpAdapterLocalStackIT`
3. Run:
   ```bash
   mvn verify -Dgroups=integration
   ```

**LocalStack Limitations:**
- Some Cognito operations may not be fully supported
- Authentication flows (`USER_PASSWORD_AUTH`) have limited support
- Token-based operations may not work as expected
- Behavior may differ from real AWS Cognito

**Why unit tests by default?**
- Faster test execution (no container startup)
- No Docker/Testcontainers dependencies
- Consistent test results
- Better for CI/CD pipelines

## Integration with Security Center
This adapter is automatically loaded by the Security Center when:
1. The dependency is present in the classpath
2. `firefly.security-center.idp.provider=cognito` is set in configuration

The Security Center's `IdpAutoConfiguration` uses `@ConditionalOnClass` to detect this implementation:
```java
@Bean
@ConditionalOnClass(name = "com.firefly.idp.cognito.adapter.CognitoIdpAdapter")
@ConditionalOnProperty(name = "firefly.security-center.idp.provider", havingValue = "cognito")
public IdpAdapter cognitoIdpAdapter(ApplicationContext context) {
    return context.getBean(CognitoIdpAdapter.class);
}
```

## API Operations
The adapter implements all methods from `IdpAdapter`:

**Authentication:**
- `login(LoginRequest)` → Authenticate user and get tokens
- `refresh(RefreshRequest)` → Refresh access token
- `logout(LogoutRequest)` → Sign out user
- `introspect(String accessToken)` → Validate token
- `getUserInfo(String accessToken)` → Get user profile

**User Management:**
- `createUser(CreateUserRequest)` → Create new user
- `updateUser(UpdateUserRequest)` → Update user attributes
- `deleteUser(String userId)` → Delete user
- `changePassword(ChangePasswordRequest)` → Change user password
- `resetPassword(String username)` → Trigger password reset

**Role Management:**
- `createRoles(CreateRolesRequest)` → Create groups
- `createScope(CreateScopeRequest)` → Create custom scope (limited)
- `assignRolesToUser(AssignRolesRequest)` → Add user to groups
- `removeRolesFromUser(AssignRolesRequest)` → Remove user from groups
- `getRoles(String userId)` → List user's groups

**Session Management:**
- `listSessions(String userId)` → List user devices
- `revokeSession(String sessionId)` → Global sign out
- `revokeRefreshToken(String refreshToken)` → Revoke token

## Security Considerations
- **SECRET_HASH**: If your Cognito App Client has a client secret, the adapter automatically computes the required SECRET_HASH for authentication flows
- **Credentials**: Use IAM roles in production; avoid hardcoding AWS credentials
- **Token Storage**: Tokens should be stored securely on the client side (HttpOnly cookies, secure storage)
- **Logging**: Tokens and passwords are never logged in plain text
- **HTTPS**: Always use HTTPS in production to protect tokens in transit

## Development Notes
- **Reactive Stack**: Spring WebFlux + Project Reactor (Mono/Flux)
- **AWS SDK**: AWS SDK for Java v2 (software.amazon.awssdk:cognitoidentityprovider)
- **DTOs**: Provided by `com.firefly:lib-idp-adapter`
- **Lombok**: Used for boilerplate reduction (@Data, @RequiredArgsConstructor, @Slf4j)
- **Validation**: Jakarta Validation annotations on properties

## Troubleshooting

| Issue | Possible Cause | Solution |
|-------|---------------|----------|
| `NotAuthorizedException` | Invalid username/password | Verify credentials and user status in Cognito |
| `UserNotFoundException` | User does not exist | Check user pool and username |
| `InvalidParameterException` | Missing or invalid parameters | Review request DTO and required fields |
| `ResourceNotFoundException` | User pool or app client not found | Verify `userPoolId` and `clientId` configuration |
| `TooManyRequestsException` | Rate limiting | Implement exponential backoff and retry logic |
| Connection timeout | Network issues or slow AWS response | Increase `connectionTimeout` property |
| SECRET_HASH mismatch | Client secret not configured correctly | Verify `clientSecret` matches App Client settings |

**Enable debug logging:**
```yaml
logging:
  level:
    com.firefly.idp.cognito: DEBUG
    software.amazon.awssdk: DEBUG
```

## Versioning
- Maven coordinates: `com.firefly:lib-idp-aws-cognito-impl:1.0.0-SNAPSHOT`
- Java version: 21
- AWS SDK version: 2.20.26

## Contributing
Issues and PRs are welcome. Please include clear reproduction steps and tests when applicable.

**Development workflow:**
1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure `mvn clean install` passes
5. Submit a pull request

## License
Licensed under the Apache License, Version 2.0. See the LICENSE file for details.
