package eu.algites.pltf.modustro.builder.gradleinit

import groovy.json.JsonSlurper
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap

/** Eagerly detaches JSON values from Groovy LazyMap before any cross-thread publication. */
internal object AIcMaterializedJson {
    @JvmStatic fun AIcParse(aJson: String): Any? = AIcCopy(JsonSlurper().parseText(aJson))

    @JvmStatic fun AIcObject(aJson: String): Map<String, Any?> {
        val locValue = AIcParse(aJson)
        require(locValue is Map<*, *>) { "Expected a JSON object." }
        @Suppress("UNCHECKED_CAST")
        return locValue as Map<String, Any?>
    }

    /** Build a detached, deeply unmodifiable JSON tree before sharing it between threads. */
    @JvmStatic fun AIcCopy(aValue: Any?): Any? = when (aValue) {
        is Map<*, *> -> {
            val locCopy = LinkedHashMap<String, Any?>(aValue.size)
            aValue.forEach { (locKey, locValue) ->
                require(locKey is String) { "JSON object key must be a string." }
                locCopy[locKey] = AIcCopy(locValue)
            }
            Collections.unmodifiableMap(locCopy)
        }
        is List<*> -> {
            val locCopy = ArrayList<Any?>(aValue.size)
            aValue.forEach { locCopy.add(AIcCopy(it)) }
            Collections.unmodifiableList(locCopy)
        }
        is String, is Number, is Boolean, null -> aValue
        else -> throw IllegalArgumentException("Unsupported JSON value type: ${aValue.javaClass.name}")
    }
}
