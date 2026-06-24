package ru.smetrix.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/** Keeps API/client identifiers as strings while storing them as native PostgreSQL UUID values. */
@Converter
public class UuidStringConverter implements AttributeConverter<String, UUID> {
    @Override
    public UUID convertToDatabaseColumn(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    @Override
    public String convertToEntityAttribute(UUID value) {
        return value == null ? null : value.toString();
    }
}
