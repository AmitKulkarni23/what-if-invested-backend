package org.dagger;

import dagger.Component;
import org.service.CoinbaseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Singleton;


@Singleton
@Component(modules = {CoinbaseModule.class, SysEnvModule.class, UtilityModule.class})
public interface ServiceComponent {
    CoinbaseService getCoinbaseService();
    ObjectMapper getObjectMapper();
}
