package uz.topdim.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantProfileRouteTest {

    @Test
    void adminMerchantChangeRequestsAreRoutedToCouponService() {
        YamlPropertiesFactoryBean loader = new YamlPropertiesFactoryBean();
        loader.setResources(new ClassPathResource("application.yml"));
        Properties properties = loader.getObject();
        assertThat(properties).isNotNull();

        String couponServicePathPredicate = null;
        for (int routeIndex = 0; routeIndex < 50; routeIndex++) {
            String prefix = "spring.cloud.gateway.routes[" + routeIndex + "]";
            if ("coupon-service".equals(properties.getProperty(prefix + ".id"))) {
                couponServicePathPredicate = properties.getProperty(prefix + ".predicates[0]");
                break;
            }
        }

        assertThat(couponServicePathPredicate)
                .as("coupon-service Path predicate")
                .isNotNull()
                .contains("/api/v1/admin/merchant-change-requests/**");
    }
}
