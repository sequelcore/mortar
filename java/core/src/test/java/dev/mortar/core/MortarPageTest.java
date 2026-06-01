package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class MortarPageTest {
    @Test
    void calculatesOffsetFromPageAndSize() {
        MortarPage page = MortarPage.of(3, 20);

        assertThat(page.page()).isEqualTo(3);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.offset()).isEqualTo(60);
    }

    @Test
    void rejectsInvalidPageValues() {
        assertThatThrownBy(() -> MortarPage.of(-1, 20))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("page cannot be negative");

        assertThatThrownBy(() -> MortarPage.of(0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("size must be greater than zero");
    }
}
