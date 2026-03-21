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
package io.github.carlos_emr.drugref2026;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;

import io.github.carlos_emr.drugref2026.ca.dpd.CdTherapeuticClass;

import java.util.List;

import io.github.carlos_emr.drugref2026.ca.dpd.TablesDao;

import java.util.Vector;

import jakarta.persistence.Query;
import jakarta.persistence.EntityManager;

import io.github.carlos_emr.drugref2026.ca.dpd.CdDrugSearch;
import io.github.carlos_emr.drugref2026.ca.dpd.History;
import io.github.carlos_emr.drugref2026.util.JpaUtils;
import io.github.carlos_emr.drugref2026.util.RxUpdateDBWorker;
import io.github.carlos_emr.drugref2026.util.SpringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.drugref2026.util.MiscUtils;
import io.github.carlos_emr.drugref2026.dinInteractionCheck.InteractionRecord;
import io.github.carlos_emr.drugref2026.dinInteractionCheck.InteractionsChecker;
import io.github.carlos_emr.drugref2026.dinInteractionCheck.InteractionsCheckerFactory;

/**
 * Main XML-RPC handler class for the drugref2026 application.
 *
 * <p>All public methods in this class are remotely callable via XML-RPC through
 * {@link DrugrefService}. This class serves as the primary API for:
 * <ul>
 *   <li>Drug search and lookup against the Canadian Drug Product Database (DPD)</li>
 *   <li>Drug interaction checking via Medi-Span integration</li>
 *   <li>Allergy warning retrieval based on ATC codes</li>
 *   <li>ATC (Anatomical Therapeutic Chemical) code lookups</li>
 *   <li>DIN (Drug Identification Number) to drug resolution</li>
 *   <li>Database update orchestration (DPD import from Health Canada)</li>
 * </ul>
 *
 * <p>Data access is delegated to {@link TablesDao} which queries the underlying
 * DPD tables via JPA. Drug interaction checking is delegated to the
 * {@link InteractionsChecker} obtained from {@link InteractionsCheckerFactory}.
 *
 * @author jaygallagher
 * @author jacksonbi
 */

public class Drugref {

        /** Data access object for querying DPD (Drug Product Database) tables. */
        TablesDao queryDao = (TablesDao) SpringUtils.getBean("tablesDao");

        /** Stores metadata about the most recent database update (row counts, timing, etc.). */
        public static HashMap<String,Object> DB_INFO=new HashMap<String,Object>();

        /** Flag indicating whether a database update is currently in progress. Used to prevent concurrent updates. */
        public static Boolean UPDATE_DB=false;

        private static Logger logger = MiscUtils.getLogger();
        
        /**
         * Retrieves full drug information by its DIN (Drug Identification Number).
         *
         * <p>First resolves the DIN to an internal primary key, then fetches the
         * complete drug record. Returns {@code null} if no drug is found for the given DIN.
         *
         * @param DIN the Drug Identification Number (8-digit Health Canada identifier)
         * @param bvalue if {@code true}, include HTML-formatted content in the result
         * @return a Vector of Hashtables containing the drug record, or {@code null} if not found
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
         * Resolves a DIN (Drug Identification Number) to the internal database primary key.
         *
         * @param DIN the Drug Identification Number to look up
         * @return the internal primary key for the drug, or 0 if not found
         */
        public synchronized int get_drug_pkey_from_DIN( String DIN ) {
        	return queryDao.getDrugpKeyFromDIN(DIN);
        }
        
        /**
         * Resolves a numeric drug ID (as a String) to the internal database primary key.
         *
         * <p>The drugId must be a valid numeric string. Non-numeric or zero values
         * return 0 without querying the database.
         *
         * @param drugId the numeric drug identifier as a String
         * @return the internal primary key for the drug, or 0 if not found or invalid input
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
         * Resolves a DIN (Drug Identification Number) to the drug ID used in the DPD.
         *
         * @param DIN the Drug Identification Number to look up
         * @return the drug ID associated with the DIN, or 0 if the DIN is empty or not found
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
        
        /**
         * Returns the timestamp of the most recent successful database update.
         *
         * <p>If an update is currently in progress, returns the string "updating"
         * instead of a timestamp. Queries the History table for the latest entry.
         *
         * @return the date/time string of the last update, "updating" if an update is in progress,
         *         or {@code null} if no update history exists
         */
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
        
        /**
         * Triggers a full database update from Health Canada's DPD data.
         *
         * <p>Launches the update in a background thread ({@link RxUpdateDBWorker}).
         * Only one update can run at a time; if an update is already in progress,
         * returns "updating" without starting a new one.
         *
         * @return "running" if a new update was started, or "updating" if one is already in progress
         */
        public String updateDB(){
            if(!UPDATE_DB){
                RxUpdateDBWorker worker = new RxUpdateDBWorker();
                worker.start();                
                return "running";
            }else{                
                return "updating";
            }
        }

        /**
         * Searches the drug database for drugs matching the given search string.
         *
         * @param searchStr the search term (drug name or partial name)
         * @return a Vector of Hashtables containing matching drug search results
         */
        public Vector list_search_element(String searchStr){
                Vector vec=queryDao.listSearchElement(searchStr);
                return vec;
        }

        /**
         * Extended drug search that finds all matches and then resolves available
         * formulation types using the AI (Active Ingredient) code (first 7 characters).
         *
         * <p>This is an experimental/testing variant of {@link #list_search_element(String)}.
         *
         * @param searchStr the search term (drug name or partial name)
         * @return a Vector of Hashtables containing matching drug search results with type information
         */
        public Vector list_search_element2(String searchStr){
                Vector vec=queryDao.listSearchElement2(searchStr);
                return vec;
        }

        /**
         * Searches for drugs using the enhanced search algorithm (version 4) with left-anchored matching.
         *
         * @param searchStr the search term (drug name or partial name)
         * @return a Vector of Hashtables containing matching drug search results
         */
        public Vector list_search_element3(String searchStr){
                Vector vec=queryDao.listSearchElement4(searchStr,false);
                return vec;
        }
        
        /**
         * Searches for drugs using the enhanced search algorithm (version 4) with right-anchored matching.
         *
         * <p>Unlike {@link #list_search_element3(String)}, this variant matches from the right side
         * of the search term, useful for suffix-based drug name lookups.
         *
         * @param searchStr the search term (drug name or partial name)
         * @return a Vector of Hashtables containing matching drug search results
         */
        public Vector list_search_element3_right(String searchStr){
            Vector vec=queryDao.listSearchElement4(searchStr,true);
            return vec;
    }


            /**
         * Retrieves detailed drug information (ATC, DIN, Route, Form) by primary key.
         *
         * <p>Handles different drug categories:
         * <ul>
         *   <li>Category 13: branded drug products -- fetched directly</li>
         *   <li>Category 18/19: generic drug composites -- the drug code contains
         *       an AI code and form code separated by "+", which are used to look up
         *       a representative generic example</li>
         * </ul>
         *
         * @param pkey the primary key of the drug search record (as a String)
         * @param html if {@code true}, include HTML-formatted content in the result
         * @return a Vector of Hashtables with drug details, or {@code null} if not found
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
                       // Category 13 = branded product, fetch directly by primary key
                       return queryDao.getDrug(pkey, true);
                    }else if (cds.getCategory() == 18 || cds.getCategory() == 19){
                       // Categories 18/19 = generic composites; drug code format is "aiCode+formCode"
                       // Split to extract the Active Ingredient code and pharmaceutical form code
                       int pl = cds.getDrugCode().indexOf("+");
                       String aiCode = cds.getDrugCode().substring(0, pl);
                       String formCode = cds.getDrugCode().substring(pl+1);
                       return queryDao.getMadeGenericExample(aiCode,formCode,false);
                    }
                }
                return null;
        }



        /**
         * Searches for drugs matching the given name and filtered by administration route.
         *
         * @param str the search term (drug name or partial name)
         * @param route the route of administration to filter by (e.g., "ORAL", "TOPICAL")
         * @return a Vector of Hashtables containing matching drug search results
         */
        public Vector list_search_element_route(String str, String route) {
                Vector vec=queryDao.listSearchElementRoute(str,route);
                return vec;
        }

        /**
         * Lists all brand-name products associated with a given drug element (active ingredient).
         *
         * @param drugID the drug identifier to find brands for
         * @return a Vector of Hashtables containing brand name drug records
         */
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

        /**
         * Searches for drugs matching the given name, filtered by the specified drug categories.
         *
         * @param str the search term (drug name or partial name)
         * @param cat a Vector of category identifiers to include in the search
         * @return a Vector of Hashtables containing matching drug search results
         */
        public Vector list_search_element_select_categories(String str, Vector cat) {
                Vector vec=queryDao.listSearchElementSelectCategories(str,cat);
                return vec;
        }
        
        /**
         * Searches for drugs matching the given name with right-anchored matching,
         * filtered by the specified drug categories.
         *
         * @param str the search term (drug name or partial name)
         * @param cat a Vector of category identifiers to include in the search
         * @return a Vector of Hashtables containing matching drug search results
         */
        public Vector list_search_element_select_categories_right(String str, Vector cat) {
            Vector vec=queryDao.listSearchElementSelectCategories(str,cat,false,true);
            return vec;
        }

        /**
         * Retrieves the date a drug product became inactive (was withdrawn or discontinued).
         *
         * @param str the drug identifier to look up
         * @return a Vector containing the inactive date information
         */
        public Vector get_inactive_date(String str ){
             Vector vec=queryDao.getInactiveDate(str);
             return vec;
        }

       /**
        * Retrieves the generic (non-proprietary) name for a drug given its ID.
        *
        * @param drugID the drug identifier to look up
        * @return a Vector containing the generic name(s) for the drug
        */
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
       /**
        * Retrieves the pharmaceutical form (e.g., tablet, capsule, solution) for a drug.
        *
        * @param pKey the primary key of the drug
        * @return a Vector containing the drug's pharmaceutical form information
        */
       public Vector get_form(String pKey) {
                Vector vec=queryDao.getForm(pKey);
                return vec;
        }
        /**
         * Lists drugs belonging to the specified therapeutic drug classes.
         *
         * @param Dclass a Vector of drug class identifiers (e.g., ATC codes)
         * @return a Vector of Hashtables containing drugs in the specified classes
         */
        public Vector list_drug_class(Vector Dclass) {
                Vector vec=queryDao.listDrugClass(Dclass);
                return vec;
        }
     /**
      * Checks a drug (identified by ATC code) against a patient's known allergies
      * and returns any matching warnings.
      *
      * @param atcCode the ATC code of the drug being prescribed
      * @param allergies a Vector of allergy identifiers for the patient
      * @return a Vector of Hashtables containing allergy warning details for any matches
      */
     public Vector get_allergy_warnings(String atcCode, Vector allergies) {

                Vector vec=queryDao.getAllergyWarnings(atcCode,allergies);
                return vec;
    }
     
     /**
      * Retrieves the drug classes associated with the given allergy identifiers.
      *
      * @param allergies a Vector of allergy identifiers
      * @return a Vector of Hashtables containing the associated drug class information
      */
     public Vector get_allergy_classes(Vector allergies) {
         Vector vec=queryDao.getAllergyClasses(allergies);
         return vec; 
     }
     
     /**
      * Retrieves full drug information by its integer primary key.
      *
      * <p>Convenience overload that delegates to {@link #get_drug(String, boolean)}.
      *
      * @param pKey the primary key of the drug record
      * @param html if {@code true}, include HTML-formatted content in the result
      * @return a Vector of Hashtables containing the drug record
      */
     public Vector get_drug(int pKey, boolean html) {
    	 return get_drug(pKey+"", html);
     }
     
     /**
      * Retrieves full drug information by its primary key (as a String).
      *
      * @param pKey the primary key of the drug record (as a String)
      * @param html if {@code true}, include HTML-formatted content in the result
      * @return a Vector of Hashtables containing the drug record
      */
     public Vector get_drug(String pKey, boolean html) {
                Vector vec=queryDao.getDrug(pKey,html);
                return vec;
        }

     /**
      * Generic fetch operation that retrieves a specific attribute for the given key(s).
      *
      * <p>This is a general-purpose query method used by the XML-RPC protocol
      * to retrieve arbitrary drug attributes.
      *
      * @param attribute the name of the attribute to fetch (e.g., "name", "strength")
      * @param key a Vector of key values identifying the drug(s)
      * @return the fetched attribute value, type depends on the attribute requested
      */
     public Object fetch(String attribute, Vector key) {
        Vector services = new Vector();
        boolean b = true;
        Object obj = queryDao.fetch(attribute, key, services, b);
        return obj;
    }

    /**
     * Returns a human-readable identification string for this drugref service instance.
     *
     * @return the service identification string
     */
    public String identify() {

        return queryDao.identify();
    }

    /**
     * Returns the version string of this drugref service.
     *
     * @return the version string
     */
    public String version() {

        return queryDao.version();
    }

    /**
     * Lists the services (XML-RPC method names) available on this drugref instance.
     *
     * @return a Vector of service name strings
     */
    public Vector list_available_services() {

        return queryDao.list_available_services();
    }

    /**
     * Returns a map of this service's capabilities and their supported status.
     *
     * @return a Hashtable mapping capability names to their values
     */
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
        
       String queryStr = "select cds from CdTherapeuticClass cds, CdDrugProduct cdp where cdp.drugIdentificationNumber = :din and cds.drugCode = cdp.drugCode";
       try {
            // Attempt to search with the named query
          Query query = em.createQuery(queryStr);
          query.setParameter("din", din);
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


    /**
     * Retrieves the therapeutic class name for a given ATC code.
     *
     * @param atc the ATC (Anatomical Therapeutic Chemical) classification code
     * @return a Vector containing the ATC class name and related information
     */
    public Vector get_atc_name(String atc){
        Vector result = queryDao.getTcATC(atc);
        return result;
    }
    
    
    /**
     * Checks for drug-drug interactions among a list of drugs identified by their DINs
     * (Drug Identification Numbers), using the Medi-Span interaction database.
     *
     * <p>This method performs pairwise interaction checks between all DINs in the list,
     * plus food/ethanol interaction checks for each individual DIN. Results are returned
     * as a Vector of Hashtables, each representing one interaction with keys including:
     * "id", "updated_at", "name", "body" (HTML-formatted detail), "significance" (severity),
     * "author", "trusted", "evidence", and "reference".
     *
     * <p><b>Medi-Span license expiry handling:</b>
     * <ul>
     *   <li>If the interaction checker is not active (e.g., no license), returns an error message.</li>
     *   <li>If the license expired 50-60 days ago, a warning is prepended but checks still run.</li>
     *   <li>If expired more than 60 days ago, interaction checking is blocked entirely and
     *       only the expiry error is returned (hard cutoff for compliance).</li>
     * </ul>
     *
     * @param listOfDins a Vector of DIN strings representing the patient's current medications
     * @param minSignificates the minimum significance level to include (currently unused by this method)
     * @return a Vector of Hashtables, each representing a detected interaction or status message
     */
    public Vector interaction_by_regional_identifier(Vector listOfDins, int minSignificates){

    	InteractionsChecker interactionsChecker = InteractionsCheckerFactory.getInteractionChecker();
    	Vector retVec = new Vector();
    	logger.debug("Interaction checker active:"+interactionsChecker.interactionsCheckerActive());

    	// If the Medi-Span interaction checker is not active (missing license, failed init, etc.),
    	// return a single error entry explaining the service is unavailable.
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

    	// Check whether the Medi-Span license/data file has expired.
    	// There is a grace period: 50-60 days past expiry shows a warning but still runs;
    	// beyond 60 days the checker is fully disabled (hard cutoff for licensing compliance).
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
    			// Grace period: warn the user but continue with interaction checks below
    			returnHash.put("body", "Interaction Checker has Expired a new version must be installed to continue use");
    			retVec.add(returnHash);
    		}else if(daysBetween > 60){
    			// Hard cutoff: do not perform any interaction checks, return only the error
    			returnHash.put("body", "Interaction Checker has Expired, a new version must be installed");
    			Vector v = new Vector();
    			v.add(returnHash);
            	return v;
    		}

    	}

    	// Phase 1: Pairwise drug-drug interaction check.
    	// Compare every DIN against every other DIN (including self-pairs, which the
    	// checker handles internally). This is an O(n^2) check over the medication list.
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

    	// Phase 2: Food and ethanol interaction check.
    	// Each DIN is individually checked for interactions with food or alcohol.
    	for (String din1 :(List<String>)listOfDins){
    		List<InteractionRecord> foodInteractions = interactionsChecker.checkForFoodAndEthanol(din1);

    		logger.debug("food "+foodInteractions);
    		if(foodInteractions != null){
    			interactionsFull.addAll(foodInteractions);
    		}
    	}

		logger.debug("interactionsFull "+interactionsFull.size());

    	// Phase 3: Convert InteractionRecord objects into Hashtable maps for XML-RPC transport.
    	// Each record includes the warning text, effect, mechanism, management advice,
    	// discussion, references, and Medi-Span copyright/disclaimer information.
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
    
    /**
     * Returns the input string, or "n/a" if the input is {@code null}.
     * Used to ensure Hashtable values are never null (Hashtable does not allow null values).
     */
    private String nullCheck(String s){
    	if(s ==null){
    		return "n/a";
    	}
    	return s;
    }
    


}
