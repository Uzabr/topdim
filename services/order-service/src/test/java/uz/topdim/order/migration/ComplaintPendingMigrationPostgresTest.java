package uz.topdim.order.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class ComplaintPendingMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void upgradesLegacyDuplicatesWithoutChangingHistoryAndConstrainsFutureRows() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("12"))
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders
                        (id, order_number, user_id, total_amount, status)
                    VALUES
                        (100, 'ORD-100', 7, 10000, 'PAID')
                    """);
            statement.executeUpdate("""
                    INSERT INTO purchased_coupons
                        (id, user_id, order_id, coupon_offer_id, coupon_option_id,
                         coupon_code, status)
                    VALUES
                        (200, 7, 100, 1, 1, 'LEGACY-200', 'USED'),
                        (201, 7, 100, 2, 2, 'FUTURE-201', 'USED')
                    """);
            statement.executeUpdate("""
                    INSERT INTO complaints
                        (user_id, order_id, purchased_coupon_id, subject, description, status)
                    VALUES
                        (7, 100, 200, 'Legacy first', 'History must remain', 'PENDING'),
                        (7, 100, 200, 'Legacy second', 'History must remain', 'PENDING')
                    """);
        }

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var legacy = statement.executeQuery("""
                    SELECT COUNT(*) AS row_count,
                           COUNT(*) FILTER (WHERE status = 'PENDING') AS pending_count,
                           COUNT(*) FILTER (WHERE enforce_pending_uniqueness = FALSE) AS legacy_count
                    FROM complaints
                    WHERE purchased_coupon_id = 200
                    """)) {
                assertThat(legacy.next()).isTrue();
                assertThat(legacy.getInt("row_count")).isEqualTo(2);
                assertThat(legacy.getInt("pending_count")).isEqualTo(2);
                assertThat(legacy.getInt("legacy_count")).isEqualTo(2);
            }

            statement.executeUpdate("""
                    INSERT INTO complaints
                        (user_id, order_id, purchased_coupon_id, subject, description, status)
                    VALUES
                        (7, 100, 201, 'Future first', 'Must be enforced', 'PENDING')
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO complaints
                        (user_id, order_id, purchased_coupon_id, subject, description, status)
                    VALUES
                        (7, 100, 201, 'Future duplicate', 'Must be rejected', 'PENDING')
                    """))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_complaints_pending_coupon");
        }
    }
}
