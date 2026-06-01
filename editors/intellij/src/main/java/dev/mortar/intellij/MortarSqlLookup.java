package dev.mortar.intellij;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class MortarSqlLookup {
    private MortarSqlLookup() {
    }

    public static Optional<MortarSqlResult> findSqlForElement(PsiElement element) {
        if (element == null) {
            return Optional.empty();
        }

        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
        if (method == null) {
            return Optional.empty();
        }

        Optional<String> snapshotName = MortarSnapshotMarker.before(method);
        if (snapshotName.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> snapshotContent = readNearestSnapshotFile(method);
        if (snapshotContent.isEmpty()) {
            return Optional.empty();
        }

        return MortarSqlSnapshots.findSql(snapshotContent.get(), snapshotName.get())
            .map(sql -> new MortarSqlResult(snapshotName.get(), sql));
    }

    private static Optional<String> readNearestSnapshotFile(PsiElement element) {
        Optional<VirtualFile> snapshot = MortarSnapshotFiles.findNearestSnapshotFile(element);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new String(snapshot.get().contentsToByteArray(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
