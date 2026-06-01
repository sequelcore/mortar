package dev.mortar.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

final class MortarSqlDocumentationTest {
    @Test
    void escapesSqlDocumentationHtml() {
        String documentation = MortarSqlDocumentation.render(
            "ClientRepository.find",
            "select * from clients where name <> ?"
        );

        assertThat(documentation)
            .contains("ClientRepository.find")
            .contains("select * from clients where name &lt;&gt; ?");
    }
}
