package dev.mortar.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;

public final class MortarSnapshotDiagnosticsTest extends BasePlatformTestCase {
    public void testDiagnosesMissingSnapshotFile() {
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

        PsiMethod method = methodAtCaret();

        assertThat(MortarSnapshotDiagnostics.diagnose(method))
            .hasValueSatisfying(diagnostic -> {
                assertThat(diagnostic.snapshotName()).isEqualTo("ClientRepository.findById");
                assertThat(diagnostic.message())
                    .isEqualTo("Mortar snapshot file not found for ClientRepository.findById");
                assertThat(diagnostic.canCreateSnapshotFile()).isTrue();
            });
    }

    public void testDiagnosesMissingSnapshotEntry() {
        myFixture.addFileToProject(
            "mortar.sql.snap.json",
            """
            {
              "format": "mortar-sql-snapshot-v1",
              "snapshots": [
                {
                  "name": "ClientRepository.findAll",
                  "sql": "select c.id from clients c",
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

        assertThat(MortarSnapshotDiagnostics.diagnose(methodAtCaret()))
            .hasValueSatisfying(diagnostic -> {
                assertThat(diagnostic.message())
                    .isEqualTo("Mortar snapshot not found or invalid: ClientRepository.findById");
                assertThat(diagnostic.canCreateSnapshotFile()).isFalse();
            });
    }

    public void testAnnotatorPublishesInlineWarning() {
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

        List<HighlightInfo> highlights = myFixture.doHighlighting();

        assertThat(highlights)
            .anySatisfy(highlight -> assertThat(highlight.getDescription())
                .isEqualTo("Mortar snapshot file not found for ClientRepository.findById"));
    }

    private PsiMethod methodAtCaret() {
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset() - 1);
        assertThat(element).isNotNull();
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        assertThat(method).isNotNull();
        return method;
    }
}
