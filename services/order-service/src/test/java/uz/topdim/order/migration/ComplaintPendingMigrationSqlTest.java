package uz.topdim.order.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ComplaintPendingMigrationSqlTest {

    @Test
    void migrationMarksLegacyRowsUnconstrainedAndEnforcesFuturePendingComplaints() throws IOException {
        String sql;
        try (var migration = getClass().getResourceAsStream(
                "/db/migration/V13__prevent_duplicate_pending_complaints.sql")) {
            assertThat(migration).isNotNull();
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .toLowerCase();
        }

        assertThat(sql)
                .contains("add column enforce_pending_uniqueness boolean")
                .contains("update complaints set enforce_pending_uniqueness = false")
                .contains("alter column enforce_pending_uniqueness set default true")
                .contains("alter column enforce_pending_uniqueness set not null")
                .contains("uq_complaints_pending_coupon")
                .contains("enforce_pending_uniqueness = true");
    }
}
