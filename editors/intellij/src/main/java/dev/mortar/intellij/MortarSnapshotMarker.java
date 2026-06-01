package dev.mortar.intellij;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import java.util.Optional;

final class MortarSnapshotMarker {
    private static final String SNAPSHOT_MARKER = "mortar:snapshot";

    private MortarSnapshotMarker() {
    }

    static Optional<String> before(PsiMethod method) {
        Optional<String> textMarker = markerInFileTextBefore(method);
        if (textMarker.isPresent()) {
            return textMarker;
        }

        PsiElement current = method.getPrevSibling();
        while (current != null) {
            if (current instanceof PsiComment comment) {
                Optional<String> snapshotName = parse(comment.getText());
                if (snapshotName.isPresent()) {
                    return snapshotName;
                }
            }
            if (!current.getText().isBlank()) {
                return Optional.empty();
            }
            current = current.getPrevSibling();
        }
        return Optional.empty();
    }

    private static Optional<String> markerInFileTextBefore(PsiMethod method) {
        if (method.getContainingFile() == null) {
            return Optional.empty();
        }

        String fileText = method.getContainingFile().getText();
        PsiIdentifier nameIdentifier = method.getNameIdentifier();
        int declarationOffset = nameIdentifier == null
            ? method.getTextRange().getStartOffset()
            : nameIdentifier.getTextRange().getStartOffset();
        int declarationLineStart = fileText.lastIndexOf('\n', Math.max(0, declarationOffset - 1));
        int scanStart = Math.min(method.getTextRange().getStartOffset(), fileText.length());
        int scanEnd = declarationLineStart < 0 ? 0 : declarationLineStart + 1;
        if (scanStart >= scanEnd) {
            return Optional.empty();
        }

        String[] lines = fileText.substring(scanStart, scanEnd).split("\\R");
        for (int index = lines.length - 1; index >= 0; index -= 1) {
            String line = lines[index];
            Optional<String> snapshotName = parse(line);
            if (snapshotName.isPresent()) {
                return snapshotName;
            }
            if (!line.isBlank()) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static Optional<String> parse(String text) {
        int markerStart = text.indexOf(SNAPSHOT_MARKER);
        if (markerStart < 0) {
            return Optional.empty();
        }

        String marker = text.substring(markerStart + SNAPSHOT_MARKER.length())
            .replaceFirst("^[=:\\s]+", "")
            .trim();
        if (marker.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(marker.split("\\s+")[0]);
    }
}
