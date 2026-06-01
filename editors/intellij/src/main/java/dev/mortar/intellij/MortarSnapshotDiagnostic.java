package dev.mortar.intellij;

public record MortarSnapshotDiagnostic(
    String snapshotName,
    String message,
    boolean canCreateSnapshotFile
) {
}
