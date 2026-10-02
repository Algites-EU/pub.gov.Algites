package eu.algites.pltf.modustro.builder.structureddata.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import eu.algites.pltf.modustro.builder.model.structureddata.AIiStructuredDataLoader;
import eu.algites.pltf.modustro.builder.model.structureddata.AInStructuredDataFormat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Jackson-backed structured-data loader for YAML, JSON and XML Builder data objects.
 */
public final class AIcJacksonStructuredDataLoader implements AIiStructuredDataLoader {

    private final AInStructuredDataFormat format;
    private final ObjectMapper mapper;

    /**
     * Creates one loader for the selected structured-data format.
     *
     * @param aFormat structured-data format
     */
    public AIcJacksonStructuredDataLoader(AInStructuredDataFormat aFormat) {
        format = Objects.requireNonNull(aFormat, "format");
        mapper = AIcMapper(aFormat);
    }

    /**
     * Creates a YAML loader.
     *
     * @return YAML loader
     */
    public static AIcJacksonStructuredDataLoader yaml() {
        return new AIcJacksonStructuredDataLoader(AInStructuredDataFormat.YAML);
    }

    /**
     * Creates a JSON loader.
     *
     * @return JSON loader
     */
    public static AIcJacksonStructuredDataLoader json() {
        return new AIcJacksonStructuredDataLoader(AInStructuredDataFormat.JSON);
    }

    /**
     * Creates an XML loader.
     *
     * @return XML loader
     */
    public static AIcJacksonStructuredDataLoader xml() {
        return new AIcJacksonStructuredDataLoader(AInStructuredDataFormat.XML);
    }

    /**
     * Returns the serialization family handled by this loader.
     *
     * @return supported format
     */
    @Override
    public AInStructuredDataFormat format() {
        return format;
    }

    /**
     * Loads one document into the requested generated or handwritten data-object type.
     *
     * @param aSource source document path
     * @param aTargetType target data-object type
     * @param <T> target data-object type
     * @return mapped data object
     * @throws IOException if the document cannot be read or mapped
     */
    @Override
    public <T> T load(Path aSource, Class<T> aTargetType) throws IOException {
        Objects.requireNonNull(aSource, "source");
        Objects.requireNonNull(aTargetType, "targetType");
        return mapper.readValue(aSource.toFile(), aTargetType);
    }

    private static ObjectMapper AIcMapper(AInStructuredDataFormat aFormat) {
        ObjectMapper locMapper = switch (aFormat) {
            case YAML -> new ObjectMapper((JsonFactory) new YAMLFactory());
            case JSON -> new ObjectMapper();
            case XML -> new XmlMapper();
        };
        locMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        locMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
        locMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        locMapper.addHandler(new AIcGeneratedEnumWireValueHandler());
        return locMapper;
    }

    /**
     * Resolves generated enum constants through their canonical {@code wireValue()} method when the default
     * Jackson enum-name mapping does not match the serialized value.
     */
    private static final class AIcGeneratedEnumWireValueHandler extends DeserializationProblemHandler {

        @Override
        public Object handleWeirdStringValue(
            DeserializationContext aContext,
            Class<?> aTargetType,
            String aValue,
            String aFailureMessage
        ) throws IOException {
            if (!aTargetType.isEnum()) {
                return NOT_HANDLED;
            }
            Method locWireValueMethod;
            try {
                locWireValueMethod = aTargetType.getMethod("wireValue");
            } catch (NoSuchMethodException locException) {
                return NOT_HANDLED;
            }
            Object[] locConstants = aTargetType.getEnumConstants();
            if (locConstants == null) {
                return NOT_HANDLED;
            }
            for (Object locConstant : locConstants) {
                try {
                    Object locWireValue = locWireValueMethod.invoke(locConstant);
                    if (aValue.equals(locWireValue)) {
                        return locConstant;
                    }
                } catch (IllegalAccessException | InvocationTargetException locException) {
                    throw new IOException(
                        "Cannot resolve canonical wire value for generated enum '" + aTargetType.getName() + "'.",
                        locException
                    );
                }
            }
            return NOT_HANDLED;
        }
    }
}
