package org.config;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class CoinbaseConfig {
    private final String apiKey;
    private final String frontendBaseUrl;

    public CoinbaseConfig(String apiKey, String frontendBaseUrl) {
        this.apiKey = apiKey;
        this.frontendBaseUrl = frontendBaseUrl;
    }
}

