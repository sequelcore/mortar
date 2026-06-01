package dev.mortar.intellij;

import com.intellij.ide.util.PropertiesComponent;

public final class MortarPluginProperties {
    public static final String CLI_PATH = "dev.mortar.cli.path";
    public static final String POSTGRES_CONNECTION = "dev.mortar.postgres.connection";

    private MortarPluginProperties() {
    }

    public static String cliPath() {
        return PropertiesComponent.getInstance().getValue(CLI_PATH, "mortar");
    }

    public static String postgresConnection() {
        return PropertiesComponent.getInstance().getValue(POSTGRES_CONNECTION, "");
    }
}
