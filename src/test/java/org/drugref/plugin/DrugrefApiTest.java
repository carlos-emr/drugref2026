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
import junit.framework.TestCase;

/**
 *
 * @author jackson
 */
public class DrugrefApiTest extends TestCase {
    
    public DrugrefApiTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of addfunc method, of class DrugrefApi.
     */
    public void atestAddfunc(DrugrefApi instance) {
        System.out.println("addfunc");
        TestFuncSearch func=new TestFuncSearch();

        String requires = "key";
        Vector provides = new Vector();
        provides.addElement("attribute");
        provides.addElement("attribute2");
        
        instance.addfunc(func, requires, provides);


        TestFuncSearch func2=new TestFuncSearch();
        String requires2 = "_search_key";
        Vector provides2 = new Vector();
        provides2.addElement("found");
        provides2.addElement("key");
        //DrugrefApi instance2 = new DrugrefApi();
        instance.addfunc(func2, requires2, provides2);

        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
    }

    /**
     * Test of addquery method, of class DrugrefApi.
     */
    public void testAddquery() {
        System.out.println("addquery");
        /*
        System.out.println("addquery");
        DrugrefApi instance = new DrugrefApi();
        instance.addquery();
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
         */
    }

    /**
     * Test of get method, of class DrugrefApi.
     */
    public void atestGet() {
        System.out.println("get");
        
        String attribute = "";
        Vector key = new Vector();


        Vector key1=new Vector();
        Vector key2=new Vector();
        Vector key3=new Vector();
        key1.addElement("thekey");
        key2.addElement("thekey");
        key3.addElement("test");

        Object obj1=new Object();
        Object obj2=new Object();
        Object obj3=new Object();

        DrugrefApi api = new DrugrefApi();
        atestAddfunc(api);
    /*
        //add ojbects to this.provided;
        TestFuncSearch func=new TestFuncSearch();

        String requires = "key";
        Vector provides = new Vector();
        provides.addElement("attribute");
        provides.addElement("attribute2");
         a = new DrugrefApi();
        instance.addfunc(func, requires, provides);


        TestFuncSearch func2=new TestFuncSearch();
        String requires2 = "_search_key";
        Vector provides2 = new Vector();
        provides2.addElement("found");
        provides2.addElement("key");
        //DrugrefApi instance2 = new DrugrefApi();
        instance.addfunc(func2, requires2, provides2);
        //finish add objects to this.provided.
*/
        obj1=api.get("attribute", key1);
        if(obj1 instanceof Vector)
            System.out.println("obj1 is a vector");
        else if(obj1 instanceof Hashtable)
            System.out.println("obj1 is a hashtable");

        obj2=api.get("attribute2", key2);
        if(obj2 instanceof Vector)
            System.out.println("obj2 is a vector");
        else if(obj2 instanceof Hashtable)
            System.out.println("obj2 is a hashtable");

        obj3=api.get("found", key3);
        if(obj3 instanceof Vector)
            System.out.println("obj3 is a vector");
        else if(obj3 instanceof Hashtable)
            System.out.println("obj3 is a hashtable");

        String obj1Str = obj1.toString();
        System.out.println("obj1Str="+obj1Str);
 
      //  Object result = instance.get(attribute, key);
      //  assertEquals(expResult, result);
     //   fail("The test case is a prototype.");
    }

    /**
     * Test of getWildcard method, of class DrugrefApi.
     */
    public void p(String str, String s) {
        System.out.println(str + "=" + s);
    }

    public void p(String str) {
        System.out.println(str);
    }

    public void atestGetWildcard() {
       // System.out.println("getWildcard");
       // DrugrefApi instance = new DrugrefApi();
       // String expResult = "";
       // String result = instance.getWildcard();
        Hashtable ha=new Hashtable();

        Holbrook api=new Holbrook();

        Object obj=new Object();
        Vector key=new Vector();
        key.addElement("N02BE01");
        key.addElement("N05BA01");
        key.addElement("N05BA12");

        obj=api.get("interactions_byATC",key);
        p("obj",obj.toString());
        
        ha=(Hashtable)api.legend("effect");
        p("ha_effect",ha.toString());

        ha=(Hashtable)api.legend("significance");
        p("ha_significance",ha.toString());

        ha=(Hashtable)api.legend("evidence");
        p("ha_evidence",ha.toString());

    }

    /**
     * Test of getName method, of class DrugrefApi.
     */
    public void atestGetName() {
        System.out.println("getName");
        DrugrefApi instance = new DrugrefApi();
        String expResult = "";
        String result = instance.getName();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of getVersion method, of class DrugrefApi.
     */
    public void atestGetVersion() {
        System.out.println("getVersion");
        DrugrefApi instance = new DrugrefApi();
        String expResult = "";
        String result = instance.getVersion();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

    /**
     * Test of listCapabilities method, of class DrugrefApi.
     */
    public void atestListCapabilities() {
        System.out.println("listCapabilities");
        DrugrefApi instance = new DrugrefApi();
        Hashtable expResult = null;
        Hashtable result = instance.listCapabilities();
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        fail("The test case is a prototype.");
    }

}
