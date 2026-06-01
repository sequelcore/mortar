package dev.mortar.intellij;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class MortarSnapshotDiagnostics {
    private MortarSnapshotDiagnostics() {
    }

    public static Optional<MortarSnapshotDiagnostic> diagnose(PsiMethod method) {
        Optional<String> snapshotName = MortarSnapshotMarker.before(method);
        if (snapshotName.isEmpty()) {
            return Optional.empty();
        }

        Optional<VirtualFile> snapshotFile = MortarSnapshotFiles.findNearestSnapshotFile(method);
        if (snapshotFile.isEmpty()) {
            return Optional.of(new MortarSnapshotDiagnostic(
                snapshotName.get(),
                "Mortar snapshot file not found for " + snapshotName.get(),
                true
            ));
        }

        Optional<String> snapshotContent = read(snapshotFile.get());
        if (snapshotContent.isEmpty()
            || MortarSqlSnapshots.findSql(snapshotContent.get(), snapshotName.get()).isEmpty()) {
            return Optional.of(new MortarSnapshotDiagnostic(
                snapshotName.get(),
                "Mortar snapshot not found or invalid: " + snapshotName.get(),
                false
            ));
        }

        return Optional.empty();
    }

    private static Optional<String> read(VirtualFile snapshotFile) {
        try {
            return Optional.of(new String(snapshotFile.contentsToByteArray(), StandardCharsets.UTF_8));
        } catch (IOException error) {
            return Optional.empty();
        }
    }
}
