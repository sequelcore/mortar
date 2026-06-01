package dev.mortar.intellij;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.Optional;

public final class MortarSnapshotFiles {
    public static final String SNAPSHOT_FILE = "mortar.sql.snap.json";

    private MortarSnapshotFiles() {
    }

    public static Optional<VirtualFile> findNearestSnapshotFile(PsiElement element) {
        if (element == null) {
            return Optional.empty();
        }

        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return Optional.empty();
        }

        VirtualFile file = containingFile.getVirtualFile();
        if (file == null) {
            return Optional.empty();
        }

        VirtualFile directory = file.getParent();
        while (directory != null) {
            VirtualFile snapshot = directory.findChild(SNAPSHOT_FILE);
            if (snapshot != null && !snapshot.isDirectory()) {
                return Optional.of(snapshot);
            }
            directory = directory.getParent();
        }

        return Optional.empty();
    }
}
