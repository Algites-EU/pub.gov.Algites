package eu.algites.pltf.modustro.builder.gradleinit;

import groovy.json.internal.LazyMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Checks that parsed JSON can be shared without exposing Groovy's lazy structures. */
public final class AItcMaterializedJsonTest {
    @Test
    public void AIcMaterializesNestedObjectsAndLists() {
        var locJson = "{\"profiles\":{\"a\":{\"value\":42},\"b\":[{\"x\":true}]}}";
        var locRoot = AIcMaterializedJson.AIcObject(locJson);
        AIcAssertEager(locRoot);
        AIcAssertUnmodifiable(locRoot);
        Assert.assertEquals(((Map<?, ?>)((Map<?, ?>)locRoot.get("profiles")).get("a")).get("value"), 42);
    }

    @Test
    public void AIcConcurrentReadsAreStable() throws Exception {
        var locJson = new StringBuilder("{\"profiles\":{");
        for (int locIndex = 0; locIndex < 100; locIndex++) {
            if (locIndex > 0) locJson.append(',');
            locJson.append("\"profile").append(locIndex).append("\":{\"nested\":[{\"id\":")
                .append(locIndex).append("}]}");
        }
        locJson.append("}}");
        Map<String, Object> locRoot = (Map<String, Object>)(Map<?, ?>)AIcMaterializedJson.AIcObject(locJson.toString());
        AIcAssertEager(locRoot);
        AIcAssertUnmodifiable(locRoot);
        var locPool = Executors.newFixedThreadPool(8);
        try {
            var locTasks = new java.util.ArrayList<Callable<Void>>();
            for (int locWorker = 0; locWorker < 16; locWorker++) {
                locTasks.add(() -> {
                    for (int locRepeat = 0; locRepeat < 100; locRepeat++) {
                        var locProfiles = (Map<?, ?>)locRoot.get("profiles");
                        Assert.assertEquals(locProfiles.size(), 100);
                        for (int locIndex = 0; locIndex < 100; locIndex++) {
                            var locProfile = (Map<?, ?>)locProfiles.get("profile" + locIndex);
                            Assert.assertNotNull(locProfile);
                            Assert.assertEquals(((Map<?, ?>)((List<?>)locProfile.get("nested")).get(0)).get("id"), locIndex);
                        }
                    }
                    return null;
                });
            }
            for (var locFuture : locPool.invokeAll(locTasks)) locFuture.get();
        } finally {
            locPool.shutdownNow();
        }
    }

    @Test
    public void AIcPreservesNullsAndInsertionOrder() {
        var locRoot = AIcMaterializedJson.AIcObject("{\"b\":null,\"a\":[null,{\"x\":null}]}");
        Assert.assertEquals(new java.util.ArrayList<>(locRoot.keySet()), List.of("b", "a"));
        Assert.assertTrue(locRoot.containsKey("b"));
        Assert.assertNull(locRoot.get("b"));
        AIcAssertUnmodifiable(locRoot);
    }

    @Test
    public void AIcDeepCopyIsIndependentFromMutableInputs() {
        var locChild = new java.util.LinkedHashMap<String, Object>();
        locChild.put("number", 42);
        var locItems = new java.util.ArrayList<Object>();
        locItems.add(locChild);
        var locSource = new java.util.LinkedHashMap<String, Object>();
        locSource.put("items", locItems);
        var locCopy = (Map<?, ?>)AIcMaterializedJson.AIcCopy(locSource);
        locChild.put("number", 17);
        locItems.clear();
        locSource.clear();
        Assert.assertEquals(((Map<?, ?>)((List<?>)locCopy.get("items")).get(0)).get("number"), 42);
        AIcAssertUnmodifiable(locCopy);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void AIcAssertUnmodifiable(Object aValue) {
        if (aValue instanceof Map<?, ?> locMap) {
            Assert.expectThrows(UnsupportedOperationException.class, () -> ((Map)locMap).put("__mutation_test__", true));
            Assert.expectThrows(UnsupportedOperationException.class, () -> ((Map)locMap).clear());
            locMap.values().forEach(AItcMaterializedJsonTest::AIcAssertUnmodifiable);
        }
        if (aValue instanceof List<?> locList) {
            Assert.expectThrows(UnsupportedOperationException.class, () -> ((List)locList).add("__mutation_test__"));
            Assert.expectThrows(UnsupportedOperationException.class, () -> ((List)locList).clear());
            locList.forEach(AItcMaterializedJsonTest::AIcAssertUnmodifiable);
        }
    }

    private static void AIcAssertEager(Object aValue) {
        Assert.assertFalse(aValue instanceof LazyMap, "Groovy LazyMap leaked after JSON parsing");
        if (aValue instanceof Map<?, ?> locMap) locMap.values().forEach(AItcMaterializedJsonTest::AIcAssertEager);
        if (aValue instanceof List<?> locList) locList.forEach(AItcMaterializedJsonTest::AIcAssertEager);
    }
}
