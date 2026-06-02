package com.medkernel.shared.ids;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UlidTest {

    @Test
    void createsCrockfordBase32Ulid() {
        String ulid = Ulid.newUlid();

        assertThat(ulid).matches("[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(ulid).doesNotContain("I", "L", "O", "U");
    }
}
