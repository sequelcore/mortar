package dev.mortar.intellij;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class MortarCliExplain {
    private MortarCliExplain() {
    }

    public static MortarExplainResult explain(String cliPath, String connection, String sql)
        throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command(cliPath, connection, sql))
            .redirectErrorStream(true)
            .start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new MortarExplainResult(false, "Mortar CLI EXPLAIN timed out.");
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
            .trim();
        return new MortarExplainResult(process.exitValue() == 0, output);
    }

    static List<String> command(String cliPath, String connection, String sql) {
        return List.of(
            cliPath,
            "explain",
            "--connection",
            connection,
            "--sql",
            sql
        );
    }
}
