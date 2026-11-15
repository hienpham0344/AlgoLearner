package com.algolearner.platform.practice;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores allowed languages as a sorted comma separated string, e.g. {@code JAVA,PYTHON}. */
@Converter
public class LanguageSetConverter implements AttributeConverter<Set<PracticeLanguage>, String> {
    @Override
    public String convertToDatabaseColumn(Set<PracticeLanguage> attribute) {
        if (attribute == null || attribute.isEmpty()) return "";
        return EnumSet.copyOf(attribute).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<PracticeLanguage> convertToEntityAttribute(String data) {
        EnumSet<PracticeLanguage> set = EnumSet.noneOf(PracticeLanguage.class);
        if (data != null && !data.isBlank()) {
            Arrays.stream(data.split(",")).map(String::trim).map(PracticeLanguage::valueOf).forEach(set::add);
        }
        return set;
    }
}
