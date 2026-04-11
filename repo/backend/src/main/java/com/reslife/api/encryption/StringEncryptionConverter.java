package com.reslife.api.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that transparently encrypts/decrypts
 * {@code String} fields with AES-256-GCM.
 *
 * <p>Apply to any entity field that contains PII:
 * <pre>
 *   {@literal @}Convert(converter = StringEncryptionConverter.class)
 *   {@literal @}Column(columnDefinition = "TEXT")
 *   private String sensitiveField;
 * </pre>
 *
 * <p>The converter is not a Spring bean — it accesses the key via the
 * static {@link EncryptionService#instance()} accessor.
 */
@Converter
public class StringEncryptionConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return EncryptionService.instance().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return EncryptionService.instance().decrypt(dbData);
    }
}
