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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import jakarta.annotation.PreDestroy;
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

    /**
     * Get or create the Cognito Identity Provider client
     *
     * @return Configured CognitoIdentityProviderClient
     */
    public CognitoIdentityProviderClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    log.info("Initializing AWS Cognito client for region: {}", properties.getRegion());
                    client = CognitoIdentityProviderClient.builder()
                            .region(Region.of(properties.getRegion()))
                            .overrideConfiguration(config -> config
                                    .apiCallTimeout(Duration.ofMillis(properties.getRequestTimeout()))
                                    .apiCallAttemptTimeout(Duration.ofMillis(properties.getConnectionTimeout())))
                            .build();
                }
            }
        }
        return client;
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
