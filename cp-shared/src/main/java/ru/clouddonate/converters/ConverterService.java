package ru.clouddonate.converters;

import java.io.Reader;

public interface ConverterService {
    String serialize(Object input);
    <T> T deserialize(Reader reader, Class<T> claz);
}
