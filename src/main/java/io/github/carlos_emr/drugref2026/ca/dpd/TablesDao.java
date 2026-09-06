/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * Originally: Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
package io.github.carlos_emr.drugref2026.ca.dpd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;
import io.github.carlos_emr.drugref2026.plugin.*;
import io.github.carlos_emr.drugref2026.util.DrugrefProperties;
import io.github.carlos_emr.drugref2026.util.JpaUtils;
import io.github.carlos_emr.drugref2026.util.MiscUtils;

/**
 * Main data access object (DAO) and service repository for the Canadian Drug Product Database (DPD).
 *
 * <p>This Spring {@link Repository} is the central hub for all drug search, lookup, interaction
 * checking, and allergy warning operations. It combines direct JPA/JPQL queries against the DPD
 * tables with a plugin-based architecture for drug-drug interaction checking via the
 * {@link io.github.carlos_emr.drugref2026.plugin.Holbrook Holbrook} plugin.</p>
 *
 * <h3>Plugin Registration Pattern</h3>
 * <p>On construction, the DAO initializes a {@link io.github.carlos_emr.drugref2026.plugin.DrugrefPlugin}
 * which in turn registers the {@link io.github.carlos_emr.drugref2026.plugin.Holbrook Holbrook}
 * drug interaction service. The plugin's capabilities are stored in the {@code services} and
 * {@code provided} hashtables, allowing the {@link #fetch(String, Vector, Vector, boolean)} method
 * to dynamically dispatch attribute lookups (e.g., "interactions_byATC") to the appropriate plugin.</p>
 *
 * <h3>Search Category System</h3>
 * <p>Drug search results are categorized using numeric codes in the {@link CdDrugSearch} table:</p>
 * <ul>
 *   <li>8 = ATC classification code</li>
 *   <li>10 = AHFS classification code (deprecated July 2022)</li>
 *   <li>11 = Generic compound (single ingredient)</li>
 *   <li>12 = Generic (multi-ingredient composite)</li>
 *   <li>13 = Brand name</li>
 *   <li>14 = Individual ingredient</li>
 *   <li>18 = New generic (single ingredient + dosage form)</li>
 *   <li>19 = New generic compound (multi-ingredient + dosage form)</li>
 * </ul>
 *
 * <h3>Search Algorithm</h3>
 * <p>The primary search method {@link #listSearchElement4(String, boolean)} uses a two-phase
 * approach: first a direct prefix/substring match on brand names and new generics (categories
 * 13, 18, 19) excluding common generic manufacturer prefixes (APO-, NOVO-, MYLAN-), then a
 * broader multi-keyword AND search for remaining result slots. Results are capped at
 * {@link #MAX_NO_ROWS} (60) and checked against the inactive products list.</p>
 *
 * @author jackson
 */
@Repository
public class TablesDao {

    Logger logger = MiscUtils.getLogger();
    /** Directory path for plugin loading. */
    private String plugindir;
    /** Display name of this service ("Drugref Service"). */
    private String name;
    /** Service version string. */
    private String version;
    /** Registered plugin instances (e.g., DrugrefPlugin). */
    private Vector plugins = new Vector();
    /** Map of service name to service metadata (version, plugin instance, capabilities). */
    private Hashtable services = new Hashtable();
    /** Map of capability/attribute name to list of service names that provide it. */
    private Hashtable provided = new Hashtable();
    private String db;
    private String user;
    private String pwd;
    /** Cached list of drug codes for inactive/discontinued products, loaded on first search. */
    private List<Integer> inactiveDrugs=new ArrayList();
    /** Maximum number of search result rows returned to the UI. */
    private final int MAX_NO_ROWS=60;

    public TablesDao() {
        //p("=========start tablesdao constructor======");
        this.plugindir = "plugins";
        this.name = "Drugref Service";
        this.version = "1.0";

        this.db = "drugref2";
        this.user = null;
        this.pwd = null;
        // Initialize the DrugrefPlugin, which provides the Holbrook drug interaction service.
        // The register() method returns [name, version, capabilities_map, plugin_instance].
        DrugrefPlugin dp = new DrugrefPlugin();
        this.plugins.addElement(dp);

        // Extract registration info from the plugin
        String name;
        String version;
        Hashtable haProvides = new Hashtable();
        Holbrook thePlugin = new Holbrook();
        name = (String) dp.register().get(0);       // Plugin service name
        version = (String) dp.register().get(1);     // Plugin version
        haProvides = (Hashtable) dp.register().get(2); // Map of capability names to descriptions
        thePlugin = (Holbrook) dp.register().get(3);   // The actual Holbrook plugin instance

        //p("name", name);
        //p("version", version);
        //p("provides", haProvides.toString());
        //p("theplugin", thePlugin.toString());

        // Store the service metadata (version, plugin instance, capabilities) keyed by name
        Hashtable haService = new Hashtable();
        haService.put("version", version);
        haService.put("plugin", thePlugin);
        haService.put("provides", haProvides);

        this.services.put(name, haService);

        // Build the reverse lookup: for each capability the plugin provides,
        // record which service name(s) offer it. This allows fetch() to find
        // the right plugin for a given attribute request.
        Enumeration em = haProvides.keys();
        while (em.hasMoreElements()) {

            String provided = (String) em.nextElement();
            try {
                Vector v = new Vector();
                v = (Vector) this.provided.get(provided);
                v.addElement(name);
                //p("in constructor try");
            } catch (Exception e) {
                Vector nameVec = new Vector();
                nameVec.addElement(name);
                this.provided.put(provided, nameVec);
                //p("in constructor exception");
            }
        }        
        //p("value of this.plugin after constructor", this.plugins.toString());
        //p("value of this.provided after constructor", this.provided.toString());
        //p("=========end tablesdao constructor======");
    }

    /**
     * Returns the display name of this service.
     *
     * @return the service name string
     */
    public String identify() {
        return this.name;
    }

    /**
     * Returns the version string of this service.
     *
     * @return the version string
     */
    public String version() {
        return this.version;
    }

    /**
     * Lists all available plugin services. Currently returns an empty Vector (not yet implemented).
     *
     * @return an empty Vector
     */
    public Vector list_available_services() {
        Vector v = new Vector();
        //TODO: implement
        return v;
    }

    /**
     * Returns all capabilities provided by registered plugins.
     *
     * @return a Hashtable mapping capability/attribute names to lists of providing service names
     */
    public Hashtable list_capabilities() {
        return this.provided;
    }

    private void log(String msg) {
        logger.debug(msg);
    }

    /*public void p(String str, String s) {
        logger.debug(str + "=" + s);
    }

    public void p(String str) {
        logger.debug(str);
    }*/
    //not used
  /*  public Vector fakeFetch(){
    Vector v=new Vector();
    v.addElement("fakeFetch is always happy");
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

    return v;

    }*/

    /**
     * Dispatches an attribute lookup request to registered plugins (primarily the Holbrook
     * drug interaction plugin).
     *
     * <p>This is the main plugin dispatch method. It looks up which registered service provides
     * the requested attribute (e.g., "interactions_byATC"), then delegates to that plugin's
     * {@code get()} method. When {@code feelingLucky} is true (always forced to true internally),
     * returns the first successful result immediately rather than aggregating from all providers.</p>
     *
     * @param attribute the capability/attribute name to look up (e.g., "interactions_byATC")
     * @param key a Vector of parameters to pass to the plugin (e.g., list of ATC codes)
     * @param services optional Vector of specific service names to query; if empty, all services are tried
     * @param feelingLucky if true, return first successful result (always forced true internally)
     * @return the plugin result (typically a Hashtable or Vector), or a Hashtable with "Error" key on failure
     */
    public Object fetch(String attribute, Vector key, Vector services, boolean feelingLucky) {
        //p("===start of fetch===");
        //p("attribute", attribute);
        //p("key", key.toString());

        feelingLucky = true;
        Hashtable results = new Hashtable();
        Hashtable haError = new Hashtable();
        Hashtable ha = new Hashtable();

        Vector providers = new Vector();
        Vector myservices = new Vector();

        try {
            //p("try 1");
            providers = new Vector((Vector) this.provided.get(attribute));
            //p("in fetch, providers", providers.toString());
        } catch (Exception e) {
            e.printStackTrace();
            String val = attribute + " not provided by an registered service";
            haError.put("Error", val);
            return haError;
        }


        if (services.size() > 0) {
            //p("in if");
            Collections.copy(myservices, services);
            //p("myservices and services should be identical");
            //p("myservices", myservices.toString());
            //p("services", services.toString());
        } else {
            //p("in else");
            Enumeration em = this.services.keys();
            while (em.hasMoreElements()) {
                myservices.addElement(em.nextElement());
            }
        }
        //p("myservices", myservices.toString());
        Hashtable module = new Hashtable();
        for (int i = 0; i < myservices.size(); i++) {
            String service = myservices.get(i).toString();
            Hashtable mod = new Hashtable((Hashtable) this.services.get(service));
            module = new Hashtable(mod);
            //p("module", module.toString());
            Holbrook ah = (Holbrook) module.get("plugin");
            Object result = ah.get(attribute, key);
            //call plugin function

            if (!result.equals(null)) {

                if (result instanceof Vector) {
                    Vector vec = (Vector) result;
                    if (vec.size() > 0) {
                        results.put(service, vec);
                    }
                    if (feelingLucky) {
                        //p("results", results.toString());
                        //p("===end of fetch222===");
                        return results;
                    }
                } else if (result instanceof Hashtable) {
                    Hashtable ha2 = (Hashtable) result;
                    if (ha2.size() > 0) {
                        results.put(service, ha2);
                    }
                    if (feelingLucky) {
                        //p("results", results.toString());
                        //p("===end of fetch222===");
                        return results;
                    }
                }
            }
        }
       //p("results", results.toString());
       //p("=== end of fetch===");
        return results;
    }
    /**
     * Dennis Warren Colcamex Resources
     * @param DIN
     * @return
     */
    public Integer getDrugIdFromDIN(String DIN) {
    	// try/finally close: an unclosed EntityManager pins its pooled JDBC
    	// connection for the EM's lifetime, and these lookup helpers are
    	// called per row from the search paths — the leak exhausted the
    	// 32-connection pool within two searches on a live deployment.
    	EntityManager em = JpaUtils.createEntityManager();
    	try {
    		Query drugProductByDIN = em.createNamedQuery("CdDrugProduct.findByDrugIdentificationNumber");
    		drugProductByDIN.setParameter("drugIdentificationNumber", DIN);
    		CdDrugProduct drugProduct = (CdDrugProduct) drugProductByDIN.getSingleResult();
    		return drugProduct.getDrugCode();
    	} finally {
    		JpaUtils.close(em);
    	}
    }
    /**
     * Dennis Warren Colcamex Resources
     * @param drugId
     * @return
     */
    public String getDINFromDrugId(Integer drugId) {
    	EntityManager em = JpaUtils.createEntityManager();
    	try {
    		Query drugProductByDrugId = em.createNamedQuery("CdDrugProduct.findByDrugCode");
    		drugProductByDrugId.setParameter("drugCode", drugId);
    		CdDrugProduct drugProduct = (CdDrugProduct) drugProductByDrugId.getSingleResult();
    		return drugProduct.getDrugIdentificationNumber();
    	} finally {
    		JpaUtils.close(em);
    	}
    }
    
    /**
     * Dennis Warren Colcamex Resources
     * @param DIN
     * @return
     */
    public Integer getDrugpKeyFromDIN( String DIN ) {
    	Integer drugId = getDrugIdFromDIN(DIN);    	
    	if(drugId > 0) {
    		return getDrugpKeyFromDrugId(drugId);
    	}
    	return 0;
    }
    
    /**
     * Dennis Warren Colcamex Resources
     * @param drugId
     * @return
     */
    public Integer getDrugpKeyFromDrugId(Integer drugId) {
    	EntityManager em = JpaUtils.createEntityManager();
    	try {
    		Query drugProductByDrugId = em.createNamedQuery("CdDrugProduct.findByDrugCode");
    		drugProductByDrugId.setParameter("drugCode", drugId);
    		CdDrugProduct drugProduct = (CdDrugProduct) drugProductByDrugId.getSingleResult();
    		return drugProduct.getId();
    	} finally {
    		JpaUtils.close(em);
    	}
    }


    /**
     * Retrieves the list of drug codes for all inactive (discontinued/cancelled) products.
     *
     * <p>Queries the {@link CdInactiveProducts} table and returns all drug codes. This list
     * is cached in {@link #inactiveDrugs} and used during search to flag inactive results.</p>
     *
     * @return a list of drug code integers for inactive products
     */
    public List<Integer> getInactiveDrugs(){
        List<Integer> retLs=new ArrayList();
        EntityManager em=JpaUtils.createEntityManager();
        try{
            String sql="select cip from CdInactiveProducts cip";
            Query q=em.createQuery(sql);
            List<CdInactiveProducts> list=q.getResultList();
            if(list!=null && list.size()>0){
                for(CdInactiveProducts cip:list){
                    retLs.add(cip.getDrugCode());
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            JpaUtils.close(em);
        }
        return retLs;
    }

    /**
     * Retrieves a {@link CdDrugProduct} entity by its DPD drug code.
     *
     * @param drugcode the DPD drug code (as a String that will be parsed)
     * @return the matching CdDrugProduct, or null if not found
     */
    public CdDrugProduct getDrugProduct(String drugcode) {
        EntityManager em = JpaUtils.createEntityManager();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();
        try {
            String queryStr = " select cds from CdDrugProduct cds where cds.drugCode = (:id) ";
            Query query = em.createQuery(queryStr);
            query.setParameter("id", drugcode);
            List<CdDrugProduct> list = query.getResultList();
            if (list.size() > 0) {
                return list.get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        return null;
    }

    /**
     * Retrieves the ATC therapeutic classification for a drug product by its drug code.
     *
     * @param drugcode the DPD drug code (as a String)
     * @return the first matching {@link CdTherapeuticClass} record, or null if not found
     */
    public CdTherapeuticClass getATCCodes(String drugcode) {
        EntityManager em = JpaUtils.createEntityManager();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();
        try {
            String queryStr = " select cds from CdTherapeuticClass cds where cds.drugCode = (:id) ";
            Query query = em.createQuery(queryStr);
            query.setParameter("id", drugcode);
            List<CdTherapeuticClass> list = query.getResultList();
            if (list.size() > 0) {
                return list.get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        return null;
    }

    /**
     * Retrieves a single {@link CdDrugSearch} entry by its primary key.
     *
     * @param id the cd_drug_search table primary key
     * @return the matching search entry, or null if not found
     */
    public CdDrugSearch getSearchedDrug(int id) {
        EntityManager em = JpaUtils.createEntityManager();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();

        try {
            String queryStr = " select cds from CdDrugSearch cds where cds.id = (:id) ";
            Query query = em.createQuery(queryStr);
            query.setParameter("id", id);
            List<CdDrugSearch> list = query.getResultList();

            if (list.size() > 0) {
                return list.get(0);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        return null;

    }

    /**
     * Given a list of DPD drug codes, finds the Active Ingredient (AI) group numbers and
     * returns all "new generic" search entries (categories 18/19) that share those AI groups.
     *
     * <p>This enables expanding a brand-name drug selection into all available generic
     * formulations with the same active ingredient combination.</p>
     *
     * @param listOfDrugCodes a list of DPD drug code strings
     * @return a list of {@link CdDrugSearch} entries for matching new generic formulations
     */
    public List<CdDrugSearch> getListAICodes(List<String> listOfDrugCodes) {
        EntityManager em = JpaUtils.createEntityManager();
        List<CdDrugSearch> ret = new ArrayList();
        try {
            logger.debug("before tx definition");
            //EntityTransaction tx = em.getTransaction();
            logger.debug("after txt definition");
            //tx.begin();
            if (listOfDrugCodes != null) {
                logger.debug("list of drug sizes " + listOfDrugCodes.size());
                for (String s : listOfDrugCodes) {
                    logger.debug(s);
                }
            }
            List<Integer> intListDrugCode = new ArrayList();
            for (int i = 0; i < listOfDrugCodes.size(); i++) {
                intListDrugCode.add(Integer.parseInt(listOfDrugCodes.get(i)));
            }
            //select substring(ai_group_no,1,7) from cd_drug_product where drug_code =
            String queryStr = " select distinct substring(cds.aiGroupNo,1,7) from CdDrugProduct cds where cds.drugCode in (:array) ";
            Query query = em.createQuery(queryStr);
            query.setParameter("array", intListDrugCode);

            logger.debug("before getListAICodes query");

            List<String> results = query.getResultList();
            logger.debug("results " + results.size() + " --- " + results);
            for (String s : results) {
                logger.debug("---" + s);
                ret.addAll(listDrugsbyAIGroup2(s));
            }

            //tx.commit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        return ret;
    }

    /**
     * Finds all active DINs that share the same Active Ingredient group as the given DIN.
     *
     * <p>Looks up the AI group number of the provided DIN, then finds all other drug products
     * in that group that have a current active status. This is used to find therapeutically
     * equivalent products (same ingredients, potentially different brands/manufacturers).</p>
     *
     * @param din the Drug Identification Number to search from
     * @return a list of DIN strings for active products in the same AI group
     */
        public List<String> findLikeDins(String din){
    	 EntityManager em = JpaUtils.createEntityManager();
         //EntityTransaction tx = em.getTransaction();
    	 List<String> dins  = new ArrayList<String>();
         //tx.begin();
         try {
             String queryStr = " select cds from CdDrugProduct cds where cds.drugIdentificationNumber = (:din) ";
             Query query = em.createQuery(queryStr);
             query.setParameter("din", din);
             List<CdDrugProduct> list = query.getResultList();
             if (list.size() > 0) {
                  String aiGroupNo = list.get(0).getAiGroupNo();
                  String queryStr2 = " select cds from CdDrugProduct cds, CdDrugStatus cdStat where cds.aiGroupNo = (:aiGroupNo) and cds.drugCode = cdStat.drugCode and cdStat.currentStatusFlag = 'Y'";
                  Query query2 = em.createQuery(queryStr2);
                  query2.setParameter("aiGroupNo", aiGroupNo);
                  List<CdDrugProduct> activeList = query2.getResultList();
                  for(CdDrugProduct cdp: activeList){
                	  dins.add(cdp.getDrugIdentificationNumber());
                  }
             }
         } catch (Exception e) {
             e.printStackTrace();
         } finally {
             JpaUtils.close(em);
         }
         return dins;
    }
    
    /**
     * Lists distinct ingredient/strength/form combinations for drugs in a given AI group.
     *
     * @param aiGroup the AI group number prefix to match
     * @return a list of Object arrays, each containing [ingredient, strength, strengthUnit, form]
     */
    public List listDrugsbyAIGroup(String aiGroup) {
        EntityManager em = JpaUtils.createEntityManager();
        List<Object[]> results = null;
        try {
            logger.debug("before tx definition");
            //EntityTransaction tx = em.getTransaction();
            logger.debug("after txt definition");
            //tx.begin();
            String queryStr = "select distinct cai.ingredient,cai.strength, cai.strengthUnit,cdf.pharmaceuticalCdForm   from CdDrugProduct cdp, CdForm cdf, CdActiveIngredients cai where cdp.drugCode = cai.drugCode and cdp.drugCode = cdf.drugCode and  cdp.aiGroupNo LIKE :aiGroup order by cai.strength";
            Query query = em.createQuery(queryStr);
            query.setParameter("aiGroup", aiGroup+"%");

            logger.debug("before getListAICodes query");

            results = query.getResultList();
            logger.debug("results " + results.size() + " --- " + results);
            for (Object[] s : results) {
                // ingredient,strength  / cai.strengthUnit cdf.pharmaceuticalCdForm
                logger.debug("---" + s[0] + "---" + s[1] + "---" + s[2] + "---" + s[3] + "---");
            }

            // tx.commit();

        } catch (Exception e) {
            logger.debug("EXCEPTION-HERE");
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        return results;
    }

    /**
     * Lists "new generic" search entries (categories 18 and 19) whose drug code starts with
     * the given AI group prefix.
     *
     * @param aiGroup the AI group number prefix to match
     * @return a list of matching {@link CdDrugSearch} entries
     */
    public List<CdDrugSearch> listDrugsbyAIGroup2(String aiGroup) {
        EntityManager em = JpaUtils.createEntityManager();
        List<CdDrugSearch> results = null;
        try {
            logger.debug("before tx definition for" + aiGroup);
            //EntityTransaction tx = em.getTransaction();
            logger.debug("after txt definition");
            //tx.begin();
            //String queryStr = "select distinct cai.ingredient,cai.strength, cai.strengthUnit,cdf.pharmaceuticalCdForm   from CdDrugProduct cdp, CdForm cdf, CdActiveIngredients cai where cdp.drugCode = cai.drugCode and cdp.drugCode = cdf.drugCode and  cdp.aiGroupNo LIKE '"+ aiGroup + "%' order by cai.strength";//(:aiGroup) ";
            String queryStr = "select cds from CdDrugSearch cds where cds.category in (18,19) and cds.drugCode like :aiGroup ";

            Query query = em.createQuery(queryStr);
            query.setParameter("aiGroup", aiGroup + "%");

            logger.debug("before getListAICodes query");

            results = query.getResultList();
            logger.debug("results " + results.size() + " --- " + results);
//            for (CdDrugSearch s : results) {
//                 // ingredient,strength  / cai.strengthUnit cdf.pharmaceuticalCdForm
//                logger.debug("---" + s[0]+"---"+ s[1]+"---"+ s[2]+"---"+ s[3]+"---");
//            }

            //tx.commit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        return results;
    }

    /**
     * Searches for drugs by name using a multi-keyword AND match, then expands brand-name
     * results (category 13) to include their generic equivalents (categories 18/19).
     *
     * <p>The search string is split on whitespace, and each token must match (LIKE) the drug
     * name. Brand results are expanded via AI group lookup. Generic results are prefixed with
     * "*" to distinguish them from direct matches. Returns a Vector of Hashtables with keys
     * "name", "category", and "id".</p>
     *
     * @param str the search string (may contain multiple space-separated keywords)
     * @return a Vector of Hashtable results, or a single "None found" entry if no matches
     */
    public Vector listSearchElement2(
            String str) {
        logger.debug("before create em in listSearchElement2");
        EntityManager em = JpaUtils.createEntityManager();
        logger.debug("created entity manager");

        str =
                str.replace(",", " ");
        str =
                str.replace("'", "");
        String[] strArray = str.split("\\s+");

        for (int i = 0; i <
                strArray.length; i++) {
            logger.debug(strArray[i]);
        }

//String queryStr = "select cds.id, cds.category, cds.name from CdDrugSearch cds where ";
        String queryStr = "select cds from CdDrugSearch cds where ";
        for (int i = 0; i <
                strArray.length; i++) {
            queryStr = queryStr + "upper(cds.name) like " + "'" + "%" + strArray[i].toUpperCase() + "%" + "'";
            if (i < strArray.length - 1) {
                queryStr = queryStr + " and ";
            }

        }
        List<CdDrugSearch> results = new ArrayList();
        queryStr =
                queryStr + " order by cds.name";
        logger.debug(queryStr);
        try {
            logger.debug("before tx definition");
            //EntityTransaction tx = em.getTransaction();
            logger.debug("after txt definition");
            //tx.begin();
            Query query = em.createQuery(queryStr);
            //  logger.debug("before query");

            results =
                    query.getResultList();

            //tx.commit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }

        if (results.size() > 0) {
            ArrayList drugCodeList = new ArrayList();
            logger.debug("Looping results");
            for (CdDrugSearch result : results) {
                logger.debug("R:" + result.getDrugCode() + " ." + result.getCategory() + ". " + result.getName());
                if (result.getCategory() == 13) {
                    logger.debug(result.getCategory());
                    drugCodeList.add(result.getDrugCode());
                } else {
                    logger.debug("catno 13 " + result.getCategory());
                }

            }

            List<CdDrugSearch> newDrugsList = getListAICodes(drugCodeList);
            Vector vec = new Vector();



            for (CdDrugSearch obj : newDrugsList) {
                //String combinedStr = obj[0]+" "+obj[1]+" "+ obj[2]+" "+ obj[3];
                if (results.contains(obj)) {
                    continue;  //ITEM IS ALREADY IN THE SEARCH BASED ON IT's NAME
                }
                Hashtable ha = new Hashtable();
                ha.put("name", "*" + obj.getName());
                ha.put("category", obj.getCategory());
                ha.put("id", obj.getId());
                vec.addElement(ha);
                //logger.debug("---" + obj[0]+"---"+ obj[1]+"---"+ obj[2]+"---"+ obj[3]+"---");
            }


            for (int i = 0; i <
                    results.size(); i++) {
                Hashtable ha = new Hashtable();
                ha.put("name", results.get(i).getName());
                ha.put("category", results.get(i).getCategory());
                ha.put("id", results.get(i).getId());
                vec.addElement(ha);
            }

            logger.debug(results);
            return (vec);
        } else {
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("id", "0");
            ha.put("category", "");
            ha.put("name", "None found");
            defaultVec.addElement(ha);
            return defaultVec;
        }

    }
    
    /**
     * Retrieves the DIN of the first (oldest by last update date) drug product in a given AI group.
     *
     * @param aiGroupNo the AI group number to look up
     * @return the DIN string of the first product in the group, or null if none found
     */
    public String getFirstDinInAIGroup(String aiGroupNo) {
    	String q1="select cdp from CdDrugProduct cdp where cdp.aiGroupNo = (:groupNo) order by cdp.lastUpdateDate";
    	// This helper runs once per candidate row inside the search expansion,
    	// so the missing close here alone consumed most of the connection pool
    	// on a single list_search_element3 call.
    	EntityManager em = JpaUtils.createEntityManager();
         try{
             Query query=em.createQuery(q1);
             query.setParameter("groupNo", aiGroupNo);
             List rs = query.getResultList();
             if(rs.size()>0) {
            	 return ((CdDrugProduct)rs.get(0)).getDrugIdentificationNumber();
             }
         }catch(Exception e){
             e.printStackTrace();
         }finally{
             JpaUtils.close(em);
         }
         return null;
    }

    /**
     * Primary drug search method using a two-phase query strategy for performance.
     *
     * <p><b>Phase 1 (direct match):</b> Searches brand names and new generics (categories 13, 18, 19)
     * using a single LIKE pattern on the search string. Excludes common generic manufacturer
     * prefixes (APO-, NOVO-, MYLAN-) from results. Limited to {@link #MAX_NO_ROWS}.</p>
     *
     * <p><b>Phase 2 (multi-keyword match):</b> If Phase 1 returns fewer than MAX_NO_ROWS results,
     * performs a broader multi-keyword AND search (splitting the input on whitespace) for the
     * remaining slots. Excludes results already found in Phase 1 to avoid duplicates.</p>
     *
     * <p>Both phases check each result against the inactive drugs list and flag inactive products.
     * For category 18/19 results, the AI group prefix is extracted from the composite drug code
     * (format: "aiGroupNo+formCode") to check inactive status via the first DIN in the group.</p>
     *
     * <p>If the property {@code sort_down_mfg_tagged_generics} is "true", results are sorted
     * so that manufacturer-prefixed generic names (APO-, TEVA-, SANDOZ-, etc.) appear after
     * non-prefixed names.</p>
     *
     * @param str the user's search input string
     * @param rightOnly if true, matches from the beginning of the name (no left wildcard) — in
     *                  Phase 2 only the first keyword is prefix-anchored and later keywords
     *                  (strengths, forms) may match anywhere; if false, all keywords match
     *                  anywhere in the name (left and right wildcards)
     * @return a Vector of Hashtable results with keys "name", "category", "id", "isInactive"
     */
    public Vector listSearchElement4(String str, boolean rightOnly){
        //logger.debug("before create em in listSearchElement4");
        // Normalize the search input: uppercase, strip commas and apostrophes, trim
        // (leading whitespace would defeat the Phase-1 prefix match in rightOnly mode)
        String matchKey=str.toUpperCase();
        matchKey=matchKey.replace(",", " ");
        matchKey=matchKey.replace("'", "");
        matchKey=matchKey.trim();
        List<CdDrugSearch> results1=new ArrayList(); // Phase 1: direct match results
        List<CdDrugSearch> results2=new ArrayList(); // Phase 2: multi-keyword match results
        int max_rows_for_result2=0;
        boolean onlyDirectMatch=false; // Set true when Phase 1 fills MAX_NO_ROWS, skipping Phase 2
        // Lazy-load the inactive drugs list on first search
        if(inactiveDrugs.size()==0)
            inactiveDrugs=getInactiveDrugs();
        //logger.debug("inactiveDrugs size ="+inactiveDrugs.size());
        EntityManager em = JpaUtils.createEntityManager();
        //logger.debug("created entity manager");
        // Phase 1 query: direct prefix/substring match on brand and new generic names,
        // excluding manufacturer-tagged generics (APO-, NOVO-, MYLAN- prefixes)
        String q1="select cds from CdDrugSearch cds where upper(cds.name) like '"+ ((rightOnly)?"":"%") +""+matchKey+"%' and cds.name NOT IN (select cc.name from CdDrugSearch cc where upper(cc.name) like 'APO-%' or upper(cc.name) like 'NOVO-%' or upper(cc.name) like 'MYLAN-%' ) and (cds.category=13 or cds.category=18 or cds.category=19)  order by cds.name";
       //logger.debug("q1 ="+q1);
        String q2="select cdss.name from CdDrugSearch cdss where upper(cdss.name) like '"+((rightOnly)?"":"%")+""+matchKey+"%'";
        try{
            Query query=em.createQuery(q1);
            query.setMaxResults(MAX_NO_ROWS);
            results1=query.getResultList();
        }catch(Exception e){
            e.printStackTrace();
        }
        // If Phase 1 already fills the max rows, skip Phase 2 entirely
        if(results1.size()>=MAX_NO_ROWS){
                onlyDirectMatch=true;
        }else{
            // Calculate remaining slots available for Phase 2 results
            max_rows_for_result2=MAX_NO_ROWS-results1.size();
        }

        if(results1.size()>0){
            // De-duplicate Phase 1 results: remove entries with identical names
            // (also normalizes double spaces to single spaces before comparison)
            List<String> temp=new ArrayList<String>();
            List<CdDrugSearch> r=new ArrayList();
            for(CdDrugSearch c:results1){
                //logger.debug("c="+c.getName());
                //logger.debug("temp="+temp);
                String name=c.getName();
                if(!temp.contains(name)){
                    //replace double space with single space.
                    name=name.replace("  ", " ");
                    if(!temp.contains(name)){
                        temp.add(name);
                        r.add(c);
                    }
                }
                else{   ;            }
            }
            results1=r;
        }

        if(!onlyDirectMatch){
                    // Phase 2: multi-keyword AND search for remaining result slots.
                    // Splits the original search string into tokens and requires all to match.
                    str = str.replace(",", " ");
                    str = str.replace("'", "");
                    // Trim before splitting: leading whitespace (typed, or left by the comma
                    // replacement above) would otherwise yield an empty first token, and in
                    // rightOnly mode the start-of-name anchor would land on that empty token
                    // instead of the first real keyword.
                    String[] strArray = str.trim().split("\\s+");

                //    for (int i = 0; i < strArray.length; i++) {
                //        logger.debug(strArray[i]);
                //    }

            //String queryStr = "select cds.id, cds.category, cds.name from CdDrugSearch cds where ";
                    String queryStr = "select cds from CdDrugSearch cds where (";
                    for (int i = 0; i < strArray.length; i++) {
                        // In rightOnly mode only the FIRST token is anchored to the start of the
                        // name; later tokens (strengths, forms) must match anywhere, otherwise a
                        // multi-word query like "RAMIP 10" becomes unsatisfiable (no name can
                        // start with both "RAMIP" and "10").
                        boolean anchorToStart = rightOnly && i == 0;
                        queryStr = queryStr + "upper(cds.name) like " + "'" + (anchorToStart ? "" : "%") + strArray[i].toUpperCase() + "%" + "'";
                        if (i < strArray.length - 1) {
                            queryStr = queryStr + " and ";
                        }

                    }

                    queryStr = queryStr + ") and cds.name NOT IN (select cc.name from CdDrugSearch cc where upper(cc.name) like 'APO-%' or upper(cc.name) like 'NOVO-%' or upper(cc.name) like 'MYLAN-%' ) "
                            + "and (cds.category=13 or cds.category=18 or cds.category=19) and cds.name NOT IN ("+q2+") order by cds.name";//q2 prevents duplication of result.
                    //logger.debug(queryStr);
                    try {                        
                        Query query = em.createQuery(queryStr);
                        //logger.debug("before query");
                        query.setMaxResults(max_rows_for_result2);
                        results2 = query.getResultList();
                        //logger.debug("after query");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    
        }
        //logger.debug("number of results1="+results1.size()+";results2="+results2.size());
        if (results1.size() > 0 || results2.size() > 0) {
            

            Vector vec = new Vector();
        try{
               for (CdDrugSearch result : results1) {

                        boolean isInactive=false;
                        String drugCode=result.getDrugCode();                        
                        if(MiscUtils.isStringToInteger(drugCode)){
                            //check if result is inactive.
                            if(inactiveDrugs.contains(Integer.parseInt(drugCode)))
                                    isInactive=true;
                        }
                        if(result.getCategory().intValue() == 18 || result.getCategory().intValue() == 19) {
                        	if(drugCode.indexOf("+")!=-1) {
                        		drugCode = drugCode.substring(0,drugCode.indexOf("+"));
                        	}
                        	String din=null;
                        	if((din=getFirstDinInAIGroup(drugCode))!=null) {
                        		if(this.getInactiveDate(din).size()>0) {
                        			isInactive=true;
                        		}
                        	}
                        }
                        Hashtable ha = new Hashtable();
                        ha.put("name", result.getName());
                        ha.put("category", result.getCategory());
                        ha.put("id", result.getId());
                        ha.put("isInactive", isInactive);
                        vec.addElement(ha);
                   // }
                }
               for (CdDrugSearch result : results2) {

                        boolean isInactive=false;
                        String drugCode=result.getDrugCode();
                        if(MiscUtils.isStringToInteger(drugCode)){
                            //check if result is inactive.
                            if(inactiveDrugs.contains(Integer.parseInt(drugCode)))
                                    isInactive=true;
                        }
                        if(result.getCategory().intValue() == 18) {
                        	if(drugCode.indexOf("+")!=-1) {
                        		drugCode = drugCode.substring(0,drugCode.indexOf("+"));
                        	}
                        	String din=null;
                        	if((din=getFirstDinInAIGroup(drugCode))!=null) {
                        		if(this.getInactiveDate(din).size()>0) {
                        			isInactive=true;
                        		}
                        	}
                        }
                        Hashtable ha = new Hashtable();
                        ha.put("name", result.getName());
                        ha.put("category", result.getCategory());
                        ha.put("id", result.getId());
                        ha.put("isInactive", isInactive);
                        vec.addElement(ha);
                   // }
                }
                logger.debug("NUMBER OF DRUGS RETURNED: " + vec.size());

                if("true".equals(DrugrefProperties.getInstance().getProperty("sort_down_mfg_tagged_generics", "false"))) {
	                //sort the generics with no manufacturer code up
	                Collections.sort(vec,new Comparator<Hashtable>() {
	
						public int compare(Hashtable o1, Hashtable o2) {
							String name = (String)o1.get("name");
							String name2 = (String)o2.get("name");
							
							//Pattern p = Pattern.compile();
							String regex = "^(ABBOTT|ACCEL|ACT|ALTI|AMI|APO|AURO|AVA|BCI|BIO|CCP|CO|DOM|ECL|EURO|FTP|GD|GEN|JAA|JAMP|KYE|LIN|MANDA|MAR|MED|MINT|MYL|NAT|NG|NOVO|NTP|NU|PENTA|PHL|PMS|PMSC|PRIVA|PRO|PHL|Q|RAN|RATIO|RHO|RIVA|SANDOZ|SDZ|SEPTA|TEVA|VAL|VAN|ZYM|Dom|Ratio|Riva|Teva)(\\-|\\s).*";
							if(!name.matches(regex) && !name2.matches(regex)) {
								return name.compareTo(name2);
							}
							
							if(name.matches(regex) && !name2.matches(regex)) {
								return 1;
							}
							
							if(name.matches(regex) && name2.matches(regex)) {
								return name.compareTo(name2);
							}
							
							if(!name.matches(regex) && name2.matches(regex)) {
								return -1;
							}
							
							//should never get to here
							return 0;
						}
	                	
	                });
				}
            }catch(Exception e){
                e.printStackTrace();
            }finally{
                JpaUtils.close(em);
            }
            return (vec);
        } else {
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("id", "0");
            ha.put("category", "");
            ha.put("name", "None found");
            defaultVec.addElement(ha);
            return defaultVec;
        }


    }
    /**
     * Searches for drugs by name with inactive status checking. Superseded by the faster
     * {@link #listSearchElement4(String, boolean)} and no longer actively used.
     *
     * <p>Performs a multi-keyword AND search on drug names (categories 13, 18, 19), excluding
     * common generic prefixes (APO-, NOVO-, MYLAN-). Checks each result against the inactive
     * products list.</p>
     *
     * @param str the search string
     * @return a Vector of Hashtable results with keys "name", "category", "id", "isInactive"
     * @deprecated Use {@link #listSearchElement4(String, boolean)} instead for better performance
     */
    public Vector listSearchElement3(String str) {
        //logger.debug("before create em in listSearchElement3");
        if(inactiveDrugs.size()==0)
            inactiveDrugs=getInactiveDrugs();
        //logger.debug("inactiveDrugs size ="+inactiveDrugs.size());
        EntityManager em = JpaUtils.createEntityManager();
        //logger.debug("created entity manager");

        str = str.replace(",", " ");
        str = str.replace("'", "");
        String[] strArray = str.split("\\s+");


//String queryStr = "select cds.id, cds.category, cds.name from CdDrugSearch cds where ";
        String queryStr = "select cds from CdDrugSearch cds where ";
        for (int i = 0; i < strArray.length; i++) {
            queryStr = queryStr + "upper(cds.name) like " + "'" + "%" + strArray[i].toUpperCase() + "%" + "'";
            if (i < strArray.length - 1) {
                queryStr = queryStr + " and ";
            }

        }
        List<CdDrugSearch> results = new ArrayList();
        queryStr = queryStr + " order by cds.name";
        //logger.debug(queryStr);
        try {
            //logger.debug("before tx definition");
            // EntityTransaction tx = em.getTransaction();
            //logger.debug("after txt definition");
            //tx.begin();
            Query query = em.createQuery(queryStr);
            //logger.debug("before query");

            results = query.getResultList();

            //tx.commit();

        } catch (Exception e) {
            e.printStackTrace();
        } 

        if (results.size() > 0) {
            ArrayList drugCodeList = new ArrayList();
            //logger.debug("Looping results  updated in 3");

            Vector vec = new Vector();
        try{
               for (CdDrugSearch result : results) {
                    //for (int i = 0; i < results.size(); i++) {
                    if (result.getName().startsWith("APO-") || result.getName().startsWith("NOVO-") || result.getName().startsWith("MYLAN-")) {
                        /*
                        APO-
                        DOM-
                        NOVO-
                        PHL-
                        PMS-
                        RAN-
                        RATIO-
                        TARO-
                         */
                        continue;
                    }
                    if (result.getCategory() == 13 || result.getCategory() == 18 || result.getCategory() == 19) {
                        boolean isInactive=false;
                        String drugCode=result.getDrugCode();
                        if(MiscUtils.isStringToInteger(drugCode)){
                            //check if result is inactive.
                            if(inactiveDrugs.contains(Integer.parseInt(drugCode)))
                                    isInactive=true;
                        }
                        Hashtable ha = new Hashtable();
                        ha.put("name", result.getName());
                        ha.put("category", result.getCategory());
                        ha.put("id", result.getId());
                        ha.put("isInactive", isInactive);
                        vec.addElement(ha);
                    }
                }
                //logger.debug("NUMBER OF DRUGS RETURNED: " + vec.size());
                /*for (int i = 0; i < vec.size(); i++) {
                    logger.debug("vec=" + vec.get(i));
                }*/
            }catch(Exception e){
                e.printStackTrace();
            }finally{
                JpaUtils.close(em);
            }
            return (vec);
        } else {
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("id", "0");
            ha.put("category", "");
            ha.put("name", "None found");
            defaultVec.addElement(ha);
            return defaultVec;
        }

    }

    /**
     * Basic drug search by name using multi-keyword AND matching across all categories.
     *
     * <p>This is the simplest search method -- no inactive checking, no category filtering,
     * no manufacturer prefix exclusion. Splits the search string on whitespace and requires
     * all tokens to match (case-insensitive LIKE). Returns results ordered by name.</p>
     *
     * @param str the search string (may contain multiple space-separated keywords)
     * @return a Vector of Hashtable results with keys "name", "category", "id"
     */
    public Vector listSearchElement(String str) {
        //EntityManagerFactory emf = (EntityManagerFactory) SpringUtils.getBean("entityManagerFactory");
        //EntityManager em = emf.createEntityManager();
        //logger.debug("before create em in listSearchElement");
        EntityManager em = JpaUtils.createEntityManager();
        //logger.debug("created entity manager");

        str = str.replace(",", " ");
        str = str.replace("'", "");
        String[] strArray = str.split("\\s+");

        for (int i = 0; i < strArray.length; i++) {
            logger.debug(strArray[i]);
        }

        //String queryStr = "select cds.id, cds.category, cds.name from CdDrugSearch cds where ";
        String queryStr = "select cds from CdDrugSearch cds where ";
        for (int i = 0; i < strArray.length; i++) {
            queryStr = queryStr + "upper(cds.name) like " + "'" + "%" + strArray[i].toUpperCase() + "%" + "'";
            if (i < strArray.length - 1) {
                queryStr = queryStr + " and ";
            }
        }
        List<CdDrugSearch> results = new ArrayList();
        queryStr = queryStr + " order by cds.name";
        //logger.debug(queryStr);
        try {
            //logger.debug("before tx definition");
            //EntityTransaction tx = em.getTransaction();
            //logger.debug("after txt definition");
            //tx.begin();
            Query query = em.createQuery(queryStr);
            //    logger.debug("before query");

            results = query.getResultList();

            //tx.commit();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        if (results.size() > 0) {
            Vector vec = new Vector();
            for (int i = 0; i < results.size(); i++) {
                Hashtable ha = new Hashtable();
                ha.put("name", results.get(i).getName());
                ha.put("category", results.get(i).getCategory());
                ha.put("id", results.get(i).getId());
                vec.addElement(ha);
            }

            //logger.debug(results);
            return (vec);
        } else {
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("id", "0");
            ha.put("category", "");
            ha.put("name", "None found");
            defaultVec.addElement(ha);
            return defaultVec;
        }
    }

    /**
     * Searches for drugs by name filtered by route of administration.
     *
     * <p>First queries the {@link CdRoute} table to find drug codes matching the specified
     * route(s), then searches the {@link CdDrugSearch} table for drugs matching the name
     * keywords that are also in the route-filtered set.</p>
     *
     * @param str the drug name search string
     * @param route the route of administration to filter by (e.g., "ORAL", "TOPICAL")
     * @return a Vector of Hashtable results with keys "name", "category", "id"
     */
    public Vector listSearchElementRoute(String str, String route) {
        EntityManager em = JpaUtils.createEntityManager();

        str = str.replace(",", " ");
        str = str.replace("'", "");
        String[] strArray = str.split("\\s+");

        route = route.replace(",", " ");
        route = route.replace("'", "");
        String[] routeArray = route.split("\\s+");

        String queryOne = "select cr.drugCode from CdRoute cr where ";
        for (int i = 0; i < routeArray.length; i++) {
            queryOne = queryOne + "upper(cr.routeOfAdministration) like " + "'" + "%" + routeArray[i].toUpperCase() + "%" + "'";
            if (i < routeArray.length - 1) {
                queryOne = queryOne + " or ";
            }
        }

        List resultOne = new ArrayList();
        List<CdDrugSearch> resultTwo = new ArrayList();
        //logger.debug("queryOne :" + queryOne);
        try {
            //EntityTransaction tx = em.getTransaction();
            //tx.begin();
            Query queryFirst = em.createQuery(queryOne);
            //@SuppressWarnings("unchecked")
            resultOne = queryFirst.getResultList();
            ArrayList<String> strListOne = new ArrayList<String>();

            for (int i = 0; i < resultOne.size(); i++) {
                String element = resultOne.get(i).toString();
                strListOne.add(element);
            }
            //logger.debug(strListOne);

            //String queryTwo = "select cds.id, cds.category, cds.name from CdDrugSearch cds where ";
            String queryTwo = "select cds from CdDrugSearch cds where ";
            for (int i = 0; i < strArray.length; i++) {
                queryTwo = queryTwo + " upper(cds.name) like " + "'" + "%" + strArray[i].toUpperCase() + "%" + "' ";
                if (i < strArray.length - 1) {
                    queryTwo = queryTwo + " and ";
                }
            }
            //logger.debug("queryTwo: " + queryTwo);
            Query querySecond = em.createQuery(queryTwo + " and cds.drugCode in (:array) order by cds.name");
            querySecond.setParameter("array", strListOne);
            resultTwo = querySecond.getResultList();
            //tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        //logger.debug("results:" + resultTwo);
        if (resultTwo.size() > 0) {
            Vector vec = new Vector();
            for (int i = 0; i < resultTwo.size(); i++) {
                Hashtable ha = new Hashtable();
                ha.put("name", resultTwo.get(i).getName());
                ha.put("category", resultTwo.get(i).getCategory());
                ha.put("id", resultTwo.get(i).getId());
                vec.addElement(ha);
            }

            return (vec);
        } else {
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("id", "0");
            ha.put("category", "");
            ha.put("name", "None found");
            defaultVec.addElement(ha);
            return defaultVec;
        }
    }

    /**
     * Lists all brand-name products associated with a given search entry.
     *
     * <p>The behavior depends on the category of the selected drug:</p>
     * <ul>
     *   <li><b>Category 8 (ATC):</b> Finds all drug products with that ATC code, returns their
     *       brand names from the search table.</li>
     *   <li><b>Category 10 (AHFS):</b> Finds all drug products with that AHFS number, returns
     *       their brand names.</li>
     *   <li><b>Category 11/12 (Generic):</b> Uses the {@link LinkGenericBrand} table to find
     *       all brand-name products linked to the generic entry.</li>
     * </ul>
     *
     * @param drugID the {@link CdDrugSearch} primary key (id) as a String
     * @return a Vector of Hashtable results with keys "name", "category", "id"
     */
    public Vector listBrandsFromElement(String drugID) {
        EntityManager em = JpaUtils.createEntityManager();

        String drugCode = "";
        Integer category;
        List<CdDrugSearch> results = new ArrayList();

        try {
            //EntityTransaction tx = em.getTransaction();
            //tx.begin();


            Query brandQuery = em.createQuery("select cds from CdDrugSearch cds where cds.id = :id");
            brandQuery.setParameter("id", Integer.valueOf(drugID.trim()));
            CdDrugSearch cdsResult = (CdDrugSearch) brandQuery.getSingleResult();


            //logger.debug(cdsResult.getDrugCode() + " -- " + cdsResult.getName() + " :: " + cdsResult.getCategory());

            if (cdsResult != null) {
                drugCode = cdsResult.getDrugCode();
                category = cdsResult.getCategory();
            } else {
                Vector vec = new Vector();
                return vec;
            }

            if (category == 8) {

                Query queryOne = em.createQuery("select tc from CdTherapeuticClass tc where tc.tcAtcNumber=(:drugCode)");
                queryOne.setParameter("drugCode", Integer.parseInt(drugCode.trim()));
                List<CdTherapeuticClass> listOne = queryOne.getResultList();

                for (int i = 0; i < listOne.size(); i++) {
                    Query query = em.createQuery("select sd   from CdDrugSearch sd where (:tcDrugCode)= sd.drugCode order by sd.name");
                    query.setParameter("tcDrugCode", listOne.get(i).getDrugCode());
                    CdDrugSearch cds = (CdDrugSearch) query.getSingleResult();

                    results.add(cds);

                }
            } else if (category == 10) {
                Query queryOne = em.createQuery("select tc from CdTherapeuticClass tc where tc.tcAhfsNumber=(:drugCode)");
                queryOne.setParameter("drugCode", drugCode);
                List<CdTherapeuticClass> listOne = queryOne.getResultList();

                for (int i = 0; i < listOne.size(); i++) {
                    Query query = em.createQuery("select sd  from CdDrugSearch sd where (:tcDrugCode)= sd.drugCode order by sd.name");
                    query.setParameter("tcDrugCode", listOne.get(i).getDrugCode().toString());
                    CdDrugSearch cds = (CdDrugSearch) query.getSingleResult();
                    results.add(cds);

                }
            } else {//category=11 or 12
                //logger.debug("category is 11 or 12");
                Query query = em.createQuery("select sd from LinkGenericBrand lgb, CdDrugSearch sd where lgb.id = (:drugCode) and lgb.drugCode = sd.drugCode order by sd.name");
                query.setParameter("drugCode", Integer.parseInt(drugCode.trim()));
                //logger.debug("drugCode=" + Integer.parseInt(drugCode.trim()));
                results = query.getResultList();

            }
        } catch (Exception e) {
            //logger.debug("in listBrandsFromElement exception");
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        if (results.size() > 0) {
            Vector vec = new Vector();
            for (int j = 0; j < results.size(); j++) {
                Hashtable ha = new Hashtable();
                ha.put("name", results.get(j).getName());
                ha.put("category", results.get(j).getCategory());
                ha.put("id", results.get(j).getId());
                vec.addElement(ha);
            }

            //logger.debug(vec);
            return (vec);
        } else {
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("name", "None found");
            ha.put("category", "");
            ha.put("id", "0");
            defaultVec.addElement(ha);
            return defaultVec;
        }
    }

    /**
     * Searches for drugs by name, restricted to specific search categories. Convenience
     * overload that uses both left and right wildcards.
     *
     * @param str the drug name search string
     * @param cat a Vector of Integer category codes to include in results
     * @return a Vector of Hashtable results with keys "name", "category", "id"
     */
    public Vector listSearchElementSelectCategories(String str, Vector cat) {
    	return listSearchElementSelectCategories(str,cat,true,true);
    }
    
    /**
     * Searches for drugs by name, restricted to specific search categories, with configurable
     * wildcard placement.
     *
     * @param str the drug name search string
     * @param cat a Vector of Integer category codes to include in results
     * @param wildcardLeft if true, adds a leading "%" wildcard for substring matching
     * @param wildcardRight if true, adds a trailing "%" wildcard for prefix matching
     * @return a Vector of Hashtable results with keys "name", "category", "id"
     */
    public Vector listSearchElementSelectCategories(String str, Vector cat, boolean wildcardLeft, boolean wildcardRight) {
        EntityManager em = JpaUtils.createEntityManager();

        str = str.replace(",", " ");
        str = str.replace("'", "");
        String[] strArray = str.split("\\s+");
        /*for (int i = 0; i < strArray.length; i++) {
            logger.debug(strArray[i]);
        }*/

        String queryStr = "select cds from CdDrugSearch cds where ";

        for (int i = 0; i < strArray.length; i++) {
            queryStr = queryStr + "upper(cds.name) like " + "'" + ((wildcardLeft)?"%":"") + strArray[i].toUpperCase() + ((wildcardRight)?"%":"") + "'";
            queryStr = queryStr + " and ";
        }

        queryStr = queryStr + "(";

        for (int i = 0; i < cat.size(); i++) {
            queryStr = queryStr + "cds.category= :cat" + i;
            if (i < (cat.size() - 1)) {
                queryStr = queryStr + " or ";
            }
        }
        queryStr = queryStr + ") order by cds.category, cds.name";
        //logger.debug(queryStr);

        List<CdDrugSearch> results = new ArrayList();

        try {
            //EntityTransaction tx = em.getTransaction();
            //tx.begin();
            Query query = em.createQuery(queryStr);
            for (int i = 0; i < cat.size(); i++) {
                query.setParameter("cat" + i, Integer.valueOf(String.valueOf(cat.get(i))));
            }
            //@SuppressWarnings("unchecked")
            results = query.getResultList();
            //tx.commit();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        if (results.size() > 0) {
            Vector vec = new Vector();
            for (int j = 0; j < results.size(); j++) {
                Hashtable ha = new Hashtable();
                ha.put("name", results.get(j).getName());
                ha.put("category", results.get(j).getCategory());
                ha.put("id", results.get(j).getId());
                vec.addElement(ha);
            }
            //logger.debug(vec);
            return (vec);
        } else {
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("id", "0");
            ha.put("category", "");
            ha.put("name", "None found");
            defaultVec.addElement(ha);
            return defaultVec;
        }
    }

    /**
     * Retrieves the generic drug name(s) associated with a given brand-name search entry.
     *
     * <p>Uses the {@link LinkGenericBrand} table to find the generic drug entries (categories
     * 11/12) that correspond to the brand product identified by the given search table ID.</p>
     *
     * @param drugID the {@link CdDrugSearch} primary key (id) as a String
     * @return a Vector of Hashtable results with keys "name", "category", "id"
     */
    public Vector getGenericName(String drugID) {
        //logger.debug("in getGenericName.drugID=" + drugID);
        Vector vec = new Vector();
        EntityManager em = JpaUtils.createEntityManager();
        List<CdDrugSearch> results = new ArrayList();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();
        Query queryOne = em.createQuery("select lgb.id from LinkGenericBrand lgb ,CdDrugSearch cds where lgb.drugCode = cds.drugCode and cds.id=(:ID)");
        List resultDrugCode = new ArrayList();
        try {
            queryOne.setParameter("ID", Integer.parseInt(drugID));
            resultDrugCode = queryOne.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //logger.debug("size of list=" + resultDrugCode.size());
        if (resultDrugCode == null) {
            logger.debug("resultDrugCode is null!");
        }
        if (resultDrugCode.size() > 0) {
            try {
                Query queryTwo = em.createQuery("select cds from CdDrugSearch cds where cds.drugCode in (:drugCodeList)");
                queryTwo.setParameter("drugCodeList", MiscUtils.toStringArrayList(resultDrugCode));
                results = queryTwo.getResultList();

                //logger.debug("in if");
                //logger.debug(results);
                for (int j = 0; j < results.size(); j++) {
                    Hashtable ha = new Hashtable();
                    ha.put("name", results.get(j).getName());
                    ha.put("category", results.get(j).getCategory());
                    ha.put("id", results.get(j).getId());
                    vec.addElement(ha);
                }
                for (int i = 0; i < vec.size(); i++) {
                    logger.debug("if:vec.get(i)=" + vec.get(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                JpaUtils.close(em);
            }
            return vec;
        } else {
            //logger.debug("in else , getGenericName.");
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("id", "0");
            ha.put("name", "None found");
            defaultVec.addElement(ha);
            return defaultVec;
        }
    }
    /**
     * Retrieves the discontinuation/cancellation date(s) for a drug identified by its DIN.
     *
     * <p>Looks up the DIN in the {@link CdInactiveProducts} table. If found, the product has
     * been discontinued and the history date(s) are returned. An empty Vector indicates the
     * product is still active.</p>
     *
     * @param pKey the Drug Identification Number (DIN) to check
     * @return a Vector of Date objects representing discontinuation dates, empty if active
     */
    public Vector getInactiveDate(String pKey) {
        logger.debug("in getInactiveDate");
        EntityManager em = JpaUtils.createEntityManager();
        Vector vec = new Vector();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();
        //Query queryOne = em.createQuery("select cds from CdInactiveProducts cds where cds.drugIdentificationNumber = (:din)");
        try {
            Query queryOne = em.createNamedQuery("CdInactiveProducts.findByDrugIdentificationNumber");
            queryOne.setParameter("drugIdentificationNumber", pKey);

            List<CdInactiveProducts> inactiveCodes = queryOne.getResultList();
            if (inactiveCodes != null) {
                for (CdInactiveProducts inp : inactiveCodes) {
                    vec.add(inp.getHistoryDate());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        return vec;
    }

    /**
     * Retrieves the pharmaceutical dosage form(s) for a drug identified by its search table ID.
     *
     * @param pKey the {@link CdDrugSearch} primary key (id) as a String
     * @return a Vector of Hashtable results with keys "pharmaceutical_cd_form" and "pharm_cd_form_code"
     */
    public Vector getForm(String pKey) {
        EntityManager em = JpaUtils.createEntityManager();
        List<CdForm> results = new ArrayList();
        Vector vec = new Vector();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();
        Query queryOne = em.createQuery("select cds.drugCode from CdDrugSearch cds where cds.id = (:ID)");
        queryOne.setParameter("ID", Integer.parseInt(pKey));
        List resultDrugCode = queryOne.getResultList();



        if (resultDrugCode != null) {
            try {
                Query queryTwo = em.createQuery("select cf from CdForm cf where cf.drugCode in (:drugCodeList)");
                queryTwo.setParameter("drugCodeList", MiscUtils.toIntegerArrayList(resultDrugCode));
                results = queryTwo.getResultList();

                logger.debug(results);

                for (int i = 0; i < results.size(); i++) {
                    Hashtable ha = new Hashtable();
                    ha.put("pharmaceutical_cd_form", results.get(0).getPharmaceuticalCdForm());
                    ha.put("pharm_cd_form_code", results.get(0).getPharmCdFormCode());
                    vec.addElement(ha);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                JpaUtils.close(em);
            }
            return vec;
        } else {
            Vector defaultVec = new Vector();
            Hashtable ha = new Hashtable();
            ha.put("pharm_cd_form_code", "");
            ha.put("pharmaceutical_cd_form", "None found");
            defaultVec.addElement(ha);
            return defaultVec;
        }
    }

    /**
     * Lists the AHFS drug class(es) for a set of drug search entry IDs.
     *
     * <p>For each provided search entry ID, looks up the drug code, finds its AHFS therapeutic
     * classification number(s) via the {@link CdTherapeuticClass} table, then retrieves the
     * corresponding AHFS class entries from the search table.</p>
     *
     * @param Dclass a Vector of drug search entry IDs
     * @return a Vector of Hashtable results with keys "id_class", "name", "id_drug"
     */
    public Vector listDrugClass(Vector Dclass) {
        EntityManager em = JpaUtils.createEntityManager();
        List results = new ArrayList();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();

        String q1 = "select cds from  CdDrugSearch cds where ";
        for (int i = 0; i < Dclass.size(); i++) {
            q1 = q1 + " cds.id = :id" + i;
            if (i < Dclass.size() - 1) {
                q1 = q1 + " or ";
            }
        }
        logger.debug(q1);
        Vector vec = new Vector();
        // The EntityManager is closed in the finally below for EVERY exit path.
        // Previously createQuery/getResultList sat outside any try and the only
        // close was reachable when listOne was non-empty, so an empty result —
        // or a query that threw — leaked a pooled JDBC connection per call, an
        // unauthenticated route to exhausting the pool. Binding the ids as
        // parameters widened the set of inputs that throw here (a non-numeric id
        // now fails fast instead of building invalid HQL), which makes closing
        // properly a prerequisite rather than a nicety. trim() keeps
        // whitespace-padded numerics working as they did before parameterizing.
        try {
            Query queryOne = em.createQuery(q1);
            for (int i = 0; i < Dclass.size(); i++) {
                queryOne.setParameter("id" + i, Integer.valueOf(String.valueOf(Dclass.get(i)).trim()));
            }
            List<CdDrugSearch> listOne = queryOne.getResultList();
            if (listOne.size() > 0) {
                for (int i = 0; i < listOne.size(); i++) {
                    Integer id = listOne.get(i).getId();
                    Query queryTwo = em.createQuery("select cds2 from CdDrugSearch cds2 where cds2.drugCode in (select tc.tcAhfsNumber from   CdTherapeuticClass tc where tc.drugCode = (:cdIntDrugCode)) order by cds2.id");
                    queryTwo.setParameter("cdIntDrugCode", Integer.parseInt(listOne.get(i).getDrugCode().trim()));
                    List<CdDrugSearch> listTwo = queryTwo.getResultList();
                    for (CdDrugSearch resultTwo : listTwo) {
                        Hashtable ha = new Hashtable();
                        ha.put("id_class", resultTwo.getId());
                        ha.put("name", resultTwo.getName());
                        ha.put("id_drug", id);
                        vec.add(ha);
                    }
                }
                return vec;
            }
        } catch (Exception e) {
            // Consistent with the sibling lookups in this DAO: a failed query
            // degrades to the "None found" shape rather than propagating an
            // XML-RPC fault (and, before this, leaking the connection with it).
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        Vector defaultVec = new Vector();
        Hashtable ha = new Hashtable();
        ha.put("pharm_cd_form_code", "");
        ha.put("pharmaceutical_cd_form", "None found");
        defaultVec.addElement(ha);
        return defaultVec;
    }

    /**
     * Checks a drug (identified by ATC code) against a patient's allergy list and returns
     * any matching allergy warnings.
     *
     * <p>Each allergy carries a category type that says which part of the drug reference the
     * allergen name lives in, and the check for a category is: does the drug being prescribed
     * share that part of the reference with the allergen?</p>
     * <ul>
     *   <li><b>Type 8 (ATC class):</b> resolve the allergen name to its ATC code(s) and check
     *       whether the drug's ATC code sits under any of them. ATC is a hierarchy encoded in the
     *       code, and an allergen can name any level of it, so this is a prefix match.</li>
     *   <li><b>Type 10 (AHFS class):</b> look up the AHFS numbers carrying that class name, then
     *       check whether the drug's ATC code appears under any of them.</li>
     *   <li><b>Type 11/12 (generic / compound):</b> resolve the generic name through the link
     *       table to brand drug codes, then check for a shared therapeutic class.</li>
     *   <li><b>Type 13 (brand):</b> resolve the brand name to drug codes and check for a shared
     *       therapeutic class.</li>
     *   <li><b>Type 14 (ingredient):</b> not yet implemented.</li>
     *   <li><b>Type 0 (free text):</b> the allergen was typed by a clinician rather than picked
     *       from the reference, so it carries no category at all. Its description is resolved
     *       against the reference's own allergen names and then checked as whichever categories
     *       it turns out to name. See below.</li>
     * </ul>
     *
     * <p><b>Why type 0 is checked and not skipped.</b> CARLOS writes typeCode 0 for every
     * allergy added through its "Custom Allergy" button, and its demonstration dataset seeds
     * PENICILLINS allergies that way. Falling through to the "no match yet" branch meant those
     * allergies were silently never checked: prescribing amoxicillin to a patient recorded as
     * allergic to PENICILLINS produced no warning at all, which is the dangerous direction for
     * this function to fail in. Resolution is by EXACT name (the column collation makes that
     * case-insensitive) against {@code cd_drug_search}, the same table the picker offers those
     * names from — deliberately whole-name and not a fuzzy or substring match, because a false
     * allergy alert on a prescription is its own harm and every near-miss spelling would produce
     * one. (The hierarchy prefix matching inside categories 8 and 10 is a different thing: it
     * walks the reference's own classification codes, not the typed text.)</p>
     *
     * <p><b>Data dependency.</b> Category 10 is the only check that reads AHFS, which Health
     * Canada deprecated in July 2022 — {@link CdTherapeuticClass} notes {@code tcAhfsNumber} may
     * be null in newer extracts. An allergen the reference knows ONLY as an AHFS class therefore
     * stops resolving once an import carries no AHFS, and is reported in "missing" rather than
     * warned on. That is the safe direction (it says "not checked", not "no allergy"), and
     * category 8 covering the ATC hierarchy is what keeps class-level allergies working without
     * AHFS; but an import that drops AHFS should be paired with a check that the class allergens
     * clinicians actually record still resolve.</p>
     *
     * @param atcCode the ATC code of the drug being prescribed
     * @param allergies a Vector of Hashtables, each with keys "type", "description", "id"
     * @return a Vector containing a single Hashtable with keys "warnings" (Vector of allergy IDs
     *         the drug is contraindicated against) and "missing" (Vector of allergy IDs that were
     *         NOT CHECKED, for any reason: the allergen name is one the reference does not carry,
     *         the allergy arrived with no description, or its only category has no implementation
     *         here). "missing" is deliberately not "checked and clear" — an ID absent from both
     *         lists is the only answer that means this drug was compared against that allergy and
     *         did not match.
     */
    public Vector getAllergyWarnings(String atcCode, Vector allergies) {

        logger.debug("in getAllergyWarnings: atcCode="+atcCode+",allergies="+allergies);
        Vector results = new Vector();
        Vector vec = new Vector();
        Hashtable ha = new Hashtable();
        Vector missing = new Vector();

        if (atcCode == null || atcCode.matches("") || atcCode.matches("null")) {
            ha.put("warnings", results);
            ha.put("missing", missing);
            vec.add(ha);
            return vec;
        }
        EntityManager em = JpaUtils.createEntityManager();
        try {
            Enumeration e = allergies.elements();
            while (e.hasMoreElements()) {
                Hashtable alleHash = new Hashtable((Hashtable) e.nextElement());
                String aType = (String) alleHash.get("type");
                String aDesc = (String) alleHash.get("description");
                String aId = (String) alleHash.get("id");

                if (aDesc == null || aDesc.trim().isEmpty()) {
                    logger.debug("allergy id " + aId + " has no description; nothing to check");
                    missing.add(aId);
                    continue;
                }
                aDesc = aDesc.trim();

                // A free-text allergen names no category, so ask the reference which categories
                // carry that name and check the drug against each of them. A typed allergen is
                // checked as exactly the one category it declares.
                List<Integer> categories;
                if (isFreeTextAllergyType(aType)) {
                    categories = resolveFreeTextAllergenCategories(em, aDesc);
                    if (categories.isEmpty()) {
                        logger.debug("free-text allergen '" + aDesc + "' is not a name this reference knows; not checked");
                        missing.add(aId);
                        continue;
                    }
                    logger.debug("free-text allergen '" + aDesc + "' resolved to categories " + categories);
                } else {
                    categories = new ArrayList<Integer>();
                    try {
                        categories.add(Integer.valueOf(aType.trim()));
                    } catch (NumberFormatException nfe) {
                        logger.debug("allergy id " + aId + " has unparseable type '" + aType + "'; not checked");
                        missing.add(aId);
                        continue;
                    }
                }

                boolean warned = false;
                boolean resolvedAny = false;
                for (Integer category : categories) {
                    Boolean match = matchesAllergyCategory(em, atcCode, category.intValue(), aDesc);
                    if (match == null) {
                        // The allergen name does not exist in that part of the reference, so this
                        // category checked nothing. Only report "missing" if no category could.
                        continue;
                    }
                    resolvedAny = true;
                    if (match.booleanValue()) {
                        warned = true;
                        break;
                    }
                }

                if (warned) {
                    logger.debug(atcCode + " is in allergy group " + aDesc);
                    results.add(aId);
                } else if (!resolvedAny) {
                    missing.add(aId);
                } else {
                    logger.debug(atcCode + " is NOT in allergy group " + aDesc);
                }
            }
        } catch (Exception e) {
            logger.error("getAllergyWarnings failed for atcCode=" + atcCode, e);
        } finally {
            JpaUtils.close(em);
        }

        ha.put("warnings", results);
        ha.put("missing", missing);
        vec.add(ha);
        return vec;
    }

    /**
     * Returns true when an allergy's category type means "no category" — the allergen was typed
     * as free text rather than picked out of the drug reference. CARLOS sends "0" for those; a
     * null, blank or non-numeric type is treated the same way rather than being dropped.
     *
     * @param aType the allergy's category type as sent by the caller
     * @return true when the description has to be resolved against the reference before it can
     *         be checked
     */
    private boolean isFreeTextAllergyType(String aType) {
        if (aType == null || aType.trim().isEmpty()) {
            return true;
        }
        String t = aType.trim();
        if ("0".equals(t) || "null".equals(t)) {
            return true;
        }
        // Anything that is not a category number names no category either. Falling through to
        // Integer.valueOf() here would report the allergy as unresolvable without ever trying its
        // description, which is the silent-skip this method exists to prevent.
        try {
            Integer.valueOf(t);
            return false;
        } catch (NumberFormatException nfe) {
            return true;
        }
    }

    /**
     * Resolves a free-text allergen description to the drug-reference categories that carry a
     * name exactly equal to it.
     *
     * <p>Whole-name match only. This feeds a prescription-time safety alert, where a wrong match
     * is a false alarm the prescriber learns to dismiss — so a name is either one the reference
     * knows or it is reported as unresolved. Case is folded in the query rather than left to the
     * column collation: DrugRef also runs on PostgreSQL (see
     * {@code DrugrefProperties.isPostgres()}), where JPQL {@code =} is case-sensitive and a
     * clinician's "Penicillins" would not match the reference's "PENICILLINS".</p>
     *
     * @param em the entity manager to query with
     * @param aDesc the trimmed free-text allergen description
     * @return the distinct categories naming that allergen; empty when the reference does not
     *         know the name
     */
    @SuppressWarnings("unchecked")
    private List<Integer> resolveFreeTextAllergenCategories(EntityManager em, String aDesc) {
        Query q = em.createQuery(
                "select distinct cds.category from CdDrugSearch cds where lower(cds.name) = lower(:aDesc)");
        q.setParameter("aDesc", aDesc);
        List<Integer> categories = q.getResultList();
        return categories == null ? new ArrayList<Integer>() : categories;
    }

    /**
     * Checks the drug being prescribed against one allergen category.
     *
     * @param em the entity manager to query with
     * @param atcCode the ATC code of the drug being prescribed
     * @param category the allergen category to check as (8, 10, 11, 12, 13 or 14)
     * @param aDesc the allergen name
     * @return TRUE when the drug falls in the allergen's group, FALSE when it demonstrably does
     *         not, and null when this category could not resolve the name at all (an unsupported
     *         category, or a name that exists nowhere in that part of the reference) — the caller
     *         reports that as "missing" rather than as "checked and clear"
     */
    @SuppressWarnings("unchecked")
    private Boolean matchesAllergyCategory(EntityManager em, String atcCode, int category, String aDesc) {
        if (category == 8) {
            // ATC is a hierarchy encoded in the code itself, and an allergen name can sit at any
            // level of it: J01CF is "BETA-LACTAMASE RESISTANT PENICILLINS" and J01CF02 is the
            // cloxacillin under it, each its own row. Comparing the prescribed drug's own
            // description would only ever match a leaf, so an allergy to the class name never
            // warned on any drug in that class. Resolve the name to its ATC code(s) and prefix
            // match, exactly as category 10 does with AHFS numbers.
            Query atcNumbers = em.createQuery(
                    "select distinct tc.tcAtcNumber from CdTherapeuticClass tc where lower(tc.tcAtc) = lower(:aDesc)");
            atcNumbers.setParameter("aDesc", aDesc);
            List<String> allergenAtcNumbers = atcNumbers.getResultList();
            if (allergenAtcNumbers == null || allergenAtcNumbers.isEmpty()) {
                return null;
            }
            for (String allergenAtc : allergenAtcNumbers) {
                if (allergenAtc != null && atcCode.startsWith(allergenAtc)) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }

        if (category == 10) {
            Query queryAhfsNumber = em.createQuery(
                    "select distinct tc.tcAhfsNumber from CdTherapeuticClass tc where lower(tc.tcAhfs) = lower(:aDesc)");
            queryAhfsNumber.setParameter("aDesc", aDesc);
            List<String> ahfsNumbers = queryAhfsNumber.getResultList();
            if (ahfsNumbers == null || ahfsNumbers.isEmpty()) {
                return null;
            }
            for (String ahfsNumber : ahfsNumbers) {
                // The AHFS number is a hierarchy: 08:12.16 (penicillins) is the parent of
                // 08:12.16.08 (aminopenicillins), which is where amoxicillin actually sits.
                // Prefix-matching the number is what makes a class-level allergy cover the
                // drugs filed under its subclasses.
                Query query = em.createQuery(
                        "select tc.tcAtcNumber from CdTherapeuticClass tc where tc.tcAtcNumber = (:atcCode) and tc.tcAhfsNumber like :ahfsPrefix");
                query.setParameter("atcCode", atcCode);
                query.setParameter("ahfsPrefix", ahfsNumber + "%");
                if (!query.getResultList().isEmpty()) {
                    return Boolean.TRUE;
                }
            }
            return Boolean.FALSE;
        }

        if (category == 11 || category == 12) {
            // For categories 11 and 12 the search-table id doubles as the generic-brand link id.
            Query drugCodesForAtc = em.createQuery(
                    "select distinct tc.drugCode from CdTherapeuticClass tc where tc.tcAtcNumber = (:atcCode)");
            drugCodesForAtc.setParameter("atcCode", atcCode);
            List<Integer> drugCodes = drugCodesForAtc.getResultList();
            if (drugCodes == null || drugCodes.isEmpty()) {
                return null;
            }
            List<String> drugCodeStrings = new ArrayList<String>();
            for (Integer drugCode : drugCodes) {
                drugCodeStrings.add(drugCode.toString());
            }
            Query query = em.createQuery(
                    "select distinct tc.tcAtcNumber from CdDrugSearch cds, CdTherapeuticClass tc, LinkGenericBrand lgb "
                            + "where tc.tcAtcNumber = (:atcCode) and lower(cds.name) = lower(:aDesc) and cds.id = lgb.id and lgb.drugCode in (:drugCodes)");
            query.setParameter("atcCode", atcCode);
            query.setParameter("aDesc", aDesc);
            query.setParameter("drugCodes", drugCodeStrings);
            if (!query.getResultList().isEmpty()) {
                return Boolean.TRUE;
            }
            // An empty result here means either "this drug is not that generic" or "the reference
            // has never heard of this generic". Only the first is a checked-and-clear answer.
            Query known = em.createQuery(
                    "select count(cds) from CdDrugSearch cds where cds.category in (11, 12) and lower(cds.name) = lower(:aDesc)");
            known.setParameter("aDesc", aDesc);
            return ((Number) known.getSingleResult()).longValue() > 0 ? Boolean.FALSE : null;
        }

        if (category == 13) {
            Query brandDrugCodes = em.createQuery(
                    "select cds.drugCode from CdDrugSearch cds where cds.category = 13 and lower(cds.name) = lower(:aDesc)");
            brandDrugCodes.setParameter("aDesc", aDesc);
            List<String> codes = brandDrugCodes.getResultList();
            if (codes == null || codes.isEmpty()) {
                return null;
            }
            List<Integer> codeInts = new ArrayList<Integer>();
            for (String code : codes) {
                try {
                    codeInts.add(Integer.valueOf(code));
                } catch (NumberFormatException nfe) {
                    logger.debug("skipping non-numeric brand drug code '" + code + "' for " + aDesc);
                }
            }
            if (codeInts.isEmpty()) {
                return null;
            }
            // The brand's drug codes are already resolved above, so the name is fully spent:
            // keeping CdDrugSearch in this query only cross-joins every row sharing that name
            // back over the result for no added predicate.
            Query query = em.createQuery(
                    "select distinct tc.tcAtcNumber from CdTherapeuticClass tc "
                            + "where tc.tcAtcNumber = (:atcCode) and tc.drugCode in (:codes)");
            query.setParameter("atcCode", atcCode);
            query.setParameter("codes", codeInts);
            return query.getResultList().isEmpty() ? Boolean.FALSE : Boolean.TRUE;
        }

        // Category 14 (active ingredient) and the AI-derived generic categories 18/19 have no
        // implementation here; report them as unresolved rather than as "checked and clear".
        logger.debug("allergen category " + category + " is not implemented; '" + aDesc + "' not checked");
        return null;
    }

    /**
     * Retrieves detailed drug information for a "made" generic entry (categories 18/19) by
     * finding an example product in the given AI group with the specified dosage form.
     *
     * <p>Since new generic entries do not directly correspond to a single DPD drug code,
     * this method finds the first real drug product in the AI group that has the specified
     * form code, then delegates to {@link #getDrugByDrugCode(String, String, boolean)}.</p>
     *
     * @param groupno the AI group number
     * @param formCode the pharmaceutical form code
     * @param html whether to format results as HTML (currently unused)
     * @return a Vector of Hashtable results with full drug details, or null if no match
     */
    public Vector getMadeGenericExample(String groupno, String formCode, boolean html) {
        logger.debug("in getMadeGenericExample");
        String drugCode = "";

        EntityManager em = JpaUtils.createEntityManager();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();
        try {
            Query queryDrugCode = em.createQuery("select cdp.drugCode from CdDrugProduct cdp ,CdForm cf where cdp.aiGroupNo = (:groupNo) and cf.pharmCdFormCode = (:formCode) and cdp.drugCode = cf.drugCode");
            queryDrugCode.setParameter("groupNo", groupno);
            queryDrugCode.setParameter("formCode", Integer.parseInt(formCode));

            List<Integer> drugCodes = queryDrugCode.getResultList();
            if (drugCodes == null || drugCodes.size() < 1) {
                return null;
            }
            Integer obj = drugCodes.get(0);
            drugCode = "" + obj;
            logger.debug("now going to call getDrug with drugCode " + drugCode);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        return getDrugByDrugCode(drugCode, formCode, html);
    }

    /**
     * Retrieves comprehensive drug information for a given search table entry.
     *
     * <p>Resolves the search table ID to a DPD drug code, then assembles a complete drug
     * profile including: active ingredient names, ATC code, brand/product name, DIN
     * (regional identifier), component details (name, strength, unit), and dosage form.</p>
     *
     * @param pKey the {@link CdDrugSearch} primary key (id) as a String
     * @param html whether to format results as HTML (currently unused)
     * @return a Vector containing a single Hashtable with keys "name", "atc", "product",
     *         "regional_identifier", "components" (Vector of Hashtables), "drugForm"
     */
    public Vector getDrug(String pKey, boolean html) {
        String productId = "";
        String origId = pKey;
        Vector returnRows = new Vector();
        if (origId == null) {
            logger.debug("origId is null.");
        }
        EntityManager em = JpaUtils.createEntityManager();
        try {

            //EntityTransaction tx = em.getTransaction();
            //tx.begin();
            Query queryDrugCode = em.createQuery("select cds.drugCode from CdDrugSearch cds where cds.id=(:pKey)");
            queryDrugCode.setParameter("pKey", Integer.parseInt(pKey));
            String resultDrugCode = "";
            resultDrugCode = (String) queryDrugCode.getSingleResult();
            if (!resultDrugCode.matches("")) {
                pKey = resultDrugCode;
            }
            
            // pKey now points to a standard drug code
            // active Ingredient Names List
            Query queryName = em.createQuery("select cai.ingredient from CdActiveIngredients cai where cai.drugCode=(:pKey)");
            queryName.setParameter("pKey", Integer.parseInt(pKey));
            
            // Therapeutic Class
            Query queryAtc = em.createQuery("select ctc.tcAtcNumber from CdTherapeuticClass ctc where ctc.drugCode = (:pKey)");
            queryAtc.setParameter("pKey", Integer.parseInt(pKey));
            
            // point back to original drugRef drug id
            // Get the original drug Name
            Query queryProduct = em.createQuery("select cds.name from CdDrugSearch cds where cds.id=(:origId)");
            queryProduct.setParameter("origId", Integer.parseInt(origId));
            
            // drug product regional information
            Query queryRegionalIdentifier = em.createQuery("select cdp from CdDrugProduct cdp where cdp.drugCode = (:pKey)");
            queryRegionalIdentifier.setParameter("pKey", Integer.parseInt(pKey));
            
            // Active Ingredients Full info
            Query queryComponent = em.createQuery("select cai from CdActiveIngredients cai where cai.drugCode=(:pKey)");
            queryComponent.setParameter("pKey", Integer.parseInt(pKey));
            
            // Drug form
            Query queryForm = em.createQuery("select  cf from CdForm cf where cf.drugCode=(:pKey)");
            queryForm.setParameter("pKey", Integer.parseInt(pKey));

            String name = ""; // List of Active Ingredients
            String atc = ""; // Therapeutic Class
            String product = ""; // Original Drug name (from search table)
            String ProductId = ""; // Regional Drug Code 
            String regionalIdentifier = ""; // Regional Identifier

            // Active Ingredient Names
            List resultName = queryName.getResultList(); 
            if (resultName.size() > 0) {
            	StringBuilder sb = new StringBuilder();
            	boolean first = true;
                for (int i = 0; i < resultName.size(); i++) {
                	if(!first){
                		sb.append("/ ");
                	}
                    sb.append( (String) resultName.get(i) );
                    first = false;
                }
                name = sb.toString();
            }

            // List of Therapeutic Class Id's
            // what if there is more than one?
            List resultAtc = queryAtc.getResultList(); 
            if (resultAtc.size() > 0) {
                for (int i = 0; i < resultAtc.size(); i++) {
                    atc = (String) resultAtc.get(i);
                }
            }

            // Original Drug name. 
            // could be generic or brand name. Not clear here.
            // what if there is more than one
            List resultProduct = queryProduct.getResultList();
            if (resultProduct.size() > 0) {
                for (int i = 0; i < resultProduct.size(); i++) {
                    product = (String) resultProduct.get(i);
                }
            }

            // Regional Product information
            List<CdDrugProduct> resultRegionalIdentifier = queryRegionalIdentifier.getResultList();
            if (resultRegionalIdentifier.size() > 0) {
                for (int i = 0; i < resultRegionalIdentifier.size(); i++) {
                    productId = resultRegionalIdentifier.get(i).getDrugCode().toString();
                    regionalIdentifier = resultRegionalIdentifier.get(i).getDrugIdentificationNumber().toString();
                }
            }
            
            //logger.debug("reginoal Identifier " + regionalIdentifier);
            String ingredient = ""; // Ingredient Name (same as Name variable above)
            String strength = ""; // Ingredient Strength
            String strengthUnit = ""; // Strength Unit

            // Active Ingredients
            Hashtable ha = null;
            Vector component = new Vector();           
            List<CdActiveIngredients> resultComponent = queryComponent.getResultList();
            
            if (resultComponent.size() > 0) {
                for (int i = 0; i < resultComponent.size(); i++) {
                	
                    ingredient = resultComponent.get(i).getIngredient();
                    strength = resultComponent.get(i).getStrength();
                    strengthUnit = resultComponent.get(i).getStrengthUnit();
                
                    ha = new Hashtable();
	                ha.put("name", ingredient);
	                ha.put("strength", Float.valueOf(strength.trim()).floatValue());
	                ha.put("unit", strengthUnit);
	                
	                component.addElement(ha);
                }
            }
            
            // Drug Form
            StringBuilder drugForm = new StringBuilder();
            List<CdForm> resultForm = queryForm.getResultList();
            if (resultForm.size() > 0) {
            	for(CdForm f:resultForm) {
            		String temp = f.getPharmaceuticalCdForm();
            		if(drugForm.length()>0) {
            			drugForm.append(",");
            		}
            		drugForm.append(temp);            		
            	}                
            }
            
            
            Hashtable ha2 = new Hashtable();
            ha2.put("name", name);
            ha2.put("atc", atc);
            ha2.put("product", product);
            ha2.put("regional_identifier", regionalIdentifier);
            ha2.put("components", component);
            ha2.put("drugForm", drugForm.toString());
            returnRows.addElement(ha2);
           // logger.debug("returned when cat=13:"+returnRows);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }

        return returnRows;
    }

    /**
     * Retrieves comprehensive drug information by DPD drug code and dosage form code.
     *
     * <p>Similar to {@link #getDrug(String, boolean)} but uses a drug code directly rather
     * than a search table ID, and additionally filters the dosage form and includes route
     * of administration information.</p>
     *
     * @param pKey the DPD drug code as a String
     * @param formCode the pharmaceutical form code to filter by
     * @param html whether to format results as HTML (currently unused)
     * @return a Vector containing a single Hashtable with keys "name", "atc", "product",
     *         "regional_identifier", "components", "drugForm", "drugRoute"
     */
    public Vector getDrugByDrugCode(String pKey, String formCode, boolean html) {
        String productId = "";
        String origId = pKey;
        Vector returnRows = new Vector();

        EntityManager em = JpaUtils.createEntityManager();
        try {
            //EntityTransaction tx = em.getTransaction();
            //tx.begin();
            Query queryName = em.createQuery("select cai.ingredient from CdActiveIngredients cai where cai.drugCode=(:pKey)");
            queryName.setParameter("pKey", Integer.parseInt(pKey));
            Query queryAtc = em.createQuery("select ctc.tcAtcNumber from CdTherapeuticClass ctc where ctc.drugCode = (:pKey)");
            queryAtc.setParameter("pKey", Integer.parseInt(pKey));
            Query queryProduct = em.createQuery("select cds.name from CdDrugSearch cds where cds.id=(:origId)");
            queryProduct.setParameter("origId", Integer.parseInt(origId));
            Query queryRegionalIdentifier = em.createQuery("select cdp from CdDrugProduct cdp where cdp.drugCode = (:pKey)");
            queryRegionalIdentifier.setParameter("pKey", Integer.parseInt(pKey));
            Query queryComponent = em.createQuery("select cai from CdActiveIngredients cai where cai.drugCode=(:pKey)");
            queryComponent.setParameter("pKey", Integer.parseInt(pKey));
            Query queryForm = em.createQuery("select  cf from CdForm cf where cf.drugCode=(:pKey) and cf.pharmCdFormCode=(:formCode)");
            queryForm.setParameter("pKey", Integer.parseInt(pKey));
            queryForm.setParameter("formCode", Integer.parseInt(formCode));
            Query queryRoute = em.createQuery("select cr from CdRoute cr where cr.drugCode=(:pKey)");
            queryRoute.setParameter("pKey", Integer.parseInt(pKey));

            String name = "";
            String atc = "";
            String product = "";
            String ProductId = "";
            String regionalIdentifier = "";

            List resultName = queryName.getResultList();
            if (resultName.size() > 0) {//always get the last result in the resultlist??
                for (int i = 0; i <
                        resultName.size(); i++) {
                    name = (String) resultName.get(i);
                }

            }

            List resultAtc = queryAtc.getResultList();
            if (resultAtc.size() > 0) {
                for (int i = 0; i <
                        resultAtc.size(); i++) {
                    atc = (String) resultAtc.get(i);
                }

            }

            List resultProduct = queryProduct.getResultList();

            if (resultProduct.size() > 0) {
                for (int i = 0; i <
                        resultProduct.size(); i++) {
                    product = (String) resultProduct.get(i);
                }

            }


            List<CdDrugProduct> resultRegionalIdentifier = queryRegionalIdentifier.getResultList();


            if (resultRegionalIdentifier.size() > 0) {
                for (int i = 0; i <
                        resultRegionalIdentifier.size(); i++) {
                    productId = resultRegionalIdentifier.get(i).getDrugCode().toString();
                    regionalIdentifier =
                            resultRegionalIdentifier.get(i).getDrugIdentificationNumber().toString();
                }

            }
            //logger.debug("reginoal Identifier " + regionalIdentifier);
            String ingredient = "";
            String strength = "";
            String strengthUnit = "";


            Vector component = new Vector();
            List<CdActiveIngredients> resultComponent = queryComponent.getResultList();
            if (resultComponent.size() > 0) {
                for (int i = 0; i < resultComponent.size(); i++) {//always get the last component in the list??
                    ingredient = resultComponent.get(i).getIngredient();
                    strength =
                            resultComponent.get(i).getStrength();
                    strengthUnit =
                            resultComponent.get(i).getStrengthUnit();
                    Hashtable ha = new Hashtable();
                    ha.put("name", ingredient);
                    if(strength.trim().length()>0)
                        ha.put("strength", Float.valueOf(strength.trim()).floatValue());
                    else
                        ha.put("strength", 0f);
                    ha.put("unit", strengthUnit);
                    component.addElement(ha);
                }
            }

            Vector drugRoute = new Vector();
            List<CdRoute> resultRoute = queryRoute.getResultList();
            if (resultRoute.size() > 0) {
                for (int i = 0; i < resultRoute.size(); i++) {
                    drugRoute.addElement(resultRoute.get(i).getRouteOfAdministration());
                }
            }

            String drugForm = "";
            List<CdForm> resultForm = queryForm.getResultList();
            if (resultForm.size() > 0) {
                //assert resultForm.size()==1; // queryForm should return a unique result.
                drugForm = resultForm.get(0).getPharmaceuticalCdForm();
            }
            Hashtable ha2 = new Hashtable();
            ha2.put("name", name);
            ha2.put("atc", atc);
            ha2.put("product", product);
            ha2.put("regional_identifier", regionalIdentifier);
            ha2.put("components", component);
            ha2.put("drugForm", drugForm);
            ha2.put("drugRoute", drugRoute);

            returnRows.addElement(ha2);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        logger.debug("returnRows in getDrugByDrugCode=" + returnRows);
        return returnRows;
    }
    
    
    /**
     * Resolves a patient's allergy list into a set of ATC codes representing the therapeutic
     * classes the patient is allergic to.
     *
     * <p>For each allergy, the resolution strategy depends on the allergy type:</p>
     * <ul>
     *   <li><b>Type 8 (ATC):</b> Returns the ATC drug code directly from the search table.</li>
     *   <li><b>Type 10 (AHFS):</b> Looks up AHFS number, finds all ATC codes in that AHFS group.</li>
     *   <li><b>Type 11/12 (Generic):</b> Resolves via link table to drug codes, finds their ATC codes.</li>
     *   <li><b>Type 13 (Brand):</b> Resolves brand to drug code, finds its ATC code(s).</li>
     *   <li><b>Type 14 (Ingredient):</b> Not yet implemented.</li>
     * </ul>
     *
     * @param allergies a Vector of Hashtables, each with keys "type", "description", "id"
     * @return a Vector of ATC code strings representing the patient's allergy classes
     */
    public Vector getAllergyClasses(Vector allergies) {
    	Vector vec = new Vector();       
        EntityManager em = JpaUtils.createEntityManager();
        
        try {
            Enumeration e = allergies.elements();
            while (e.hasMoreElements()) {
                Hashtable alleHash = new Hashtable((Hashtable) e.nextElement());
                String aType = (String) alleHash.get("type");
                String aDesc = (String) alleHash.get("description");
                String aId = (String) alleHash.get("id");

                if (aType.matches("8")) {
                	//ATC
                	Query q1 = em.createQuery("select cds.drugCode from CdDrugSearch cds where cds.name=(:descr) and cds.category=(:cat)");
                	q1.setParameter("descr", aDesc);
                	q1.setParameter("cat", Integer.valueOf(aType));                	
                	String cdsDrugCode =(String)q1.getSingleResult();
                	vec.add(cdsDrugCode);
                	
                } else if(aType.matches("10")) {
                	//AHFS
                    Query queryAHFSNumber = em.createQuery("select distinct tc.tcAhfsNumber from CdTherapeuticClass tc where tc.tcAhfs=(:aDesc)");
                    queryAHFSNumber.setParameter("aDesc", aDesc);
                    List<String> list = (List) queryAHFSNumber.getResultList();
                    for(String s:list) {
                        Query query = em.createQuery("select distinct tc.tcAtcNumber from CdTherapeuticClass tc where tc.tcAhfsNumber like :aDesc");
                        query.setParameter("aDesc", s + "%");
                        List<String> resultTcAtcNumber = query.getResultList();
                        vec.addAll(resultTcAtcNumber);
                    }
                } else if (aType.matches("11") || aType.matches("12")) {
                	//GENERIC and GENERIC COMPOUND
                	//get drug code we can lookup class with using link table
                	Query q1 = em.createQuery("select cds.id from CdDrugSearch cds where cds.name=(:descr) and cds.category=(:cat)");
                	q1.setParameter("descr", aDesc);
                	q1.setParameter("cat", Integer.valueOf(aType));
                	
                	Integer cdsId =(Integer)q1.getSingleResult();
                	if(cdsId != null) {                	
	                	Query q = em.createQuery("select lgb.drugCode from LinkGenericBrand lgb where lgb.id=(:x)");
	                	q.setParameter("x", cdsId);
	                	List<String> drugCodes = q.getResultList();
	                	
	                	Query q2 = em.createQuery("select distinct tc.tcAtcNumber from CdTherapeuticClass tc where tc.drugCode in (:codes)");
	                	q2.setParameter("codes", drugCodes);
	                	List<String> atcCodes = q2.getResultList();
	                	vec.addAll(atcCodes);
                	}
                } else if(aType.matches("13")) {
                	//BRAND NAME
                	Query q1 = em.createQuery("select cds.drugCode from CdDrugSearch cds where cds.name=(:descr) and cds.category=(:cat)");
                	q1.setParameter("descr", aDesc);
                	q1.setParameter("cat", Integer.valueOf(aType));
                	String cdsDrugCode =(String)q1.getSingleResult();
                	
                	if(cdsDrugCode != null) {
                		Query q2 = em.createQuery("select distinct tc.tcAtcNumber from CdTherapeuticClass tc where tc.drugCode = (:drugCode)");
                		q2.setParameter("drugCode", Integer.valueOf(cdsDrugCode));
                		List<String> atcCodes = q2.getResultList();
                		vec.addAll(atcCodes);
                	}
                } else if(aType.matches("14")) {
                	//INGREDIENT
                } else {                	  
                	logger.debug("No Match YET desc " + aDesc + " type " + aType + " id " + aId);
                }
                
            }
        }catch(Exception e) {
        	e.printStackTrace();
        }finally {
        	em.close();
        }
        
    	return vec;
    }
    
    /**
     * Looks up the ATC therapeutic class description for a given ATC code.
     *
     * @param atc the ATC code to look up (e.g., "N02BE01")
     * @return a Vector containing a single Hashtable with key "tc_atc" mapping to the ATC
     *         description, or "Not found" if the code does not exist
     */
    public Vector getTcATC(String atc) {
        EntityManager em = JpaUtils.createEntityManager();
        Vector result = new Vector();
        List<CdTherapeuticClass> drugs = new ArrayList<CdTherapeuticClass>();
        try {
            String queryStr = " select cds from CdTherapeuticClass cds where cds.tcAtcNumber = (:atc) ";
            Query query = em.createQuery(queryStr);
            query.setParameter("atc", atc);
            
            drugs = query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }
        
        if(drugs.size() > 0) {
            CdTherapeuticClass drug = drugs.get(0);
            Hashtable ha = new Hashtable();
            ha.put("tc_atc", drug.getTcAtc());
            result.add(ha);
        } else {
            Hashtable ha = new Hashtable();
            ha.put("tc_atc", "Not found");
            result.add(ha);
        }
        
        return result;
    }

    
}
