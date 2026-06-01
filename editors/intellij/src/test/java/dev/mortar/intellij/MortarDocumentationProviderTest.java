package dev.mortar.intellij;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

final class MortarDocumentationProviderTest {
    @Test
    void parsesSnapshotMarkerFromJavaComment() {
        assertThat(MortarSnapshotMarker.parse(
            "// mortar:snapshot=ClientRepository.findById"
        ))
            .contains("ClientRepository.findById");
    }

    @Test
    void ignoresCommentsWithoutSnapshotMarker() {
        assertThat(MortarSnapshotMarker.parse("// regular comment"))
            .isEmpty();
    }
}
