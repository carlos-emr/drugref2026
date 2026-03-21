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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import org.drugref.util.JpaUtils;

/**
 *
 * @author jackson
 */
// public class DrugrefApiBase {
/*       def testsearch(key):
return [{'found': 'one', 'key' : 1}, {'found': 'two', 'key' : 2}]
 */

/* public static void main(String[] args) {
DrugrefApiBase base=new DrugrefApiBase();
DrugrefApiBase.DrugrefApi api=base.new DrugrefApi();
TestFuncSearch test=new TestFuncSearch();
Vector params1=new Vector();
params1.addElement("attribute");
params1.addElement("attribute2");
api.addfunc(test, "key", params1);
Vector params2=new Vector();
params2.addElement("found");
params2.addElement("key");
api.addfunc(test, "_search_key", params2);
Vector key1=new Vector();
Vector key2=new Vector();
Vector key3=new Vector();
key1.addElement("thekey");
key2.addElement("thekey");
key3.addElement("test");

Object obj1=new Object();
Object obj2=new Object();
Object obj3=new Object();

obj1=api.get("attribute", key1);
if(obj1 instanceof Vector)
//System.out.println("obj1 is a vector");
else if(obj1 instanceof Hashtable)
//System.out.println("obj1 is a hashtable");

obj2=api.get("attribute2", key2);
if(obj2 instanceof Vector)
//System.out.println("obj2 is a vector");
else if(obj2 instanceof Hashtable)
//System.out.println("obj2 is a hashtable");

obj3=api.get("found", key3);
if(obj3 instanceof Vector)
//System.out.println("obj3 is a vector");
else if(obj3 instanceof Hashtable)
//System.out.println("obj3 is a hashtable");

}
 */
//public void drugrefApiBase (String host="localhost", Integer port=8123, String database="drugref",String user="drugref",String pwd="drugref",String backend ="postgres") {
public class DrugrefApi {

    private String name;
    private String version;
    private String versiondate;
    private String notcasesensitive;
    private String dbapi;
    private String db;
    private String wildcard;
    private Vector languages = new Vector();
    private Vector countires = new Vector();
    private Hashtable params = new Hashtable();
    private Hashtable queries = new Hashtable();
    private Hashtable cache = new Hashtable();
    private Hashtable provided = new Hashtable();
    private Hashtable required = new Hashtable();
    private Hashtable funcs = new Hashtable();

    public DrugrefApi() {
    }

    public DrugrefApi(String host, Integer port, String database, String user, String pwd, String backend) {
        this.name = "generic adapter";
        this.version = "0.2";
        this.versiondate = "11.01.2005";
        this.params.put("host", host);
        this.params.put("port", port);
        this.params.put("database", database);
        this.params.put("user", user);
        this.params.put("pwd", pwd);
        this.params.put("backend", backend);
        this.dbapi = null;
        this.db = null;
        this.wildcard = "*";
        this.languages.addElement("?");
        this.countires.addElement("?");
        if (backend.equals("postgres")) {
            this.wildcard = "%";
            this.notcasesensitive = "ilike";
        }
    }

    public void addfunc(Object func, String requires, Vector provides) {

        for (int i = 0; i < provides.size(); i++) {
            Hashtable ha = new Hashtable();
            ha.put("func", func);
            ha.put("requires", requires);
            this.provided.put(provides.get(i), ha);
        }
        //System.out.println("this.provided=" + this.provided.toString());
    }

    public void addquery() {
    }
    /*
    public void compareVectors(Vector expected, Vector actual) {
    if (expected.isEmpty() && actual.isEmpty()) {
    } else {
    assertEquals(expected.size(), actual.size());
    String str1 = expected.toString();
    String str2 = actual.toString();
    char[] char1 = str1.toCharArray();
    char[] char2 = str2.toCharArray();
    for (int i = 0; i < char1.length; i++) {
    ////System.out.println("HERE");
    ////System.out.println(char1[i]);
    ////System.out.println(char2[i]);
    assertEquals(char1[i], char2[i]);
    }
    }
    }

    public void compareHashtables(Hashtable expected, Hashtable actual) {
    String str1 = expected.toString();
    String str2 = actual.toString();
    char[] char1 = str1.toCharArray();
    char[] char2 = str2.toCharArray();
    for (int i = 0; i < char1.length; i++) {
    // //System.out.println("HERE");
    ////System.out.println(char1[i]);
    ////System.out.println(char2[i]);
    assertEquals(char1[i], char2[i]);
    }
    }
     */

    public void p(String str, String s) {
        ////System.out.println(str + "=" + s);
    }

    public void p(String str) {
        //System.out.println(str);
    }

    public Object get(String attribute, Vector key) {
        //System.out.println("======in get======");
        //System.out.println("attribute=" + attribute);
        //System.out.println("key=" + key.toString());
        Hashtable haError = new Hashtable();
        String valStr = "Attribute " + attribute + " not available";
        haError.put("Error", valStr);
        try {
            Vector attr = (Vector) this.cache.get(attribute);
           if(attr!=null && attr.size()>1){
                p("attr",attr.toString());
                p("try1");
                if (attr.get(1).equals(key)) {
                    p ("attr[0]=",attr.get(0).toString());
                    return attr.get(0);
                }
           }
        } catch (Exception e) {
            e.printStackTrace();
            //System.out.println("except1");
        }
        Hashtable queryinfo = new Hashtable();
        try {
            //check whether we can provide this attribute in principle (= service provider available)
            Object obj = new Object();
            p("this.provided=", this.provided.toString());
            obj = this.provided.get(attribute);
            p("provided.get atrribute", obj.toString());
            queryinfo = new Hashtable((Hashtable) this.provided.get(attribute));
            p("queryinfo", queryinfo.toString());
        } catch (Exception e) {
            p("error1");
            return haError;
        }
        Vector results = new Vector();
        try {
            //if service provider available, try fiurst to retrieve the attribute by registered function
            Object funcName = queryinfo.get("func");
            if (funcName instanceof TestFuncSearch) {
                p("testFunc first");
                TestFuncSearch tfs = (TestFuncSearch) funcName;
                results = new Vector();
                results = tfs.testFunc((String) key.get(0));
                p("try_results", results.toString());

            /*    p("testSearch first");
                results = new Vector();
                results = tfs.testSearch((String) key.get(0));
                p("try_results", results.toString());
            */
            }

            else if (funcName instanceof Holbrook.checkInteractionsATC){
                p("funcName is an instance of Holbrook.checkInteractionsATC");
         
                Holbrook hb=new Holbrook();
                Holbrook.checkInteractionsATC cia=hb.new checkInteractionsATC();
                results = cia.checkInteractionsATC(key);
                p(results.toString());
            }
        } catch (Exception e) { //need to implement KeyError
            try {
                String query = queryinfo.get("query").toString();
                Hashtable params = new Hashtable();
                params.put(queryinfo.get("requires"), key);
                results = runquery(query, params);

            } catch (Exception exception) {
                results = null;
            }
        }

        if (results==null) {
            Hashtable htError = new Hashtable();
            htError.put("Error", "Not Found");
            return htError;//return a hashtable
        }
        String checkStr = (String) queryinfo.get("requires");
        if (checkStr.startsWith("_search")) {
            p("queryinfo['requires'] starts with '_search'=",results.toString());
            return results;//return a vector
        } else {
            p("results before assertion",results.toString());
            assert results.size() == 1 : results.size();
            Hashtable result = new Hashtable();
            result = new Hashtable((Hashtable) results.get(0));

            p("for loop result");
            Enumeration resultKeys = result.keys();
            while (resultKeys.hasMoreElements()) {
                
                String attrib = resultKeys.nextElement().toString();
                p("attrib", attrib);
                //List valList=new ArrayList();
                //valList.add(result.get(attrib));
                //valList.add(key);
                try {
                    Vector valVec = new Vector();
                    valVec.addElement(result.get(attrib));
                    valVec.addElement(key);
                    this.cache.put(attrib, valVec);
                } catch (Exception e) {
                }
            }
        }

        try {
            Vector attr = (Vector) this.cache.get(attribute);
            Vector attrElm = new Vector((Vector) attr.get(1));
            p("try22 attrElm", attr.toString());

            if (attrElm.equals(key)) {
                p("return 2nd last");
                p(attr.get(0).toString());
                return attr.get(0);
            }
        } catch (Exception e) {
        }
        p("return last");
        return haError;
    }

    private void connect() {
    }

    private Vector runquery(String query, Hashtable params) {
        Vector returnVal = new Vector();
        //start to run query
        EntityManager em = JpaUtils.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        Query queryOne = em.createQuery(query);
        Enumeration enm = params.keys();
        while (enm.hasMoreElements()) {
            String keyHashtable = (String) enm.nextElement();
            queryOne.setParameter(keyHashtable, params.get(keyHashtable));
        }

        List results = new ArrayList(queryOne.getResultList());
        tx.commit();

        Collections.copy(returnVal, results);
        return returnVal;
    }

    public String getWildcard() {
        return this.wildcard;
    }

    public String getName() {
        return this.name;
    }

    public String getVersion() {
        return this.getVersion();
    }

    public Hashtable listCapabilities() {
        return this.provided;
    }
}

