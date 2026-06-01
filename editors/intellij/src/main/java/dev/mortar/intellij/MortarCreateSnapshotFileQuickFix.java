package dev.mortar.intellij;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class MortarCreateSnapshotFileQuickFix implements IntentionAction {
    private static final String EMPTY_SNAPSHOT = """
        {
          "format": "mortar-sql-snapshot-v1",
          "snapshots": []
        }
        """;

    @Override
    public @NotNull String getText() {
        return "Create mortar.sql.snap.json";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Mortar";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        VirtualFile directory = directory(file);
        return directory != null && directory.findChild(MortarSnapshotFiles.SNAPSHOT_FILE) == null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        VirtualFile directory = directory(file);
        if (directory == null || directory.findChild(MortarSnapshotFiles.SNAPSHOT_FILE) != null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile snapshotFile = directory.createChildData(this, MortarSnapshotFiles.SNAPSHOT_FILE);
                VfsUtil.saveText(snapshotFile, EMPTY_SNAPSHOT);
            } catch (Exception error) {
                throw new IllegalStateException("Could not create Mortar snapshot file", error);
            }
        });
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    private static VirtualFile directory(PsiFile file) {
        if (file == null || file.getVirtualFile() == null) {
            return null;
        }
        return file.getVirtualFile().getParent();
    }
}
