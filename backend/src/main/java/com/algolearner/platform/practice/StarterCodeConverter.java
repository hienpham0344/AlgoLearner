package com.algolearner.platform.practice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.EnumMap;
import java.util.Map;

/** Stores per-language boilerplate as a JSON object, e.g. {@code {"JAVA":"class Main {}"}}. */
@Converter
public class StarterCodeConverter implements AttributeConverter<Map<PracticeLanguage, String>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<EnumMap<PracticeLanguage, String>> TYPE = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(Map<PracticeLanguage, String> attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Starter code is not serializable", e);
        }
    }

    @Override
    public Map<PracticeLanguage, String> convertToEntityAttribute(String data) {
        if (data == null || data.isBlank()) return new EnumMap<>(PracticeLanguage.class);
        try {
            return MAPPER.readValue(data, TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored starter code is not valid JSON", e);
        }
    }
}
