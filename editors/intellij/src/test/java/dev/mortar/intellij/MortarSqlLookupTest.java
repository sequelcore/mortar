package dev.mortar.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public final class MortarSqlLookupTest extends BasePlatformTestCase {
    public void testFindsSqlAtJavaCaret() {
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

        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset() - 1);
        assertThat(element).isNotNull();
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        assertThat(method).isNotNull();
        assertThat(MortarSnapshotMarker.before(method)).contains("ClientRepository.findById");
        assertThat(MortarSnapshotFiles.findNearestSnapshotFile(method)).isPresent();

        assertThat(MortarSqlLookup.findSqlForElement(element))
            .hasValueSatisfying(sql -> assertThat(sql.sql())
                .isEqualTo("select c.id from clients c where c.id = ?"));
    }

    public void testDoesNotReusePreviousMethodMarker() {
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
                public void findById(Long id) {}

                public void find<caret>All() {}
            }
            """
        );

        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset() - 1);
        assertThat(element).isNotNull();
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        assertThat(method).isNotNull();

        assertThat(MortarSnapshotMarker.before(method)).isEmpty();
        assertThat(MortarSqlLookup.findSqlForElement(element)).isEmpty();
    }
}
