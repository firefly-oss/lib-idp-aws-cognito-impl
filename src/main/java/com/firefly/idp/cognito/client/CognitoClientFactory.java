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

package com.firefly.idp.cognito.client;

import com.firefly.idp.cognito.properties.CognitoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClientBuilder;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;

/**
 * Factory for creating and managing AWS Cognito Identity Provider clients.
 * 
 * <p>This factory creates a singleton instance of the Cognito client
 * configured with the appropriate region and timeouts from properties.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CognitoClientFactory {

    private final CognitoProperties properties;
    private volatile CognitoIdentityProviderClient client;
    private URI endpointOverride;
    private AwsCredentialsProvider credentialsProvider;

    /**
     * Set custom endpoint override (for LocalStack testing)
     *
     * @param endpointOverride Custom endpoint URI
     */
    public void setEndpointOverride(URI endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    /**
     * Set custom credentials provider (for LocalStack testing)
     *
     * @param credentialsProvider Custom credentials provider
     */
    public void setCredentialsProvider(AwsCredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    /**
     * Get or create the Cognito Identity Provider client
     *
     * @return Configured CognitoIdentityProviderClient
     */
    public CognitoIdentityProviderClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = createClient();
                }
            }
        }
        return client;
    }

    /**
     * Create a new Cognito Identity Provider client
     * 
     * @return Configured CognitoIdentityProviderClient
     */
    protected CognitoIdentityProviderClient createClient() {
        log.info("Initializing AWS Cognito client for region: {}", properties.getRegion());
        
        CognitoIdentityProviderClientBuilder builder = CognitoIdentityProviderClient.builder()
                .region(Region.of(properties.getRegion()))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofMillis(properties.getRequestTimeout()))
                        .apiCallAttemptTimeout(Duration.ofMillis(properties.getConnectionTimeout())));
        
        // Apply endpoint override if set (for LocalStack)
        if (endpointOverride != null) {
            log.info("Using custom endpoint: {}", endpointOverride);
            builder.endpointOverride(endpointOverride);
        }
        
        // Apply custom credentials provider if set (for LocalStack)
        if (credentialsProvider != null) {
            log.info("Using custom credentials provider");
            builder.credentialsProvider(credentialsProvider);
        }
        
        return builder.build();
    }

    /**
     * Close the Cognito client on shutdown
     */
    @PreDestroy
    public void destroy() {
        if (client != null) {
            log.info("Closing AWS Cognito client");
            client.close();
        }
    }
}
