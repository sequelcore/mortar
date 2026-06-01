package dev.mortar.intellij;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import java.util.Optional;

public final class MortarDocumentationProvider implements DocumentationProvider {
    @Override
    public String generateDoc(PsiElement element, PsiElement originalElement) {
        return documentationFor(originalElement).orElse(null);
    }

    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return documentationFor(originalElement).orElse(null);
    }

    private static Optional<String> documentationFor(PsiElement element) {
        return MortarSqlLookup.findSqlForElement(element)
            .map(sql -> MortarSqlDocumentation.render(sql.snapshotName(), sql.sql()));
    }
}
