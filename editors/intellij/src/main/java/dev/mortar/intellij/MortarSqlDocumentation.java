package dev.mortar.intellij;

public final class MortarSqlDocumentation {
    private MortarSqlDocumentation() {
    }

    public static String render(String snapshotName, String sql) {
        return "<b>Mortar SQL for "
            + escape(snapshotName)
            + "</b><pre><code>"
            + escape(sql)
            + "</code></pre>";
    }

    private static String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
