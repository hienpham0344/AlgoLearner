package com.algolearner.platform.profile;

import com.algolearner.platform.common.exception.ApiException;
import com.algolearner.platform.common.exception.ErrorCode;
import com.algolearner.platform.profile.dashboard.DashboardRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Proxy;

/** Fail closed until the 4.2 persistence adapters are installed. */
@Configuration
@Profile("!mock")
public class UnavailableProfileDataConfiguration {
    @Bean
    ProfileRepository unavailableProfileRepository() {
        return unavailable(ProfileRepository.class);
    }

    @Bean
    PracticeRepository unavailablePracticeRepository() {
        return unavailable(PracticeRepository.class);
    }

    @Bean
    DashboardRepository unavailableDashboardRepository() {
        return unavailable(DashboardRepository.class);
    }

    private <T> T unavailable(Class<T> contract) {
        return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] { contract },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> contract.getSimpleName() + "Unavailable";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    throw new ApiException(ErrorCode.FEATURE_DATA_UNAVAILABLE,
                            "Profile and dashboard persistence is not configured");
                }));
    }
}
