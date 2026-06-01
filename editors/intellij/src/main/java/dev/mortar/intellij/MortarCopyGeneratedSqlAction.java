package dev.mortar.intellij;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.awt.datatransfer.StringSelection;
import java.util.Optional;

public final class MortarCopyGeneratedSqlAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent event) {
        Optional<MortarSqlResult> result = sqlAtCaret(event);
        result.ifPresent(sql -> CopyPasteManager.getInstance()
            .setContents(new StringSelection(sql.sql())));
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(sqlAtCaret(event).isPresent());
    }

    private static Optional<MortarSqlResult> sqlAtCaret(AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null) {
            return Optional.empty();
        }

        PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null && offset > 0) {
            element = file.findElementAt(offset - 1);
        }
        return MortarSqlLookup.findSqlForElement(element);
    }
}
