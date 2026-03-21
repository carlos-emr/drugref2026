/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.carlos_emr.drugref2026.plugin;

import java.util.Hashtable;
import java.util.Vector;

/**
 * Test fixture class used in unit tests for the {@link DrugrefApi} plugin framework.
 *
 * <p>Provides simple stub functions ({@link #testFunc(String)} and {@link #testSearch(String)})
 * that return canned data for testing the provider/consumer resolution and caching logic
 * in {@link DrugrefApi#get(String, Vector)}.</p>
 *
 * @author jackson
 */
public class TestFuncSearch{

    /**
     * Test function that returns a single-entry Vector containing a Hashtable with
     * "attribute" and "attribute2" keys when called with the key "thekey".
     * Returns an empty Vector for any other key.
     *
     * @param key the lookup key
     * @return a Vector of Hashtable results
     */
    public Vector testFunc(String key) {
        Vector vec = new Vector();
        Hashtable ha = new Hashtable();
        if (key.equals("thekey")) {
            ha.put("attribute", "value");
            ha.put("attribute2", "value2");
            vec.addElement(ha);
        }
        return vec;
    }

    /**
     * Test search function that returns a two-entry Vector of Hashtables regardless of
     * the key value. Each Hashtable contains "found" and "key" entries.
     * Used to test the "_search" prefix handling in {@link DrugrefApi#get(String, Vector)}.
     *
     * @param key the lookup key (ignored; always returns the same canned results)
     * @return a Vector of two Hashtable results
     */
    public Vector testSearch(String key) {
        Vector vec = new Vector();
        Hashtable ha1 = new Hashtable();
        Hashtable ha2 = new Hashtable();
        ha1.put("found", "one");
        ha1.put("key", 1);
        ha2.put("found", "two");
        ha2.put("key", 2);
        vec.addElement(ha1);
        vec.addElement(ha2);
        return vec;

    }
}