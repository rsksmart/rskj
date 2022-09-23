/*
 * This file is part of RskJ
 * Copyright (C) 2022 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.datasource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class TransientMapTest {

    private Map<String, String> base;

    private Map<String, String> transientMap;

    @Before
    public void setUp() throws Exception {
        base = new HashMap<>();
        base.put("a", "A");
        base.put("b", "B");

        transientMap = TransientMap.transientMap(base);
    }

    @Test
    public void size() {
        Assert.assertEquals(2, transientMap.size());

        transientMap.put("c", "C");
        Assert.assertEquals(3, transientMap.size());

        transientMap.put("d", "D");
        Assert.assertEquals(4, transientMap.size());

        transientMap.remove("a");
        Assert.assertEquals(3, transientMap.size());

        transientMap.remove("b");
        Assert.assertEquals(2, transientMap.size());

        transientMap.remove("c");
        Assert.assertEquals(1, transientMap.size());

        transientMap.clear();
        Assert.assertEquals("Base map should not have been affected", 2, base.size());
    }

    @Test
    public void isEmpty() {
        Assert.assertFalse(transientMap.isEmpty());

        transientMap.remove("a");
        transientMap.remove("b");
        Assert.assertFalse("TransientMap should not be empty, as base has values", transientMap.isEmpty());

        Assert.assertFalse("Base map should remain intact", base.isEmpty());
    }

    @Test
    public void containsKey() {
        Assert.assertTrue("Entry from base map is present", transientMap.containsKey("a"));
        Assert.assertFalse("Unknown entry is not present", transientMap.containsKey("c"));

        transientMap.put("c", "C");
        Assert.assertTrue(transientMap.containsKey("c"));

        transientMap.put("a", "newA");

        Assert.assertTrue("An entry that existed on base map should remain there after clear", base.containsKey("a"));
        Assert.assertFalse("An added entry should not exist in base after clear", base.containsKey("c"));
    }

    @Test
    public void containsValue() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.containsValue("V"));
    }

    @Test
    public void get() {
        Assert.assertEquals("An entry from base map is present", "A", transientMap.get("a"));
        Assert.assertNull("Unknown entry is not present", transientMap.get("c"));

        transientMap.put("c", "C");
        Assert.assertEquals("A new entry returns its value", "C", transientMap.get("c"));

        transientMap.put("a", "newA");
        Assert.assertEquals("A changed entry returns its new value", "newA", transientMap.get("a"));

        transientMap.remove("a");
        Assert.assertNull("A removed entry returns null", transientMap.get("a"));

        Assert.assertNull("An added entry should return null in base", base.get("c"));
        Assert.assertEquals("A changed entry should return original base value", "A", base.get("a"));
    }

    @Test
    public void put() {
        Assert.assertThrows("Null values are not allowed", IllegalArgumentException.class, () -> transientMap.put("c", null));
        Assert.assertThrows("Null keys are not allowed", IllegalArgumentException.class, () -> transientMap.put(null, "C"));

        String valC = transientMap.put("c", "C");
        Assert.assertNull("New entry result should be null on put", valC);
        Assert.assertEquals("A new entry should return its value on get", "C", transientMap.get("c"));

        String valA = transientMap.put("a", "newA");
        Assert.assertEquals("A one-time changed entry should return its previous value on put", "A", valA);
        Assert.assertEquals("A one-time changed entry should return its new value on get", "newA", transientMap.get("a"));

        String valNewA = transientMap.put("a", "brandNewA");
        Assert.assertEquals("A many-times changed entry should return its previous value on put", "newA", valNewA);
        Assert.assertEquals("A many-times changed entry should return its new value on get", "brandNewA", transientMap.get("a"));

        transientMap.remove("a");
        String valBrandNewA = transientMap.put("a", "afterDeleteA");
        Assert.assertNull("A deleted and re-added entry should return null on put", valBrandNewA);
        Assert.assertEquals("A deleted and re-added entry should return its new value on get", "afterDeleteA", transientMap.get("a"));

        Assert.assertNull("An added entry should return null in base", base.get("c"));
        Assert.assertEquals("A changed entry should return original base value", "A", base.get("a"));
    }

    @Test
    public void putAll() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.putAll(new HashMap<>()));
    }

    @Test
    public void clear() {
        transientMap.put("c", "C");
        transientMap.put("a", "newA");
        transientMap.remove("b");

        transientMap.clear();
        Assert.assertEquals("Modified entry should remain intact in base", "A", base.get("a"));
        Assert.assertEquals("Removed entry should remain intact in base", "B", base.get("b"));
        Assert.assertNull("Added entry should not be in base after clear", base.get("c"));
        Assert.assertNull("Changed entry should no longer exist in transientMap after clear", transientMap.get("a"));
        Assert.assertNull("Added entry should no longer exist in transientMap after clear", transientMap.get("c"));
    }

    @Test
    public void keySet() {
        //TODO https://github.com/rsksmart/rskj/pull/1863/files#r975456885
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.keySet());
    }

    @Test
    public void entrySet() {
        //TODO https://github.com/rsksmart/rskj/pull/1863/files#r975456885
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.entrySet());
    }

    @Test
    public void values() {
        //TODO https://github.com/rsksmart/rskj/pull/1863/files#r975456885
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.values());
    }

    @Test
    public void getOrDefault() {
        Assert.assertEquals("Existing entry defined value", "A", transientMap.getOrDefault("a", "defaultA"));
        Assert.assertEquals("Missing entry default value", "defaultC", transientMap.getOrDefault("c", "defaultC"));
    }

    @Test
    public void forEach() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.forEach((a, b) -> {}));
    }

    @Test
    public void replaceAll() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.replaceAll((a, b) -> a));
    }

    @Test
    public void putIfAbsent() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.putIfAbsent("a", "b"));
    }

    @Test
    public void remove() {
        String nullVal = transientMap.remove("c");
        Assert.assertNull("New entry result should be null on remove", nullVal);

        transientMap.put("c", "C");
        String valC = transientMap.remove("c");
        Assert.assertEquals("An added and removed entry should return its previous value on remove", "C", valC);
        Assert.assertNull("An added and removed entry should return null on get", transientMap.get("c"));

        transientMap.put("a", "newA");
        String valA = transientMap.remove("a");
        Assert.assertEquals("A changed and removed entry should return its previous value on remove", "newA", valA);
        Assert.assertNull("A changed and removed entry should return null on get", transientMap.get("a"));

        String valDoubleC = transientMap.remove("c");
        Assert.assertNull("An entry removed twice should return its previous value on remove", valDoubleC);
        Assert.assertNull("An entry removed twice should return null on get", transientMap.get("c"));

        transientMap.clear();
        Assert.assertNull("A removed entry should return null after clear", transientMap.get("c"));
        Assert.assertEquals("A removed entry should return base value after clear", "A", base.get("a"));
    }

    @Test
    public void removeKeyValue() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.remove("a", "b"));
    }

    @Test
    public void replaceKeyValue() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.replace("a", "b"));
    }

    @Test
    public void replaceKeyOldNew() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.replace("a", "b", "c"));
    }

    @Test
    public void computeIfAbsent() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.computeIfAbsent("a", (a) -> a));
    }

    @Test
    public void computeIfPresent() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.computeIfPresent("a", (a, b) -> a));
    }

    @Test
    public void compute() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.compute("a", (a, b) -> a));
    }

    @Test
    public void merge() {
        Assert.assertThrows(UnsupportedOperationException.class, () -> transientMap.merge("a", "b", (a, b) -> a));
    }
}
