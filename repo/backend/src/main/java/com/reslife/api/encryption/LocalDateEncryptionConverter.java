package com.reslife.api.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/**
 * JPA {@link AttributeConverter} that encrypts {@link LocalDate} values at rest.
 *
 * <p>The date is serialised as an ISO-8601 string ({@code "YYYY-MM-DD"}) before
 * encryption. The DB column must be {@code TEXT} to hold the Base64 ciphertext.
 *
 * <pre>
 *   {@literal @}Convert(converter = LocalDateEncryptionConverter.class)
 *   {@literal @}Column(name = "date_of_birth", columnDefinition = "TEXT")
 *   private LocalDate dateOfBirth;
 * </pre>
 */
@Converter
public class LocalDateEncryptionConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        if (attribute == null) return null;
        return EncryptionService.instance().encrypt(attribute.toString()); // ISO-8601
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return LocalDate.parse(EncryptionService.instance().decrypt(dbData));
    }
}
