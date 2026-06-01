package dev.mortar.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.nio.charset.StandardCharsets;

public final class MortarCreateSnapshotFileQuickFixTest extends BasePlatformTestCase {
    public void testCreatesCanonicalSnapshotFile() throws Exception {
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
        MortarCreateSnapshotFileQuickFix quickFix = new MortarCreateSnapshotFileQuickFix();

        assertThat(quickFix.isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()))
            .isTrue();

        quickFix.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());

        VirtualFile snapshotFile = myFixture.getFile()
            .getVirtualFile()
            .getParent()
            .findChild(MortarSnapshotFiles.SNAPSHOT_FILE);
        assertThat(snapshotFile).isNotNull();
        assertThat(new String(snapshotFile.contentsToByteArray(), StandardCharsets.UTF_8))
            .contains("\"format\": \"mortar-sql-snapshot-v1\"")
            .contains("\"snapshots\": []");
        assertThat(quickFix.isAvailable(getProject(), myFixture.getEditor(), myFixture.getFile()))
            .isFalse();
    }
}
