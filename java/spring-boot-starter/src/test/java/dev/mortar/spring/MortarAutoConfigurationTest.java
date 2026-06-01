package dev.mortar.spring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.MortarDb;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.jdbc.MortarJdbcLogEvent;
import dev.mortar.jdbc.MortarJdbcLogger;
import dev.mortar.jdbc.MortarJdbcClient;
import dev.mortar.postgres.PostgresQueryRenderer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

final class MortarAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MortarAutoConfiguration.class));

    @Test
    void registersMortarBeansWhenDataSourceExists() {
        contextRunner
            .withBean(javax.sql.DataSource.class, TestDataSource::new)
            .run(context -> assertThat(context)
                .hasSingleBean(MortarDb.class)
                .hasSingleBean(QueryRenderer.class)
                .hasSingleBean(MortarJdbcClient.class));
    }

    @Test
    void backsOffWhenUserDefinesMortarBeans() {
        MortarDb mortarDb = new SimpleMortarDb();
        QueryRenderer renderer = query -> new RenderedQuery("select 1", java.util.List.of());

        contextRunner
            .withBean(javax.sql.DataSource.class, TestDataSource::new)
            .withBean(MortarDb.class, () -> mortarDb)
            .withBean(QueryRenderer.class, () -> renderer)
            .run(context -> {
                assertThat(context).hasSingleBean(MortarDb.class);
                assertThat(context).hasSingleBean(QueryRenderer.class);
                assertThat(context.getBean(MortarDb.class)).isSameAs(mortarDb);
                assertThat(context.getBean(QueryRenderer.class)).isSameAs(renderer);
            });
    }

    @Test
    void doesNotCreateJdbcClientWithoutDataSource() {
        contextRunner.run(context -> assertThat(context)
            .hasSingleBean(MortarDb.class)
            .hasSingleBean(QueryRenderer.class)
            .doesNotHaveBean(MortarJdbcClient.class));
    }

    @Test
    void usesPostgresRendererByDefault() {
        contextRunner.run(context -> assertThat(context.getBean(QueryRenderer.class))
            .isInstanceOf(PostgresQueryRenderer.class));
    }

    @Test
    void appliesSqlFormattingPropertyToPostgresRenderer() {
        contextRunner
            .withPropertyValues("mortar.sql-format=pretty")
            .run(context -> {
                QueryRenderer renderer = context.getBean(QueryRenderer.class);
                RenderedQuery rendered = renderer.render(new SimpleMortarDb()
                    .from(new dev.mortar.core.TableRef("clients", "c"))
                    .build());

                assertThat(rendered.sql()).contains("\nfrom clients c");
            });
    }

    @Test
    void appliesDialectPropertyToPostgresRenderer() {
        contextRunner
            .withPropertyValues("mortar.dialect=postgres")
            .run(context -> {
                assertThat(context.getBean(MortarSpringProperties.class).getDialect())
                    .isEqualTo(MortarDialect.POSTGRES);
                assertThat(context.getBean(QueryRenderer.class))
                    .isInstanceOf(PostgresQueryRenderer.class);
            });
    }

    @Test
    void enablesJdbcLoggingBeanFromProperty() {
        contextRunner
            .withPropertyValues("mortar.jdbc.logging.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(MortarJdbcLogger.class));
    }

    @Test
    void allowsUserProvidedJdbcLogger() {
        contextRunner
            .withBean(MortarJdbcLogger.class, CapturingLogger::new)
            .run(context -> assertThat(context.getBean(MortarJdbcLogger.class))
                .isInstanceOf(CapturingLogger.class));
    }

    @Test
    void injectsJdbcLoggerIntoAutoConfiguredClientWhenAvailable() throws Exception {
        contextRunner
            .withBean(javax.sql.DataSource.class, TestDataSource::new)
            .withBean(MortarJdbcLogger.class, CapturingLogger::new)
            .run(context -> {
                java.lang.reflect.Field loggerField = MortarJdbcClient.class.getDeclaredField("logger");
                loggerField.setAccessible(true);

                assertThat(loggerField.get(context.getBean(MortarJdbcClient.class))).isSameAs(
                    context.getBean(MortarJdbcLogger.class)
                );
            });
    }

    @Test
    void participatesInSpringManagedJdbcTransaction() {
        CountingDataSource dataSource = new CountingDataSource();
        contextRunner
            .withBean(DataSource.class, () -> dataSource)
            .withBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(dataSource))
            .run(context -> {
                TransactionTemplate transactionTemplate = new TransactionTemplate(
                    context.getBean(DataSourceTransactionManager.class)
                );

                transactionTemplate.execute(status -> {
                    context.getBean(MortarJdbcClient.class)
                        .fetch(new SimpleMortarDb().from(new dev.mortar.core.TableRef("clients", "c")).build(), resultSet -> "x");
                    return null;
                });

                assertThat(dataSource.connectionRequests).isEqualTo(1);
                assertThat(dataSource.commitCalls).isEqualTo(1);
                assertThat(dataSource.rollbackCalls).isZero();
            });
    }

    @Test
    void keepsJdbcLoggingDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(MortarJdbcLogger.class));
    }

    private static final class CapturingLogger implements MortarJdbcLogger {
        @Override
        public void log(MortarJdbcLogEvent event) {
        }
    }

    private static final class CountingDataSource implements DataSource {
        private int connectionRequests;
        private int commitCalls;
        private int rollbackCalls;

        @Override
        public Connection getConnection() {
            connectionRequests++;
            return connection();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("unwrap is not supported");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        private Connection connection() {
            return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAutoCommit" -> true;
                    case "setAutoCommit", "close" -> null;
                    case "commit" -> {
                        commitCalls++;
                        yield null;
                    }
                    case "rollback" -> {
                        rollbackCalls++;
                        yield null;
                    }
                    case "prepareStatement" -> preparedStatement();
                    default -> defaultValue(method.getReturnType());
                }
            );
        }

        private PreparedStatement preparedStatement() {
            return (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { PreparedStatement.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("executeQuery")) {
                        return resultSet();
                    }
                    return defaultValue(method.getReturnType());
                }
            );
        }

        private ResultSet resultSet() {
            return (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { ResultSet.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("next")) {
                        return false;
                    }
                    return defaultValue(method.getReturnType());
                }
            );
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        return null;
    }
}
