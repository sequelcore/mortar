package dev.mortar.spring;

import dev.mortar.core.MortarDb;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.jdbc.MortarJdbcClient;
import dev.mortar.jdbc.MortarJdbcLogger;
import dev.mortar.postgres.PostgresQueryRenderer;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

@AutoConfiguration
@EnableConfigurationProperties(MortarSpringProperties.class)
public class MortarAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    MortarDb mortarDb() {
        return new SimpleMortarDb();
    }

    @Bean
    @ConditionalOnMissingBean
    QueryRenderer mortarQueryRenderer(MortarSpringProperties properties) {
        return new PostgresQueryRenderer(properties.getSqlFormat());
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    MortarJdbcClient mortarJdbcClient(
        DataSource dataSource,
        QueryRenderer renderer,
        ObjectProvider<MortarJdbcLogger> logger
    ) {
        return new MortarJdbcClient(
            new TransactionAwareDataSourceProxy(dataSource),
            renderer,
            logger.getIfAvailable(MortarJdbcLogger::noop)
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "mortar.jdbc.logging", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    MortarJdbcLogger mortarJdbcLogger() {
        return MortarJdbcLogger.noop();
    }

    @Bean
    @ConditionalOnProperty(prefix = "mortar.diagnostics", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    MortarDiagnosticsEndpoint mortarDiagnosticsEndpoint(MortarSpringProperties properties) {
        return new MortarDiagnosticsEndpoint(properties);
    }
}
