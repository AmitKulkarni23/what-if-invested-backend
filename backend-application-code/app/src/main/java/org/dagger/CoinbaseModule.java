package org.dagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
import lombok.extern.slf4j.Slf4j;
import org.config.CoinbaseConfig;
import org.service.CoinbaseService;

import javax.inject.Named;
import javax.inject.Singleton;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.dagger.SysEnvModule.COINBASE_COMMERCE_API_KEY_ENV_NAME;

@Module
@Slf4j
public class CoinbaseModule {

    @Provides
    @Singleton
    HttpClient provideHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Provides
    @Singleton
    CoinbaseConfig provideCoinbaseConfig(@Named(COINBASE_COMMERCE_API_KEY_ENV_NAME)
                                         final String coinbaseCommerceApiKey) {
        log.info("What is the API Key {}", coinbaseCommerceApiKey);
        String frontendBase = System.getenv("FRONTEND_BASE_URL");
        if (frontendBase == null || frontendBase.isBlank()) {
            frontendBase = "http://localhost:3000";
        }
        return new CoinbaseConfig(coinbaseCommerceApiKey, frontendBase);
    }

    @Provides
    @Singleton
    CoinbaseService provideCoinbaseService(HttpClient httpClient, ObjectMapper mapper, CoinbaseConfig config) {
        return new CoinbaseService(httpClient, mapper, config);
    }
}

