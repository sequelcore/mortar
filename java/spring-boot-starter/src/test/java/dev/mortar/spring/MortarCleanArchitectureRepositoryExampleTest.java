package dev.mortar.spring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;
import dev.mortar.jdbc.MortarJdbcClient;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLFeatureNotSupportedException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

final class MortarCleanArchitectureRepositoryExampleTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MortarAutoConfiguration.class))
        .withBean(DataSource.class, ExampleDataSource::new)
        .withUserConfiguration(InfrastructureConfiguration.class);

    @Test
    void wiresInfrastructureRepositoryThroughDomainPort() {
        contextRunner.run(context -> {
            ClientRepository repository = context.getBean(ClientRepository.class);

            Optional<ClientSummary> client = repository.findSummaryById(7L);

            assertThat(client).contains(new ClientSummary(7L, "Ada"));
            assertThat(repository).isInstanceOf(MortarClientRepository.class);
        });
    }

    interface ClientRepository {
        Optional<ClientSummary> findSummaryById(long clientId);
    }

    record ClientSummary(long id, String name) {
    }

    static final class MortarClientRepository implements ClientRepository {
        private static final TableRef CLIENTS = new TableRef("clients", "c");
        private static final ColumnRef<Long> ID = CLIENTS.column("id", "id", Long.class);
        private static final ColumnRef<String> NAME = CLIENTS.column("name", "name", String.class);

        private final MortarJdbcClient jdbcClient;

        MortarClientRepository(MortarJdbcClient jdbcClient) {
            this.jdbcClient = jdbcClient;
        }

        @Override
        public Optional<ClientSummary> findSummaryById(long clientId) {
            List<ClientSummary> rows = jdbcClient.fetch(
                new SimpleMortarDb()
                    .from(CLIENTS)
                    .select(ID, NAME)
                    .where(ID.eq(clientId))
                    .build(),
                resultSet -> new ClientSummary(resultSet.getLong("id"), resultSet.getString("name"))
            );

            return rows.stream().findFirst();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InfrastructureConfiguration {
        @Bean
        ClientRepository clientRepository(MortarJdbcClient jdbcClient) {
            return new MortarClientRepository(jdbcClient);
        }
    }

    private static final class ExampleDataSource implements DataSource {
        @Override
        public Connection getConnection() {
            return connection();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connection();
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
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) {
                        return preparedStatement();
                    }
                    return defaultValue(method.getReturnType());
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
                new java.lang.reflect.InvocationHandler() {
                    private boolean unread = true;

                    @Override
                    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                        return switch (method.getName()) {
                            case "next" -> {
                                boolean current = unread;
                                unread = false;
                                yield current;
                            }
                            case "getLong" -> 7L;
                            case "getString" -> "Ada";
                            default -> defaultValue(method.getReturnType());
                        };
                    }
                }
            );
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
}
