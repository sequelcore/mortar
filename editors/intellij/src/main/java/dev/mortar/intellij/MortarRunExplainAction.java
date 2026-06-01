package dev.mortar.intellij;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.io.IOException;
import java.util.Optional;

public final class MortarRunExplainAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent event) {
        Optional<MortarSqlResult> sql = sqlAtCaret(event);
        if (sql.isEmpty()) {
            notify(event, NotificationType.WARNING, "No Mortar SQL snapshot was found at the caret.");
            return;
        }

        String connection = MortarPluginProperties.postgresConnection();
        if (connection.isBlank()) {
            notify(event, NotificationType.WARNING, "Set dev.mortar.postgres.connection before running EXPLAIN.");
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> runExplain(event, sql.get(), connection));
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(sqlAtCaret(event).isPresent());
    }

    private static void runExplain(
        AnActionEvent event,
        MortarSqlResult sql,
        String connection
    ) {
        try {
            MortarExplainResult result = MortarCliExplain.explain(
                MortarPluginProperties.cliPath(),
                connection,
                sql.sql()
            );
            notify(
                event,
                result.ok() ? NotificationType.INFORMATION : NotificationType.ERROR,
                result.output().isBlank() ? "Mortar EXPLAIN finished without output." : result.output()
            );
        } catch (IOException error) {
            notify(event, NotificationType.ERROR, "Mortar CLI failed: " + error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            notify(event, NotificationType.ERROR, "Mortar CLI EXPLAIN was interrupted.");
        }
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

    private static void notify(
        AnActionEvent event,
        NotificationType type,
        String content
    ) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Mortar")
            .createNotification(content, type)
            .notify(event.getProject());
    }
}
