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

package com.firefly.idp.cognito.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

/**
 * Configuration properties for AWS Cognito Identity Provider.
 * 
 * <p>These properties are automatically bound from application.yml/properties
 * when the Cognito IDP adapter is enabled.
 * 
 * <p><strong>Configuration Example:</strong></p>
 * <pre>
 * firefly:
 *   security-center:
 *     idp:
 *       cognito:
 *         region: us-east-1
 *         user-pool-id: us-east-1_XXXXXXXXX
 *         client-id: your-client-id
 *         client-secret: your-client-secret
 * </pre>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "firefly.security-center.idp.cognito")
public class CognitoProperties {

    /**
     * AWS region where the Cognito User Pool is located
     */
    @NotBlank(message = "AWS region is required")
    private String region = "us-east-1";

    /**
     * Cognito User Pool ID
     */
    @NotBlank(message = "User Pool ID is required")
    private String userPoolId;

    /**
     * Cognito App Client ID
     */
    @NotBlank(message = "Client ID is required")
    private String clientId;

    /**
     * Cognito App Client Secret (optional for some flows)
     */
    private String clientSecret;

    /**
     * Cognito domain for hosted UI (optional)
     */
    private String domain;

    /**
     * Connection timeout in milliseconds
     */
    private Integer connectionTimeout = 30000;

    /**
     * Request timeout in milliseconds
     */
    private Integer requestTimeout = 60000;
}
