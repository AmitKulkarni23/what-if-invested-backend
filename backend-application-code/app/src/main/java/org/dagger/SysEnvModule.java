package org.dagger;

import dagger.Module;
import dagger.Provides;

import javax.inject.Named;

@Module
public class SysEnvModule {
    public static final String COINBASE_COMMERCE_API_KEY_ENV_NAME = "COINBASE_COMMERCE_API_KEY_ENV_NAME";
    private static final String COINBASE_COMMERCE_API_KEY_ENV = "COINBASE_COMMERCE_API_KEY_ENV";

    @Provides
    @Named(COINBASE_COMMERCE_API_KEY_ENV_NAME)
    public String provideCoinbaseCommerceApiKey() {
        return System.getenv(COINBASE_COMMERCE_API_KEY_ENV);
    }

}
