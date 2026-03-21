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
package org.drugref.ca.dpd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Repository;
import org.drugref.plugin.*;
import org.drugref.util.DrugrefProperties;
import org.drugref.util.JpaUtils;
import org.drugref.util.MiscUtils;

/**
 *  org.drugref.DrugrefTest
 * @author jackson
 */
@Repository
public class TablesDao {

    Logger logger = MiscUtils.getLogger();
    private String plugindir;
    private String name;
    private String version;
    private Vector plugins = new Vector();
    private Hashtable services = new Hashtable();
    private Hashtable provided = new Hashtable();
    private String db;
    private String user;
    private String pwd;
    private List<Integer> inactiveDrugs=new ArrayList();
    private final int MAX_NO_ROWS=60;

    public TablesDao() {
        //p("=========start tablesdao constructor======");
        this.plugindir = "plugins";
        this.name = "Drugref Service";
        this.version = "1.0";

        this.db = "drugref2";
        this.user = null;
        this.pwd = null;
        //creating an object of holbrook as plugin instead of importing from files.

        //Holbrook holbrook = new Holbrook();
        //this.plugins.addElement(holbrook);

        //PluginImpl module = new PluginImpl
        DrugrefPlugin dp = new DrugrefPlugin();
        this.plugins.addElement(dp);
        //p("DrugrefPlugin.register()", dp.register().toString());
        String name;
        String version;
        Hashtable haProvides = new Hashtable();
        Holbrook thePlugin = new Holbrook();
        name = (String) dp.register().get(0);
        version = (String) dp.register().get(1);
        haProvides = (Hashtable) dp.register().get(2);
        thePlugin = (Holbrook) dp.register().get(3);

        //p("name", name);
        //p("version", version);
        //p("provides", haProvides.toString());
        //p("theplugin", thePlugin.toString());

        Hashtable haService = new Hashtable();
        haService.put("version", version);
        haService.put("plugin", thePlugin);
        haService.put("provides", haProvides);

        this.services.put(name, haService);
        //p("this.services", this.services.toString());
        //loop through all provides
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

    public String identify() {
        return this.name;
    }

    public String version() {
        return this.version;
    }

    public Vector list_available_services() {
        Vector v = new Vector();
        //TODO: implement
        return v;
    }

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
    	EntityManager em = JpaUtils.createEntityManager();
    	Query drugProductByDIN = em.createNamedQuery("CdDrugProduct.findByDrugIdentificationNumber");
    	drugProductByDIN.setParameter("drugIdentificationNumber", DIN);
    	CdDrugProduct drugProduct = (CdDrugProduct) drugProductByDIN.getSingleResult();
    	return drugProduct.getDrugCode();
    }
    /**
     * Dennis Warren Colcamex Resources
     * @param drugId
     * @return
     */
    public String getDINFromDrugId(Integer drugId) {
    	EntityManager em = JpaUtils.createEntityManager();
    	Query drugProductByDrugId = em.createNamedQuery("CdDrugProduct.findByDrugCode");
    	drugProductByDrugId.setParameter("drugCode", drugId);
    	CdDrugProduct drugProduct = (CdDrugProduct) drugProductByDrugId.getSingleResult();
    	return drugProduct.getDrugIdentificationNumber();
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
    	Query drugProductByDrugId = em.createNamedQuery("CdDrugProduct.findByDrugCode");
    	drugProductByDrugId.setParameter("drugCode", drugId);
    	CdDrugProduct drugProduct = (CdDrugProduct) drugProductByDrugId.getSingleResult();
    	return drugProduct.getId();
    }


    //write inactiveDrugs hashtable
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

    //using drugcode find din and atc codes
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
    
    public List listDrugsbyAIGroup(String aiGroup) {
        EntityManager em = JpaUtils.createEntityManager();
        List<Object[]> results = null;
        try {
            logger.debug("before tx definition");
            //EntityTransaction tx = em.getTransaction();
            logger.debug("after txt definition");
            //tx.begin();
            String queryStr = "select distinct cai.ingredient,cai.strength, cai.strengthUnit,cdf.pharmaceuticalCdForm   from CdDrugProduct cdp, CdForm cdf, CdActiveIngredients cai where cdp.drugCode = cai.drugCode and cdp.drugCode = cdf.drugCode and  cdp.aiGroupNo LIKE '" + aiGroup + "%' order by cai.strength";//(:aiGroup) ";
            Query query = em.createQuery(queryStr);
            //query.setParameter("aiGroup", aiGroup+"%");

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

    public List<CdDrugSearch> listDrugsbyAIGroup2(String aiGroup) {
        EntityManager em = JpaUtils.createEntityManager();
        List<CdDrugSearch> results = null;
        try {
            logger.debug("before tx definition for" + aiGroup);
            //EntityTransaction tx = em.getTransaction();
            logger.debug("after txt definition");
            //tx.begin();
            //String queryStr = "select distinct cai.ingredient,cai.strength, cai.strengthUnit,cdf.pharmaceuticalCdForm   from CdDrugProduct cdp, CdForm cdf, CdActiveIngredients cai where cdp.drugCode = cai.drugCode and cdp.drugCode = cdf.drugCode and  cdp.aiGroupNo LIKE '"+ aiGroup + "%' order by cai.strength";//(:aiGroup) ";
            String queryStr = "select cds from CdDrugSearch cds where cds.category in (18,19) and cds.drugCode like '" + aiGroup + "%' ";

            Query query = em.createQuery(queryStr);
            //query.setParameter("aiGroup", aiGroup+"%");

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
    
    public String getFirstDinInAIGroup(String aiGroupNo) {
    	String q1="select cdp from CdDrugProduct cdp where cdp.aiGroupNo = (:groupNo) order by cdp.lastUpdateDate";
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
         }
         return null;
    }

    public Vector listSearchElement4(String str, boolean rightOnly){//use 2 separate queries to speed up search which returns huge number of results.
        //logger.debug("before create em in listSearchElement4");
        String matchKey=str.toUpperCase();
        matchKey=matchKey.replace(",", " ");
        matchKey=matchKey.replace("'", "");
        //Vector directMatch = new Vector();
       // Vector indirectMatch=new Vector();
        List<CdDrugSearch> results1=new ArrayList();
        List<CdDrugSearch> results2=new ArrayList();
        int max_rows_for_result2=0;
        boolean onlyDirectMatch=false;//if there are more than 60 direct match result return only direct match results
        if(inactiveDrugs.size()==0)
            inactiveDrugs=getInactiveDrugs();
        //logger.debug("inactiveDrugs size ="+inactiveDrugs.size());
        EntityManager em = JpaUtils.createEntityManager();
        //logger.debug("created entity manager");
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
        if(results1.size()>=MAX_NO_ROWS){
                onlyDirectMatch=true;
        }else{
            max_rows_for_result2=MAX_NO_ROWS-results1.size();
        }

        if(results1.size()>0){
            //remove duplicate drug names
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
                    //second part
                    str = str.replace(",", " ");
                    str = str.replace("'", "");
                    String[] strArray = str.split("\\s+");

                //    for (int i = 0; i < strArray.length; i++) {
                //        logger.debug(strArray[i]);
                //    }

            //String queryStr = "select cds.id, cds.category, cds.name from CdDrugSearch cds where ";
                    String queryStr = "select cds from CdDrugSearch cds where (";
                    for (int i = 0; i < strArray.length; i++) {
                        queryStr = queryStr + "upper(cds.name) like " + "'" + ((rightOnly)?"":"%") + strArray[i].toUpperCase() + "%" + "'";
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
    public Vector listSearchElement3(String str) {//not used, replaced by faster listSearchElement4
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

    public Vector listBrandsFromElement(String drugID) {
        EntityManager em = JpaUtils.createEntityManager();

        String drugCode = "";
        Integer category;
        List<CdDrugSearch> results = new ArrayList();

        try {
            //EntityTransaction tx = em.getTransaction();
            //tx.begin();


            CdDrugSearch cdsResult = (CdDrugSearch) em.createQuery("select cds from CdDrugSearch cds where cds.id = " + drugID).getSingleResult();


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

    public Vector listSearchElementSelectCategories(String str, Vector cat) {
    	return listSearchElementSelectCategories(str,cat,true,true);
    }
    
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
            queryStr = queryStr + "cds.category= " + cat.get(i);
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
    //change to return Hashtable??

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

    public Vector listDrugClass(Vector Dclass) {
        EntityManager em = JpaUtils.createEntityManager();
        List results = new ArrayList();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();

        String q1 = "select cds from  CdDrugSearch cds where ";
        for (int i = 0; i < Dclass.size(); i++) {
            q1 = q1 + " cds.id = " + Dclass.get(i);
            if (i < Dclass.size() - 1) {
                q1 = q1 + " or ";
            }
        }
        logger.debug(q1);
        Vector vec = new Vector();
        Query queryOne = em.createQuery(q1);
        List<CdDrugSearch> listOne = queryOne.getResultList();
        if (listOne.size() > 0) {
            try {
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

    public Vector getAllergyWarnings(String atcCode, Vector allergies) {

        logger.debug("in getAllergyWarnings: atcCode="+atcCode+",allergies="+allergies);
        Vector results = new Vector();
        Vector vec = new Vector();
        Hashtable ha = new Hashtable();
        Vector warning = new Vector();
        Vector missing = new Vector();

        if (atcCode.matches("") || atcCode.matches("null")) {
            ha.put("warnings", results);
            vec.add(ha);
            return vec;
        }
        EntityManager em = JpaUtils.createEntityManager();
        //EntityTransaction tx = em.getTransaction();
        //tx.begin();
        try {
            Enumeration e = allergies.elements();
            while (e.hasMoreElements()) {
                Hashtable alleHash = new Hashtable((Hashtable) e.nextElement());
                String aType = (String) alleHash.get("type");
                String aDesc = (String) alleHash.get("description");
                String aId = (String) alleHash.get("id");
                if (aType.matches("8")) {

                    Query query = em.createQuery("select tc.tcAtcNumber from CdTherapeuticClass tc where tc.tcAtcNumber= (:atcCode) and tc.tcAtc=(:aDesc)");
                    query.setParameter("atcCode", atcCode);
                    query.setParameter("aDesc", aDesc);
                    List resultTcAtcNumber = query.getResultList();
                    if (resultTcAtcNumber.size() > 0) {
                        logger.debug(atcCode + " is in this1 Allergy group " + aDesc);
                        results.add(aId);
                    } else {
                        logger.debug(atcCode + " is NOT in this group " + aDesc);
                    }
                } else if (aType.matches("10")) {

                    Query queryAHFSNumber = em.createQuery("select distinct tc.tcAhfsNumber from CdTherapeuticClass tc where tc.tcAhfs=(:aDesc)");
                    queryAHFSNumber.setParameter("aDesc", aDesc);
                    List<String> list = (List) queryAHFSNumber.getResultList();
                    logger.debug("LIST SIZE " + list.size());
                    for(String s: list){
                        logger.debug("GET ALLERGY WARNIGN" + s + " atc code " + atcCode);
                        /*
                        select tc.tc_atc_number from cd_therapeutic_class tc where tc.tc_atc_number= 'J01CA08' and tc.tc_ahfs_number like ('08:12.16%');+---------------+

                         */

                        Query query = em.createQuery("select tc.tcAtcNumber from CdTherapeuticClass tc where tc.tcAtcNumber= (:atcCode) and tc.tcAhfsNumber like '" + s + "%'");
                        query.setParameter("atcCode", atcCode);
                        //query.setParameter("aDesc", s+"%");
                        List resultTcAtcNumber = query.getResultList();
                        if (resultTcAtcNumber.size() > 0) {
                            logger.debug(atcCode + " is in this2 Allergy group " + aDesc);
                            results.add(aId);
                        } else {
                            logger.debug(atcCode + " is NOT in this group " + aDesc);
                        }
                    }
                } else if (aType.matches("14")) {
                    logger.debug("aType=14 is not implemented yet");
                } else if (aType.matches("11") || aType.matches("12")) {
                    logger.debug("aType=11 or 12");
                   /* Query query = em.createQuery("select tc.tcAtcNumber from CdDrugSearch cds, LinkGenericBrand lgb, CdTherapeuticClass tc where tc.tcAtcNumber =(:atcCode) and cds.name=(:aDesc) " +
                            "and cds.drugCode=lgb.id and lgb.drugCode=tc.drugCode");
                    query.setParameter("atcCode", atcCode);
                    query.setParameter("aDesc", aDesc);
                    List resultTcAtcNumber = query.getResultList();
                    if (resultTcAtcNumber.size() > 0) {
                        logger.debug("warning allergic to " + aDesc);
                        results.add(aId);
                    } else {
                        logger.debug("NO warning for " + aDesc);
                    }*/
                    //ORIGINAL QUERY:select tc.tcAtcNumber from CdDrugSearch cds, LinkGenericBrand lgb, CdTherapeuticClass tc where tc.tcAtcNumber =(:atcCode) and cds.name=(:aDesc) and cds.drugCode=lgb.id and lgb.drugCode=tc.drugCode
                    //for category 11 and 12 drugs,cds.id=cds.drugCode
                    Query q1=em.createQuery("select distinct tc.tcAtcNumber from CdDrugSearch cds, CdTherapeuticClass tc,LinkGenericBrand lgb  where tc.tcAtcNumber =(:atcCode) and cds.name=(:aDesc) and cds.id=lgb.id and lgb.drugCode in (:tcDrugCodeString)");
                    q1.setParameter("atcCode", atcCode);
                    q1.setParameter("aDesc", aDesc);              
                    

                        Query q3=em.createQuery("select distinct tc.drugCode from  CdTherapeuticClass tc where tc.tcAtcNumber=(:atcCode)");
                        q3.setParameter("atcCode", atcCode);
                        List<Integer> r3=q3.getResultList();
                        List<String> r3String=new ArrayList();
                        for(Integer ii:r3)
                            r3String.add(ii.toString());
                        q1.setParameter("tcDrugCodeString", r3String);
                        List<String> r1=q1.getResultList();
                        logger.debug("r1 size: "+r1.size());
                        for(String r1str:r1)
                            logger.debug("r1="+r1str);
                        if(r1.size()>0) {
                            results.add(aId);
                            logger.debug("warning allergic to " + aDesc);
                        }
                } else if (aType.matches("13")) {
                   /* Query query = em.createQuery("select tc.tcAtcNumber from CdDrugSearch cds,CdTherapeuticClass tc where tc.tcAtcNumber =(:atcCode) and cds.name=(:aDesc) and cds.drugCode=tc.drugCode ");
                    query.setParameter("atcCode", atcCode);
                    query.setParameter("aDesc", aDesc);
                    List resultTcAtcNumber = query.getResultList();
                    if (resultTcAtcNumber.size() > 0) {
                        logger.debug("warning allergic to " + aDesc);
                        results.add(aId);
                    } else {
                        logger.debug("NO warning for " + aDesc);
                    }*/

                    //ORIGINAL QUERY:select tc.tcAtcNumber from CdDrugSearch cds,CdTherapeuticClass tc where tc.tcAtcNumber =(:atcCode) and cds.name=(:aDesc) and cds.drugCode=tc.drugCode
                    Query q1=em.createQuery("select distinct tc.tcAtcNumber from CdDrugSearch cds,CdTherapeuticClass tc where tc.tcAtcNumber =(:atcCode) and cds.name=(:aDesc) and tc.drugCode in (:cdsDrugCodeInteger)");
                   
                    //Query q2=em.createQuery("select tc.drugCode from CdTherapeuticClass tc");
                    Query q2=em.createQuery("select cds.drugCode from CdDrugSearch cds where cds.category=13 and cds.name=(:aDesc)");
                    q2.setParameter("aDesc", aDesc);
                    List<String> r2=q2.getResultList();
                    List<Integer> r2Integer=new ArrayList();
                    for(String ss:r2)
                        r2Integer.add(Integer.parseInt(ss));
                    
                    if(r2Integer.isEmpty()) {
                    	missing.add(aId);
                    } else {
	                    q1.setParameter("atcCode", atcCode);
	                    q1.setParameter("aDesc", aDesc);
	                    q1.setParameter("cdsDrugCodeInteger", r2Integer);
	                    
	                    logger.info("atcCode="+atcCode + ",aDesc=" + aDesc + ",cdsDrugCodeInteger=" + r2Integer);
	                    List<String> r1=q1.getResultList();
	                    if(r1.size()>0)
	                        results.add(aId);
                    }

                } else {
                    logger.debug("No Match YET desc " + aDesc + " type " + aType + " atccode " + atcCode);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            JpaUtils.close(em);
        }

        ha.put("warnings", results);
        ha.put("missing", missing);
        vec.add(ha);
        // logger.debug("print out return values: ");
        // Vector retlist=(Vector)((Hashtable)vec.get(0)).get("warnings");
        //  for(int i=0;i<retlist.size();i++)
        //      logger.debug("id="+retlist.get(i));
        return vec;
    }

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
                    	Query query = em.createQuery("select distinct tc.tcAtcNumber from CdTherapeuticClass tc where tc.tcAhfsNumber like '" + s + "%'");                       
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
