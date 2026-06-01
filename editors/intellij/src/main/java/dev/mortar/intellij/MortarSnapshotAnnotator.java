package dev.mortar.intellij;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public final class MortarSnapshotAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiMethod method)) {
            return;
        }

        MortarSnapshotDiagnostics.diagnose(method)
            .ifPresent(diagnostic -> {
                PsiIdentifier name = method.getNameIdentifier();
                PsiElement target = name == null ? method : name;
                AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.WARNING, diagnostic.message())
                    .range(target);
                if (diagnostic.canCreateSnapshotFile()) {
                    builder.withFix(new MortarCreateSnapshotFileQuickFix());
                }
                builder.create();
            });
    }
}
