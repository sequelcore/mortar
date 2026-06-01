package dev.mortar.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class MortarCliExplainTest {
    @Test
    public void buildsExplainCommand() {
        assertThat(MortarCliExplain.command(
            "mortar",
            "postgres://postgres@localhost:5432/postgres",
            "select 1"
        ))
            .containsExactly(
                "mortar",
                "explain",
                "--connection",
                "postgres://postgres@localhost:5432/postgres",
                "--sql",
                "select 1"
            );
    }
}
