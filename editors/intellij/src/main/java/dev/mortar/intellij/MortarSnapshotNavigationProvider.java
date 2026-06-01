package dev.mortar.intellij;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public final class MortarSnapshotNavigationProvider implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(
        PsiElement sourceElement,
        int offset,
        Editor editor
    ) {
        Optional<VirtualFile> snapshotFile = MortarSnapshotFiles.findNearestSnapshotFile(sourceElement);
        if (snapshotFile.isEmpty()) {
            return PsiElement.EMPTY_ARRAY;
        }

        PsiElement snapshotPsi = sourceElement.getManager().findFile(snapshotFile.get());
        if (snapshotPsi == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        return new PsiElement[] { snapshotPsi };
    }
}
