package org.dagger;

import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Module;
import dagger.Provides;
import org.service.CoinbaseExchangeService;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import javax.inject.Named;
import javax.inject.Singleton;
import java.net.http.HttpClient;
import java.time.Duration;

@Module
public class CoinbaseExchangeModule {

    @Provides
    @Singleton
    public HttpClient provideHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Provides
    @Singleton
    public SecretsManagerClient provideSecretsManagerClient() {
        return SecretsManagerClient.builder().build();
    }

    @Provides
    @Singleton
    public CoinbaseExchangeService provideCoinbaseExchangeService(HttpClient httpClient, ObjectMapper objectMapper, SecretsManagerClient secretsManagerClient,
                                                                  @Named("CoinbaseApiSecretArn") String coinbaseApiSecretArn) {
        return new CoinbaseExchangeService(httpClient, objectMapper, secretsManagerClient, coinbaseApiSecretArn);
    }

    @Provides
    @Named("CoinbaseApiSecretArn")
    public String provideCoinbaseApiSecretArn() {
        // This will be provided by the Lambda environment variable
        return System.getenv("COINBASE_API_SECRET_ARN");
    }
}