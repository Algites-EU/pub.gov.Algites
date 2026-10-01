package eu.algites.pltf.modustro.builder.model.structureddata;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loads one structured-data document into a generated or handwritten data-object type.
 *
 * <p>The loading contract intentionally performs representation mapping only. Inheritance,
 * defaults, cross-field rules, and effective-model construction remain separate Builder stages.</p>
 */
public interface AIiStructuredDataLoader {
    /**
     * Returns the serialization family handled by this loader.
     *
     * @return supported structured-data format
     */
    AInStructuredDataFormat format();

    /**
     * Loads one document into the requested data-object type.
     *
     * @param aSource source document path
     * @param aTargetType requested generated or handwritten data-object class
     * @param <T> target data-object type
     * @return loaded data object
     * @throws IOException if the document cannot be read or mapped
     */
    <T> T load(Path aSource, Class<T> aTargetType) throws IOException;
}
