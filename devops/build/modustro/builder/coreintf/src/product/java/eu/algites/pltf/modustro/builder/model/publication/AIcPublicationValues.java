package eu.algites.pltf.modustro.builder.model.publication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Creates deeply immutable snapshots of publication configuration and result metadata. */
public final class AIcPublicationValues {
    private AIcPublicationValues() { }

    public static Map<String, Object> freeze(Map<String, Object> aValues) {
        Map<String, Object> locResult = new LinkedHashMap<>();
        if (aValues != null) aValues.forEach((aKey, aValue) -> locResult.put(aKey, AIcFreezeValue(aValue)));
        return Collections.unmodifiableMap(locResult);
    }

    private static Object AIcFreezeValue(Object aValue) {
        if (aValue instanceof Map<?, ?> locMap) {
            Map<Object, Object> locResult = new LinkedHashMap<>();
            locMap.forEach((aKey, aChild) -> locResult.put(aKey, AIcFreezeValue(aChild)));
            return Collections.unmodifiableMap(locResult);
        }
        if (aValue instanceof Collection<?> locCollection) {
            ArrayList<Object> locResult = new ArrayList<>();
            locCollection.forEach(aChild -> locResult.add(AIcFreezeValue(aChild)));
            return Collections.unmodifiableList(locResult);
        }
        return aValue;
    }
}
