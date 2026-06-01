package dev.mortar.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class MortarSnapshotNavigationProviderTest extends BasePlatformTestCase {
    public void testNavigatesFromJavaSourceToNearestSnapshotFile() {
        myFixture.addFileToProject(
            "mortar.sql.snap.json",
            """
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
            """
        );
        myFixture.configureByText(
            "ClientRepository.java",
            """
            package example;

            public final class ClientRepository {
                // mortar:snapshot=ClientRepository.findById
                public void find<caret>ById(Long id) {}
            }
            """
        );

        MortarSnapshotNavigationProvider provider = new MortarSnapshotNavigationProvider();

        assertThat(provider.getGotoDeclarationTargets(
            myFixture.getElementAtCaret(),
            myFixture.getCaretOffset(),
            myFixture.getEditor()
        ))
            .hasSize(1)
            .allSatisfy(element -> assertThat(element.getContainingFile().getName())
                .isEqualTo("mortar.sql.snap.json"));
    }
}
