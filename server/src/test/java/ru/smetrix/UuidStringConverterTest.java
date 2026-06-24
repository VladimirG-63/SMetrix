package ru.smetrix;

import org.junit.jupiter.api.Test;
import ru.smetrix.config.UuidStringConverter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidStringConverterTest {
    @Test
    void roundTripsNativeUuid() {
        String id = UUID.randomUUID().toString();
        UuidStringConverter converter = new UuidStringConverter();
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(id))).isEqualTo(id);
    }
}
