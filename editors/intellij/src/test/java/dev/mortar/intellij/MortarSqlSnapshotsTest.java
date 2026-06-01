package dev.mortar.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

final class MortarSqlSnapshotsTest {
    @Test
    void findsSqlBySnapshotName() {
        String content = """
            {
              "format": "mortar-sql-snapshot-v1",
              "snapshots": [
                {
                  "name": "ClientRepository.findById",
                  "sql": "select c.id from clients c where c.id = ?",
                  "parameters": [],
                  "metadata": {
                    "tables": ["clients"],
                    "columns": ["clients.id"],
                    "joins": []
                  }
                }
              ]
            }
            """;

        assertThat(MortarSqlSnapshots.findSql(content, "ClientRepository.findById"))
            .contains("select c.id from clients c where c.id = ?");
    }

    @Test
    void returnsEmptyWhenSnapshotNameIsMissing() {
        String content = """
            {
              "format": "mortar-sql-snapshot-v1",
              "snapshots": []
            }
            """;

        assertThat(MortarSqlSnapshots.findSql(content, "ClientRepository.missing"))
            .isEmpty();
    }

    @Test
    void rejectsNonCanonicalSnapshotFormat() {
        String content = """
            {
              "format": "other",
              "snapshots": []
            }
            """;

        assertThat(MortarSqlSnapshots.findSql(content, "ClientRepository.findById"))
            .isEmpty();
    }

    @Test
    void rejectsDuplicateSnapshotNames() {
        String content = """
            {
              "format": "mortar-sql-snapshot-v1",
              "snapshots": [
                {
                  "name": "ClientRepository.findById",
                  "sql": "select 1",
                  "parameters": [],
                  "metadata": {
                    "tables": [],
                    "columns": [],
                    "joins": []
                  }
                },
                {
                  "name": "ClientRepository.findById",
                  "sql": "select 2",
                  "parameters": [],
                  "metadata": {
                    "tables": [],
                    "columns": [],
                    "joins": []
                  }
                }
              ]
            }
            """;

        assertThat(MortarSqlSnapshots.findSql(content, "ClientRepository.findById"))
            .isEmpty();
    }

    @Test
    void rejectsMalformedSnapshotJson() {
        assertThat(MortarSqlSnapshots.findSql("{", "ClientRepository.findById"))
            .isEmpty();
    }

    @Test
    void rejectsBlankSnapshotSql() {
        String content = """
            {
              "format": "mortar-sql-snapshot-v1",
              "snapshots": [
                {
                  "name": "ClientRepository.findById",
                  "sql": "   ",
                  "parameters": [],
                  "metadata": {
                    "tables": [],
                    "columns": [],
                    "joins": []
                  }
                }
              ]
            }
            """;

        assertThat(MortarSqlSnapshots.findSql(content, "ClientRepository.findById"))
            .isEmpty();
    }
}
