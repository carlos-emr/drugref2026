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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import io.github.carlos_emr.drugref2026.util.JpaUtils;

/**
 * Plugin API framework implementing a provider/consumer pattern for drug reference data.
 *
 * <p>This class acts as a service registry where plugins register functions that "provide"
 * specific attributes (e.g. "interactions_byATC") given a "requires" key (e.g.
 * "_search_interactions"). The {@link #get(String, Vector)} method resolves which registered
 * plugin function can provide a requested attribute and dispatches the call.</p>
 *
 * <h3>Provider/consumer resolution</h3>
 * <ol>
 *   <li>Plugins call {@link #addfunc(Object, String, Vector)} to register a function object,
 *       a "requires" key, and the list of attribute names it can provide.</li>
 *   <li>When {@link #get(String, Vector)} is called with an attribute name and lookup key:
 *       <ol>
 *         <li>The cache is checked first for a previously resolved value.</li>
 *         <li>The {@code provided} map is consulted to find the registered function for
 *             the requested attribute.</li>
 *         <li>The function is invoked (via instanceof dispatch) with the lookup key.</li>
 *         <li>If the "requires" key starts with "_search", a Vector of results is returned
 *             directly; otherwise, a single-result Hashtable is cached and returned.</li>
 *       </ol>
 *   </li>
 * </ol>
 *
 * <p>The framework also supports JPA-based query execution as a fallback when no registered
 * function can service the request.</p>
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

    /** Display name for this API adapter. */
    private String name;
    /** Version string of this API adapter. */
    private String version;
    /** Version date of this API adapter. */
    private String versiondate;
    /** SQL operator for case-insensitive matching (e.g. "ilike" for PostgreSQL). */
    private String notcasesensitive;
    /** Database API identifier. */
    private String dbapi;
    /** Database connection reference. */
    private String db;
    /** Wildcard character for SQL LIKE queries ("%" for PostgreSQL, "*" otherwise). */
    private String wildcard;
    /** Supported languages. */
    private Vector languages = new Vector();
    /** Supported countries. */
    private Vector countires = new Vector();
    /** Database connection parameters (host, port, database, user, pwd, backend). */
    private Hashtable params = new Hashtable();
    /** Registered SQL queries (currently unused). */
    private Hashtable queries = new Hashtable();
    /** Attribute value cache: maps attribute name to [value, key] Vector pairs. */
    private Hashtable cache = new Hashtable();
    /** Provider registry: maps attribute name to {func, requires} Hashtable entries. */
    private Hashtable provided = new Hashtable();
    /** Required attributes registry (currently unused). */
    private Hashtable required = new Hashtable();
    /** Function registry (currently unused; providers are stored in {@code provided}). */
    private Hashtable funcs = new Hashtable();

    /**
     * Default constructor. Creates an unconfigured DrugrefApi instance.
     */
    public DrugrefApi() {
    }

    /**
     * Creates a DrugrefApi configured for a specific database backend.
     *
     * @param host     the database host
     * @param port     the database port
     * @param database the database name
     * @param user     the database user
     * @param pwd      the database password
     * @param backend  the database backend type (e.g. "postgres")
     */
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

    /**
     * Registers a provider function that can supply the specified attributes.
     *
     * <p>For each attribute name in {@code provides}, an entry is created in the provider
     * registry mapping the attribute to the function object and its "requires" key.</p>
     *
     * @param func     the provider function object (e.g. an instance of {@link Holbrook.checkInteractionsATC})
     * @param requires the key type this function requires as input (e.g. "_search_interactions")
     * @param provides the list of attribute names this function can provide
     */
    public void addfunc(Object func, String requires, Vector provides) {

        for (int i = 0; i < provides.size(); i++) {
            Hashtable ha = new Hashtable();
            ha.put("func", func);
            ha.put("requires", requires);
            this.provided.put(provides.get(i), ha);
        }
        //System.out.println("this.provided=" + this.provided.toString());
    }

    /**
     * Placeholder for registering SQL queries as providers. Not yet implemented.
     */
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

    /**
     * Debug print helper (output currently disabled). Prints a key=value pair.
     *
     * @param str the label
     * @param s   the value
     */
    public void p(String str, String s) {
        ////System.out.println(str + "=" + s);
    }

    /**
     * Debug print helper (output currently disabled). Prints a single string.
     *
     * @param str the message to print
     */
    public void p(String str) {
        //System.out.println(str);
    }

    /**
     * Retrieves the value of the specified attribute for the given lookup key.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Check the cache for a previously resolved value matching the key.</li>
     *   <li>Look up the registered provider for the attribute in the {@code provided} map.</li>
     *   <li>Invoke the provider function (dispatched by instanceof check for known types
     *       such as {@link TestFuncSearch} or {@link Holbrook.checkInteractionsATC}).</li>
     *   <li>If the function is not found, fall back to a JPA query.</li>
     *   <li>If the provider's "requires" key starts with "_search", return the results
     *       Vector directly. Otherwise, cache all attributes from the single result
     *       and return the requested attribute's value.</li>
     * </ol>
     *
     * @param attribute the name of the attribute to retrieve (e.g. "interactions_byATC")
     * @param key       a Vector containing the lookup key values (e.g. ATC codes)
     * @return the attribute value (a Hashtable for single results, a Vector for search results),
     *         or an error Hashtable with an "Error" key if the attribute cannot be resolved
     */
    public Object get(String attribute, Vector key) {
        //System.out.println("======in get======");
        //System.out.println("attribute=" + attribute);
        //System.out.println("key=" + key.toString());
        Hashtable haError = new Hashtable();
        String valStr = "Attribute " + attribute + " not available";
        haError.put("Error", valStr);
        // Step 1: Check the cache for a previously resolved value matching this attribute+key
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
        // Step 2: Look up the registered provider for this attribute
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
        // Step 3: Invoke the registered provider function via instanceof dispatch
        Vector results = new Vector();
        try {
            //if service provider available, try first to retrieve the attribute by registered function
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
        // Step 4: If the requires key starts with "_search", return the full results Vector;
        // otherwise, cache and return a single result
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

    /**
     * Placeholder for establishing a database connection. Not yet implemented.
     */
    private void connect() {
    }

    /**
     * Executes a JPA query with the given parameters and returns the results as a Vector.
     *
     * @param query  the JPQL query string
     * @param params a Hashtable of parameter name-value pairs to bind to the query
     * @return a Vector of query results
     */
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

    /**
     * Returns the SQL wildcard character for LIKE queries ("%" for PostgreSQL, "*" otherwise).
     *
     * @return the wildcard string
     */
    public String getWildcard() {
        return this.wildcard;
    }

    /**
     * Returns the display name of this API adapter.
     *
     * @return the name string
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the version string of this API adapter.
     *
     * @return the version string
     */
    public String getVersion() {
        return this.getVersion();
    }

    /**
     * Returns the complete map of registered capabilities (attribute providers).
     *
     * @return the provided Hashtable mapping attribute names to their provider metadata
     */
    public Hashtable listCapabilities() {
        return this.provided;
    }
}

