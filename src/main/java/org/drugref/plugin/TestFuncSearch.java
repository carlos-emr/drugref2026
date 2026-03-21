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
package org.drugref.plugin;

import java.util.Hashtable;
import java.util.Vector;

/**
 *
 * @author jackson
 */
public class TestFuncSearch{
    //if __name__ == "__main__":
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

    /*     def testfunc(key):
    if key == 'thekey':
    return [{'attribute':'value', 'attribute2':'value2'}]*/
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