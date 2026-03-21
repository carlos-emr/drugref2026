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
package org.drugref;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;

import org.drugref.ca.dpd.CdTherapeuticClass;

import java.util.List;

import org.drugref.ca.dpd.TablesDao;

import java.util.Vector;

import javax.persistence.Query;
import javax.persistence.EntityManager;

import org.drugref.ca.dpd.CdDrugSearch;
import org.drugref.ca.dpd.History;
import org.drugref.util.JpaUtils;
import org.drugref.util.RxUpdateDBWorker;
import org.drugref.util.SpringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.drugref.util.MiscUtils;
import org.drugref.dinInteractionCheck.InteractionRecord;
import org.drugref.dinInteractionCheck.InteractionsChecker;
import org.drugref.dinInteractionCheck.InteractionsCheckerFactory;

/**
 *
 * @author jaygallagher & jacksonbi
 */

public class Drugref {
	
        TablesDao queryDao = (TablesDao) SpringUtils.getBean("tablesDao");
        public static HashMap<String,Object> DB_INFO=new HashMap<String,Object>();       
        public static Boolean UPDATE_DB=false;        
        private static Logger logger = MiscUtils.getLogger();
        
        /**
         * Dennis Warren Colcamex Resources
         * @param DIN
         * @param bvalue
         * @return
         */
        public synchronized Vector get_drug_by_DIN(String DIN, boolean bvalue) {
        	Vector drug = null;
        	int pKey = get_drug_pkey_from_DIN(DIN); 
        	
        	if( pKey > 0 ) {
        		drug = get_drug(pKey, bvalue);
        	}
        	return drug;
        }
        
        /**
         * Dennis Warren Colcamex Resources
         * @param DIN
         * @return
         */
        public synchronized int get_drug_pkey_from_DIN( String DIN ) {
        	return queryDao.getDrugpKeyFromDIN(DIN);
        }
        
        /**
         * Dennis Warren Colcamex Resources
         * @param drugId
         * @return
         */
        public synchronized int get_drug_pkey_from_drug_id( String drugId ) {
        	int pKey = 0;
        	if(StringUtils.isNumeric(drugId)) {
        		pKey = Integer.parseInt(drugId);
        	}
        	if(pKey > 0) {
        		return queryDao.getDrugpKeyFromDrugId(pKey);
        	}
        	return pKey;
        }
        
        /**
         * Dennis Warren Colcamex Resources
         * @param DIN
         * @return
         */
        public synchronized int get_drug_id_from_DIN( String DIN ) {        	
        	Integer drugId = null; 
        	if( ! DIN.isEmpty() ) {
        		drugId = queryDao.getDrugIdFromDIN(DIN);
        	}        	
        	if( drugId == null ) {
        		drugId = 0;
        	}
        	return drugId;
        }
        
        public String getLastUpdateTime(){
            if(UPDATE_DB){
                return "updating";
            }else{

                EntityManager em = JpaUtils.createEntityManager();
                String queryStr="select h from History h where h.id=(select max(h2.id) from History h2)";
                Query query = em.createQuery(queryStr);
                List<History> results = query.getResultList();
                JpaUtils.close(em);
                if(results!=null && results.size()>0){
                    return results.get(0).getDateTime().toString();
                }
                else
                    return null;
            }
        }
        
        //start the updating process if there isn't one already.
        public String updateDB(){
            if(!UPDATE_DB){
                RxUpdateDBWorker worker = new RxUpdateDBWorker();
                worker.start();                
                return "running";
            }else{                
                return "updating";
            }
        }

        public Vector list_search_element(String searchStr){
                Vector vec=queryDao.listSearchElement(searchStr);
                return vec;
        }

    /*
         * This is a testing method for now!
         *
         * This should take the search string, find all the possible matches.
         * For each match get the ai code. ( first 7 characters only ) Then research all the available types using this key.
         */
        public Vector list_search_element2(String searchStr){
                Vector vec=queryDao.listSearchElement2(searchStr);
                return vec;
        }

        public Vector list_search_element3(String searchStr){
                Vector vec=queryDao.listSearchElement4(searchStr,false);
                return vec;
        }
        
        public Vector list_search_element3_right(String searchStr){
            Vector vec=queryDao.listSearchElement4(searchStr,true);
            return vec;
    }


        /*
         * Returns ATC, DIN, Route, Form
         */
        public Vector get_drug_2(String pkey,boolean html){
                logger.debug("IN get_drug_2 "+pkey);
                Hashtable<String, Object> returnHash = new Hashtable<>();
                Integer id = Integer.parseInt(pkey);
                
                CdDrugSearch cds = queryDao.getSearchedDrug(id);
                
                if (cds != null){
                    cds.getDrugCode();
                    cds.getCategory();
                    returnHash.put("drugCode",cds.getDrugCode());
                    returnHash.put("cat",cds.getCategory());
                    logger.debug("drugCode "+cds.getDrugCode()+ " category "+cds.getCategory());

                    if (cds.getCategory() == 13){
                       return queryDao.getDrug(pkey, true);
                    }else if (cds.getCategory() == 18 || cds.getCategory() == 19){
                       int pl = cds.getDrugCode().indexOf("+");
                       String aiCode = cds.getDrugCode().substring(0, pl);
                       String formCode = cds.getDrugCode().substring(pl+1);
                       return queryDao.getMadeGenericExample(aiCode,formCode,false);
                    }
                }
                return null;
        }



        public Vector list_search_element_route(String str, String route) {
                Vector vec=queryDao.listSearchElementRoute(str,route);
                return vec;
        }

        public Vector list_brands_from_element(String drugID) {
                logger.debug("in drugref.java list_brands_from_element");
                logger.debug("drugID="+drugID);
                Vector vec=queryDao.listBrandsFromElement(drugID);
                logger.debug("after listBrandsFromElement.");
                for(int i=0;i<vec.size();i++){
                        logger.debug("vector="+vec.get(i));
                }
                return vec;
        }

        public Vector list_search_element_select_categories(String str, Vector cat) {
                Vector vec=queryDao.listSearchElementSelectCategories(str,cat);
                return vec;
        }
        
        public Vector list_search_element_select_categories_right(String str, Vector cat) {
            Vector vec=queryDao.listSearchElementSelectCategories(str,cat,false,true);
            return vec;
        }

        public Vector get_inactive_date(String str ){
             Vector vec=queryDao.getInactiveDate(str);
             return vec;
        }

       public Vector get_generic_name(String drugID) {
                logger.debug("in get_generic_name,drugref.java");
                Vector vec=new Vector();
                try{
                    vec=queryDao.getGenericName(drugID);
                }
                catch(Exception e){e.printStackTrace();}
                for (int i=0; i<vec.size();i++){
                        logger.debug("the returned vec: vec.get(i)="+vec.get(i));
                }
                return vec;
        }
       public Vector get_form(String pKey) {
                Vector vec=queryDao.getForm(pKey);
                return vec;
        }
        public Vector list_drug_class(Vector Dclass) {
                Vector vec=queryDao.listDrugClass(Dclass);
                return vec;
        }
     public Vector get_allergy_warnings(String atcCode, Vector allergies) {

                Vector vec=queryDao.getAllergyWarnings(atcCode,allergies);
                return vec;
    }
     
     public Vector get_allergy_classes(Vector allergies) {
         Vector vec=queryDao.getAllergyClasses(allergies);
         return vec; 
     }
     
     public Vector get_drug(int pKey, boolean html) {
    	 return get_drug(pKey+"", html);
     }
     
     public Vector get_drug(String pKey, boolean html) {
                Vector vec=queryDao.getDrug(pKey,html);
                return vec;
        }

     public Object fetch(String attribute, Vector key) {
        Vector services = new Vector();
        boolean b = true;
        Object obj = queryDao.fetch(attribute, key, services, b);
        return obj;
    }

    public String identify() {

        return queryDao.identify();
    }

    public String version() {

        return queryDao.version();
    }

    public Vector list_available_services() {

        return queryDao.list_available_services();
    }

    public Hashtable list_capabilities() {

        return queryDao.list_capabilities();
    }
    
   /**
     * Get a set of drugs by their DIN number.
     * 
     * @param din
     *            the DIN number to search on.
     * @return A Vector containing the result of the query.
     */
    @SuppressWarnings("unchecked")
    public Vector<String> get_atcs_by_din(String din) {

       Vector<String> answer = new Vector<String>();
       EntityManager em = JpaUtils.createEntityManager();
       List<CdTherapeuticClass> searchAtcResults;
        
       String queryStr = "select cds from CdTherapeuticClass cds, CdDrugProduct cdp where cdp.drugIdentificationNumber = "+ din + " and cds.drugCode = cdp.drugCode";
        
       try {
            // Attempt to search with the named query
          Query query = em.createQuery(queryStr);
        	searchAtcResults = query.getResultList();
          for (CdTherapeuticClass resultAtc: searchAtcResults)answer.add(resultAtc.getTcAtcNumber());

	     } catch (IllegalStateException e) {
	        logger.error("Failed to retrieve drug by DIN: object persistence entity manager was closed.");
	     } catch (IllegalArgumentException e) {
	        logger.error("Failed to retrieve drug by DIN: named query or parameter has not been defined.");
	     } finally {
          JpaUtils.close(em);
       }

       // Return the results 
       return answer;
    }


    public Vector get_atc_name(String atc){
        Vector result = queryDao.getTcATC(atc);
        return result;
    }
    
    
    public Vector interaction_by_regional_identifier(Vector listOfDins, int minSignificates){

    	InteractionsChecker interactionsChecker = InteractionsCheckerFactory.getInteractionChecker();
    	Vector retVec = new Vector();
    	logger.debug("Interaction checker active:"+interactionsChecker.interactionsCheckerActive());
    	if(!interactionsChecker.interactionsCheckerActive()){
    		logger.debug("returning error message");
    		Vector v = new Vector();
    		Hashtable returnHash = new Hashtable();
    		returnHash.put("id","0");
			returnHash.put("updated_at",new Date());
			returnHash.put("name","Interactions Service Not Available");
			StringBuilder sb = new StringBuilder();
			
			for(String s:interactionsChecker.getErrors()){
				sb.append(s);
			}
			if(sb.length()==0){
				returnHash.put("body", "Interaction Checker Not Available");
			}else{
				returnHash.put("body", sb.toString());
			}
			//returnHash.put("atc = drugATC;
			returnHash.put("evidence"," ");
			returnHash.put("reference", "");
			returnHash.put("significance","3");//  //severity
			returnHash.put("trusted",true);
			returnHash.put("author","Medi-Span");
			
    			//Other keys not set: comments, type, createdAt, updatedAt, createdBy, updatedBy, interactStr	
    		v.add(returnHash);
    		
        	return v;

    	}
    	
    	Date now = new Date();
    	if(interactionsChecker.getExpiryDate().before(now)){
    		int daysBetween =  (int) ((now.getTime() -interactionsChecker.getExpiryDate().getTime()) / (1000 * 60 * 60 * 24));
    		logger.info(" how many days expired "+daysBetween);
    		Hashtable<String, Object> returnHash = new Hashtable<>();
    		returnHash.put("id","0");
			returnHash.put("updated_at",now); //NEEDS TO Be replaced by date of the file
			returnHash.put("name","Interactions Service Expired on");
			returnHash.put("evidence"," ");
			returnHash.put("reference", "");
			returnHash.put("significance","3");//  //severity
			returnHash.put("trusted",true);
			returnHash.put("author","Medi-Span");
			
			
    		if(daysBetween > 50 && daysBetween < 60){
    			returnHash.put("body", "Interaction Checker has Expired a new version must be installed to continue use");
    			retVec.add(returnHash);
    		}else if(daysBetween > 60){
    			returnHash.put("body", "Interaction Checker has Expired, a new version must be installed");
    			Vector v = new Vector();
    			v.add(returnHash);
            	return v;
    		}
    		
    	}
    	
    	List<InteractionRecord> interactionsFull = new ArrayList<InteractionRecord>();
    	for (String din1 :(List<String>)listOfDins){
    		
    		for (String din2 :(List<String>)listOfDins){
    			logger.debug("din "+din1+" din "+din2);
    			List<InteractionRecord> interactions = interactionsChecker.check(din1, din2);
    			if(interactions != null){
    				logger.error("din "+din1+" din "+din2+" size "+interactions.size());
    				interactionsFull.addAll(interactions);
    			}
    			
    		}
    	}
    	for (String din1 :(List<String>)listOfDins){
    		List<InteractionRecord> foodInteractions = interactionsChecker.checkForFoodAndEthanol(din1);
    		
    		logger.debug("food "+foodInteractions);
    		if(foodInteractions != null){
    			interactionsFull.addAll(foodInteractions);
    		}
    	}
    	
		logger.debug("interactionsFull "+interactionsFull.size());
    	
    	
    	for (InteractionRecord i : interactionsFull){
    		logger.debug("i record"+retVec.size());
    		Hashtable<String, Object> returnHash = new Hashtable<>();
    		returnHash.put("id",i.getIntid());
			returnHash.put("updated_at",interactionsChecker.getPublishDate()); //NEEDS TO Be replaced by date of the file
			returnHash.put("name",nullCheck(i.getWAR()));
			String body = "<b>Effect:</b>"+i.getEFF()+"<br><b>Mechanism:</b> "+i.getMEC()+"<br><b>Management:</b> "+i.getMAN()+"<br><b>Discussion:</b>"+i.getDIS()+"<br><b>Reference:</b>"+i.getREF()+"<br><br>"+interactionsChecker.getCopyright()+"<br><b>Issue:</b>"+interactionsChecker.getIssue()+"<br><b>Disclaimer:</b>"+interactionsChecker.getDisclaimer();
			returnHash.put("body", body);
			//returnHash.put("atc = drugATC;
			returnHash.put("evidence"," ");
			returnHash.put("reference", "");
			returnHash.put("significance",i.getSeverity());//  //severity
			returnHash.put("trusted",true);
			returnHash.put("author","Medi-Span");
			
			//Other keys not set: comments, type, createdAt, updatedAt, createdBy, updatedBy, interactStr	
			retVec.add(returnHash);
    		logger.debug("ie record"+retVec.size());
    	}
    	logger.debug("v.size "+retVec.size());
		return retVec;
	}
    
    private String nullCheck(String s){
    	if(s ==null){
    		return "n/a";
    	}
    	return s;
    }
    


}
