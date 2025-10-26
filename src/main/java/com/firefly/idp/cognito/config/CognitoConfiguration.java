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

package com.firefly.idp.cognito.config;

import com.firefly.idp.cognito.properties.CognitoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for AWS Cognito IDP Adapter.
 * 
 * <p>This configuration class:
 * <ul>
 *   <li>Enables Cognito configuration properties</li>
 *   <li>Scans the Cognito adapter package for components</li>
 *   <li>Is automatically loaded when provider=cognito</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(CognitoProperties.class)
@ComponentScan(basePackages = "com.firefly.idp.cognito")
@Slf4j
public class CognitoConfiguration {

    @Bean
    public com.firefly.idp.cognito.client.CognitoClientFactory cognitoClientFactory(CognitoProperties properties) {
        log.info("Configuring AWS Cognito Client Factory for region: {}", properties.getRegion());
        
        com.firefly.idp.cognito.client.CognitoClientFactory factory = 
                new com.firefly.idp.cognito.client.CognitoClientFactory(properties);
        
        // Configure endpoint override if provided (for LocalStack testing)
        if (properties.getEndpointOverride() != null && !properties.getEndpointOverride().isEmpty()) {
            log.info("Configuring Cognito client with endpoint override: {}", properties.getEndpointOverride());
            factory.setEndpointOverride(java.net.URI.create(properties.getEndpointOverride()));
        }
        
        return factory;
    }
    
    public CognitoConfiguration() {
        log.info("AWS Cognito IDP Adapter Configuration loaded");
    }
}
