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

package com.firefly.idp.cognito.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for calculating AWS Cognito SECRET_HASH.
 * 
 * <p>AWS Cognito requires a SECRET_HASH for app clients with a client secret.
 * The hash is calculated as: Base64(HMAC_SHA256(clientSecret, username + clientId))
 */
public class CognitoSecretHashCalculator {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /**
     * Calculate the SECRET_HASH for AWS Cognito authentication.
     *
     * @param clientId The Cognito app client ID
     * @param clientSecret The Cognito app client secret
     * @param username The username to authenticate
     * @return Base64-encoded HMAC SHA256 hash
     * @throws RuntimeException if hash calculation fails
     */
    public static String calculateSecretHash(String clientId, String clientSecret, String username) {
        try {
            String message = username + clientId;
            
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    clientSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256_ALGORITHM
            );
            mac.init(secretKey);
            
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
            
        } catch (Exception e) {
            throw new RuntimeException("Error calculating SECRET_HASH", e);
        }
    }
}
