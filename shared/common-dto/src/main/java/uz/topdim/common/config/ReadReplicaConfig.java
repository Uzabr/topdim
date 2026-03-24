package uz.topdim.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Конфигурация Read Replicas.
 * Активируется при наличии spring.datasource.replica.url в application.yml.
 *
 * Настройка в application.yml:
 * <pre>
 * spring:
 *   datasource:
 *     primary:
 *       url: jdbc:postgresql://primary-host:5432/topdim_xxx
 *       username: topdim
 *       password: xxx
 *     replica:
 *       url: jdbc:postgresql://replica-host:5432/topdim_xxx
 *       username: topdim_readonly
 *       password: xxx
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.replica.url")
public class ReadReplicaConfig {

    /**
     * Primary DataSource (для записи).
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.primary")
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * Replica DataSource (для чтения).
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.replica")
    public DataSource replicaDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * Routing DataSource: readOnly → replica, write → primary.
     */
    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replicaDataSource") DataSource replica
    ) {
        ReadWriteRoutingDataSource routing = new ReadWriteRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("primary", primary);
        dataSourceMap.put("replica", replica);

        routing.setTargetDataSources(dataSourceMap);
        routing.setDefaultTargetDataSource(primary);

        return routing;
    }
}
