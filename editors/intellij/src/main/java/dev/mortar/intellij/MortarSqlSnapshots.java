package dev.mortar.intellij;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class MortarSqlSnapshots {
    private static final String SQL_SNAPSHOT_FORMAT = "mortar-sql-snapshot-v1";

    private MortarSqlSnapshots() {
    }

    public static Optional<String> findSql(String snapshotContent, String snapshotName) {
        Optional<JsonObject> root = parseCanonicalSnapshotFile(snapshotContent);
        if (root.isEmpty()) {
            return Optional.empty();
        }

        JsonArray snapshots = root.get().getAsJsonArray("snapshots");
        for (JsonElement element : snapshots) {
            JsonObject snapshot = element.getAsJsonObject();
            if (snapshotName.equals(stringProperty(snapshot, "name"))) {
                return Optional.of(stringProperty(snapshot, "sql"));
            }
        }

        return Optional.empty();
    }

    private static Optional<JsonObject> parseCanonicalSnapshotFile(String snapshotContent) {
        try {
            JsonElement rootElement = JsonParser.parseString(snapshotContent);
            if (!rootElement.isJsonObject()) {
                return Optional.empty();
            }

            JsonObject root = rootElement.getAsJsonObject();
            if (!SQL_SNAPSHOT_FORMAT.equals(stringProperty(root, "format"))) {
                return Optional.empty();
            }

            JsonArray snapshots = root.getAsJsonArray("snapshots");
            if (snapshots == null || !hasCanonicalSnapshots(snapshots)) {
                return Optional.empty();
            }

            return Optional.of(root);
        } catch (IllegalStateException | JsonParseException error) {
            return Optional.empty();
        }
    }

    private static boolean hasCanonicalSnapshots(JsonArray snapshots) {
        Set<String> names = new HashSet<>();
        for (JsonElement element : snapshots) {
            if (!element.isJsonObject()) {
                return false;
            }

            JsonObject snapshot = element.getAsJsonObject();
            String name = stringProperty(snapshot, "name");
            String sql = stringProperty(snapshot, "sql");
            if (name == null || name.isBlank() || sql == null || sql.isBlank()) {
                return false;
            }
            if (!names.add(name)) {
                return false;
            }
        }
        return true;
    }

    private static String stringProperty(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return value.getAsString();
    }
}
