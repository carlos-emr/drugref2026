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
package io.github.carlos_emr.drugref2026.ca.dpd.fetch;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import io.github.carlos_emr.drugref2026.ca.dpd.CdActiveIngredients;
import io.github.carlos_emr.drugref2026.ca.dpd.CdDrugProduct;
import io.github.carlos_emr.drugref2026.ca.dpd.CdDrugSearch;
import io.github.carlos_emr.drugref2026.ca.dpd.LinkGenericBrand;
import io.github.carlos_emr.drugref2026.util.JpaUtils;

/**
 * Builds the {@code cd_drug_search} index table and {@code link_generic_brand} mapping table
 * from raw DPD data imported into the database.
 *
 * <p>This class is responsible for creating the searchable drug name entries that power the
 * drug search UI. It processes the raw DPD tables to generate entries in several categories:</p>
 * <ul>
 *   <li><b>Category 13 (Brand names):</b> One entry per drug product, using the brand name from
 *       {@link CdDrugProduct}.</li>
 *   <li><b>Category 8 (ATC codes):</b> Distinct ATC code/name pairs from
 *       {@link io.github.carlos_emr.drugref2026.ca.dpd.CdTherapeuticClass}.</li>
 *   <li><b>Category 11 (Generic compound):</b> Single-ingredient generic names derived from
 *       active ingredient names.</li>
 *   <li><b>Category 12 (Generic):</b> Multi-ingredient composite generic names (ingredients
 *       joined with "/ ").</li>
 *   <li><b>Category 14 (Ingredient):</b> Distinct individual ingredient names from
 *       {@link CdActiveIngredients}.</li>
 * </ul>
 *
 * <p>After initial import, a cleanup phase disambiguates duplicate brand names by appending
 * strength information (e.g., "TYLENOL" becomes "TYLENOL 500MG") when multiple products
 * share the same brand name but have different strengths.</p>
 *
 * <p>The {@link LinkGenericBrand} table is populated during generic import to link each generic
 * entry (category 11/12) to the individual branded drug products that contain those ingredients.</p>
 *
 * @author jaygallagher
 */
public class ConfigureSearchData {
    /** Cache mapping brand names (category 13) to lists of drug codes for name disambiguation. */
    private Hashtable<String, List<String>> name_drugcode_cat13=new Hashtable();
    /** Cache mapping drug codes to lists of ingredient info arrays [ingredient, strength, strengthUnit]. */
    private Hashtable<String,List<String[]>> ingred_info=new Hashtable();

    /**
     * Constructs a new ConfigureSearchData instance and pre-loads the ingredient information
     * cache from the database for use during the name cleanup phase.
     */
    public ConfigureSearchData(){

        initIngredInfo();

        //System.out.println("ingred_info="+ingred_info);
    }
    /**
     * Pre-loads the ingredient information cache by querying all active ingredients from the
     * database. Builds a map from drug code to a list of [ingredient, strength, strengthUnit]
     * arrays for efficient lookup during the name cleanup phase.
     */
   private void initIngredInfo(){
            EntityManager em=JpaUtils.createEntityManager();
            Query q=em.createQuery("select cai from CdActiveIngredients cai ");
            List<CdActiveIngredients> ls=q.getResultList();
            for(CdActiveIngredients cai:ls){
                String dc=""+cai.getDrugCode();
                if(ingred_info.containsKey(dc)){
                    List<String[]> l=ingred_info.get(dc);
                    String[] arr=new String[] {cai.getIngredient(),cai.getStrength(),cai.getStrengthUnit()};
                    l.add(arr);
                    ingred_info.put(dc, l);
                }else{
                    List<String[]> larr=new ArrayList();
                    String[] arr=new String[] {cai.getIngredient(),cai.getStrength(),cai.getStrengthUnit()};
                    larr.add(arr);
                    ingred_info.put(""+cai.getDrugCode(), larr);
                }
            }
            JpaUtils.close(em);
        }
    /**
     * Builds the name-to-drug-code mapping for category 13 (brand name) entries. This map
     * is used during the cleanup phase to find all drug codes that share a given brand name,
     * so that strength information can be appended to disambiguate them.
     */
    private void initNameDrugCode(){
            EntityManager em=JpaUtils.createEntityManager();
            Query query = em.createQuery("select cds from CdDrugSearch cds where cds.category=13");

            List<CdDrugSearch> productList = query.getResultList();
            //System.out.println("in initNameDrugCode, productList.size="+productList.size());
            for(CdDrugSearch cds:productList){
                String name=cds.getName();
                String drugcode=cds.getDrugCode();
                //System.out.println("in initNameDrugCode, name="+name+"--drugcode="+drugcode);
                if(name_drugcode_cat13.containsKey(name)){
                    List<String> dc=name_drugcode_cat13.get(name);
                    dc.add(drugcode);
                    name_drugcode_cat13.put(name, dc);
                }else{
                    List<String> ls=new ArrayList();
                    ls.add(drugcode);
                    name_drugcode_cat13.put(name, ls);
                }

            }
            JpaUtils.close(em);
        }

    /**
     * Main entry point: imports all search data categories into the cd_drug_search table.
     *
     * <p>Executes the import pipeline in sequence:</p>
     * <ol>
     *   <li>{@link #importAllBrandName} -- Category 13 brand names</li>
     *   <li>{@link #importAllATCCodeName} -- Category 8 ATC codes</li>
     *   <li>{@link #importGenerics} -- Categories 11/12 generics + link_generic_brand population</li>
     *   <li>{@link #importAllIngredients} -- Category 14 individual ingredients</li>
     *   <li>{@link #cleanUpSearchNames} -- Disambiguates duplicate brand names with strength info</li>
     * </ol>
     *
     * @param em the EntityManager to use for all database operations
     */
    public void importSearchData(EntityManager em){
        long startBN=System.currentTimeMillis();
        ////Importing Brand Information
        importAllBrandName(em);
        long afterBN=System.currentTimeMillis();
        System.out.println("============time import BN="+(afterBN-startBN));
        ////print "Import ATC Names"
        importAllATCCodeName(em);
        long afterATC=System.currentTimeMillis();
        System.out.println("============time import ATC="+(afterATC-afterBN));
        ////Import AFHC Names
        // Deprecated July 1st, 2022
//        importAllAHFSCodeName(em);
//        long afterAHFS=System.currentTimeMillis();
//        System.out.println("============time import AHFS="+(afterAHFS-afterATC));
        ////Import Generic Data
        importGenerics(em);
        long afterGenerics=System.currentTimeMillis();
        System.out.println("============time import Generics="+(afterGenerics-afterBN));
        ////Import Ingredients
        importAllIngredients(em);
        long afterIngredients=System.currentTimeMillis();
        System.out.println("============time import Ingredients="+(afterIngredients-afterGenerics));
        initNameDrugCode();
        //System.out.println("name_drugcode_cat13="+name_drugcode_cat13);
        ////Cleaning up Search Names
        cleanUpSearchNames(em);
        long afterClean=System.currentTimeMillis();
        System.out.println("============time  Clean="+(afterClean-afterIngredients));

        
        //print "Should check for duplicates and ones that need forms and strength added ie ones from generic companys with the form and strength is not included in the name"


    }

  
    /**
     * Imports all brand names (category 13) into the search table. Creates one CdDrugSearch
     * entry per CdDrugProduct record, using the product's brand name and drug code.
     *
     * @param em the EntityManager for persistence
     */
      public void importAllBrandName(EntityManager em){
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        System.out.println("Importing Brand Names");
        Query queryDrugProduct = em.createQuery("SELECT cdp from CdDrugProduct cdp");

        List<CdDrugProduct> productList = queryDrugProduct.getResultList();
       
        System.out.println("Have got all the products in memory");
        
        for (CdDrugProduct drug:productList){
            CdDrugSearch drugSearch = new CdDrugSearch();
            drugSearch.setDrugCode(""+drug.getDrugCode());
            drugSearch.setName(drug.getBrandName());
            drugSearch.setCategory(13);
            em.persist(drugSearch);
            em.flush();
            em.clear();
            
        }
        System.out.println("DONE Import Brand Name");
        tx.commit();
      }

    
    /**
     * Imports distinct ATC therapeutic classification codes (category 8) into the search table.
     * Each unique ATC code/description pair from CdTherapeuticClass becomes one search entry,
     * with the ATC number as the drug code and the ATC description as the name.
     *
     * @param em the EntityManager for persistence
     */
       public void importAllATCCodeName(EntityManager em){
           EntityTransaction tx = em.getTransaction();
        tx.begin();
        System.out.println("Import ATC Code Name");
        //Query queryDrugProduct = em.createNativeQuery("SELECT distinct cdp.tc_Atc, cdp.tc_Atc_Number from Cd_Therapeutic_Class cdp");

        Query queryDrugProduct = em.createQuery("SELECT distinct cdp.tcAtc, cdp.tcAtcNumber from CdTherapeuticClass cdp");

        List<Object[]> productList = queryDrugProduct.getResultList();

        //System.out.println("Have got all the products in memory");

        for (Object[] drug:productList){
        //    System.out.println(drug.getClass().getName()+" "+drug[0]);

            CdDrugSearch drugSearch = new CdDrugSearch();
            drugSearch.setDrugCode(""+drug[1]);
            drugSearch.setName(""+drug[0]);
            drugSearch.setCategory(8);
            em.persist(drugSearch);
            em.flush();
            em.clear();
             
             
        }
        System.out.println("DONE Import ATC Code Name");
        tx.commit();
       }

    /**
     * @Deprecated
     * The DPD eleminated the AHFS code and description as of
     * July 1st, 2022
     */
    public void importAllAHFSCodeName(EntityManager em){
           EntityTransaction tx = em.getTransaction();
        tx.begin();
        System.out.println("Import AHFS Code Name");
        //Query queryDrugProduct = em.createNativeQuery("SELECT distinct cdp.tc_Atc, cdp.tc_Atc_Number from Cd_Therapeutic_Class cdp");

        Query queryDrugProduct = em.createQuery("SELECT distinct cdp.tcAhfs, cdp.tcAhfsNumber from CdTherapeuticClass cdp");

        List<Object[]> productList = queryDrugProduct.getResultList();

        //System.out.println("Have got all the products in memory");

        for (Object[] drug:productList){
       //     System.out.println(drug.getClass().getName()+" "+drug[0]);
            CdDrugSearch drugSearch = new CdDrugSearch();
            drugSearch.setDrugCode(""+drug[1]);
            drugSearch.setName(""+drug[0]);
            drugSearch.setCategory(10);
            em.persist(drugSearch);
            em.flush();
            em.clear();


        }
        System.out.println("DONE Import AHFS Code Name");
        tx.commit();
       }


    /**
     * Imports generic drug names (categories 11 and 12) and populates the link_generic_brand table.
     *
     * <p>For each drug product, concatenates its active ingredient names with "/ " separators.
     * Single-ingredient generics get category 11; multi-ingredient composites get category 12.
     * If the same generic name was already created (from another product with the same ingredients),
     * only a link_generic_brand record is created to map the new product to the existing generic.</p>
     *
     * <p>After all generics are imported, sets drugCode = id for categories 11/12, making
     * these entries self-referencing (the search table id serves as the drug code for generics).</p>
     *
     * @param em the EntityManager for persistence
     */
        public void importGenerics(EntityManager em){
            Hashtable genHash = new Hashtable();
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            //System.out.println("Import Generics");
            Query queryDrugProduct = em.createQuery("SELECT cdp.drugCode from CdDrugProduct cdp");

            List<Integer> productList = queryDrugProduct.getResultList();          

            //System.out.println("Have got all the products in memory");
            Query ingredientQuery = em.createQuery("select cai.ingredient from CdActiveIngredients cai where cai.drugCode =(:drugCode)");
            int county = 0;
            for (Integer drug:productList){

                ingredientQuery.setParameter("drugCode", drug);
                List<String> ingredients = ingredientQuery.getResultList();
                int bool = 0;
                int compositeGeneric = 0; // Flag: 1 if this is a multi-ingredient combination
                String genName = "";
                if (ingredients.size() > 0){
                    // Build the generic name by concatenating ingredient names with "/ " separator
                    for (String ingredient:  ingredients){
                            if ( bool ==1){
                                    genName = genName + "/ ";
                                    compositeGeneric = 1; // Multiple ingredients = composite generic
                            }
                            genName = genName + ingredient;
                            bool = 1;
                    }
                    if (genHash.containsKey(genName)){
                       // Generic name already exists: just add a link from existing generic to this brand
                       LinkGenericBrand genBrand = new LinkGenericBrand();
                       Integer genBrandId = (Integer) genHash.get(genName);
                       genBrand.setId(genBrandId);
                       genBrand.setDrugCode(""+drug);
                       em.persist(genBrand);
                       em.flush();
                       em.clear();//#print " just add %s to linking table " % genName
                                                //ida.insert_into_link_generic_brand(genHash[genName],result['drug_code'])
                    }else{
                       // New generic name: create a search entry and link it to this brand
                       CdDrugSearch drugSearch = new CdDrugSearch();
                        drugSearch.setName(genName);
                        // Assign category based on ingredient count:
                        // 12 = multi-ingredient composite generic, 11 = single-ingredient generic
                        if (compositeGeneric == 1){
                            drugSearch.setCategory(12);
                        }else{
                            drugSearch.setCategory(11);
                        }
                        em.persist(drugSearch);
                        em.flush();


                        Integer drugId = drugSearch.getId();
                   //     System.out.println("ID WAS"+drugId);
                        genHash.put(genName,drugId);
                        em.clear();

                        if(genHash.containsKey(genName)){
                            LinkGenericBrand genBrand = new LinkGenericBrand();
                            Integer genBrandId = (Integer) genHash.get(genName);
                            genBrand.setId(genBrandId);
                            genBrand.setDrugCode(""+drug);
                            em.persist(genBrand);
                            em.flush();
                    // System.out.println("id added to genBrand="+genBrandId+" || drugCode added to genBrand="+drug.toString());
                            em.clear();
                        }                  
                    }
                }                
            }
            // For generic entries (cat 11/12), set drugCode = id so they are self-referencing.
            // This makes the search table id serve as the "drug code" for generics.
            Query updateQuery = em.createQuery("update CdDrugSearch cds set cds.drugCode = cds.id where cds.category = 11 or cds.category = 12");
            updateQuery.executeUpdate();
            em.flush();
            em.clear();
            // System.out.println("DONE Import Generics");
            tx.commit();
        }

     



        /*
         def import_all_Ingredients(self):
                con = dbapi.connect(database=self._db)
                cur = con.cursor()
                query = "select  distinct ingredient, active_ingredient_code  from cd_active_ingredients"

                print query
                cur.execute(query)
                results = cur.fetchall()
                if len(results)>0:
                        ida = ImportSearchData()
                        for result in results:
                                if result['ingredient'] != "" :
                                        ida.insert_into_drug_search(con,result['active_ingredient_code'],result['ingredient'],'14')

         */
    /**
     * Imports distinct individual ingredient names (category 14) into the search table.
     * Each unique ingredient name/code pair from CdActiveIngredients becomes one search entry,
     * with the active_ingredient_code as the drug code and the ingredient name as the name.
     *
     * @param em the EntityManager for persistence
     */
         public void importAllIngredients(EntityManager em){
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            //System.out.println("Import Ingredients");

            Query queryDrugProduct = em.createQuery("SELECT distinct cai.ingredient, cai.activeIngredientCode from CdActiveIngredients cai");

            List<Object[]> productList = queryDrugProduct.getResultList();

            //System.out.println("Have got all the products in memory");

            for (Object[] drug:productList){
            //    System.out.println(drug.getClass().getName()+" "+drug[0]);

                CdDrugSearch drugSearch = new CdDrugSearch();
                drugSearch.setDrugCode(""+drug[1]);
                drugSearch.setName(""+drug[0]);
                drugSearch.setCategory(14);
                em.persist(drugSearch);
                em.flush();
                em.clear();


            }
//System.out.println("DONE Import Ingredients");
            tx.commit();
       }


    


    /**
     * Returns the list of drug codes associated with a given brand name from the cached
     * name-to-drug-code mapping (category 13 entries).
     *
     * @param name the brand name to look up
     * @return a list of drug code strings, or an empty list if the name is not found
     */
        public List<String> getDrugCodeFromName(String name){
            if(name_drugcode_cat13.containsKey(name))
                    return name_drugcode_cat13.get(name);
            else{
                List<String> emptyList=new ArrayList();
                return emptyList;
            }
        }

    /**
     * Disambiguates duplicate brand names in the search table by appending strength information.
     *
     * <p>Finds all category 13 brand names that appear more than once (same name, different
     * drug codes). For each duplicate group, if the product has a single active ingredient
     * whose strength is not already present in the name, appends the strength and unit
     * (e.g., "TYLENOL" becomes "TYLENOL 500MG").</p>
     *
     * @param em the EntityManager for database operations
     */
        public void cleanUpSearchNames(EntityManager em){
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            //System.out.println("Clean Up Search Names");

            Query queryDrugProduct = em.createQuery("select cds.name ,count(cds.name)  from CdDrugSearch cds where cds.category = 13 group by cds.name ");

            List<Object[]> productList = queryDrugProduct.getResultList();

            //System.out.println("Have got all the products in memory");

            for (Object[] drug:productList){
                //System.out.println(drug.getClass().getName()+" "+drug[0]+"---"+drug[1]);
                String name = ""+drug[0];
                Long num = (Long) drug[1];
                if (num > 1){
                    //System.out.println("Donig something with "+drug[0]);
                    //List<String> drugCodeList = getDrugCodeForName( em, ""+drug[0]);
                    List<String> drugCodeList=getDrugCodeFromName(name);

                    Query updateDrugSearch = em.createQuery("update CdDrugSearch cds set cds.name = (:NAME) where cds.drugCode = (:DRUGCODE) and cds.category = 13");
                    for (String code :drugCodeList){
                        //System.out.println("Going to work on "+code);
                                                List<String[]> strens=new ArrayList();
                                                strens= getIngredInfoFromDrugCode(code);
                                                //System.out.println(code+"--:--"+name+"--:--"+strens.size());
                                                String suggName = getSuggestedNewName(name, strens);
                                                //System.out.println(suggName);
                                                if (!suggName.equals("")){
                                                    updateDrugSearch.setParameter("NAME", suggName);
                                                    updateDrugSearch.setParameter("DRUGCODE", code);
                                                    updateDrugSearch.executeUpdate();
                                                    em.flush();
                                                    
                                                }
                                                //       drug_code_name.append({'drug_code':code,'name':suggName})

                    }
                }
                
            }

            tx.commit();

        }


      /*  private String getSuggestedNewName(String name,List<Object[]> strengths){
            String suggested_name = "";
            int  numStrenFoundInName = 0;
                for (Object[] strens : strengths){
                        boolean strenFoundInName = name.contains(""+strens[1]);
                        if (strenFoundInName){
                                numStrenFoundInName++;
                        }

                }
                if (numStrenFoundInName == 0 && strengths.size() == 1){
                        Object[] strens = strengths.get(0);
                        suggested_name = name+" " +strens[1]+""+strens[2];
                }
                return suggested_name;
        }
        */
    /**
     * Generates a suggested disambiguated name by appending strength information.
     *
     * <p>Returns a new name with strength appended only if: (1) the product has exactly one
     * active ingredient, and (2) that ingredient's strength is not already present in the
     * name. Returns an empty string if no modification is needed.</p>
     *
     * @param name the current brand name
     * @param strengths a list of [ingredient, strength, strengthUnit] arrays
     * @return the suggested new name with strength appended, or empty string if no change needed
     */
           private String getSuggestedNewName(String name,List<String[]> strengths){
            String suggested_name = "";
            int  numStrenFoundInName = 0;
                for (String[] strens : strengths){
                        boolean strenFoundInName = name.contains(""+strens[1]);
                        if (strenFoundInName){
                                numStrenFoundInName++;
                        }

                }
                if (numStrenFoundInName == 0 && strengths.size() == 1){
                        String[] strens = strengths.get(0);
                        suggested_name = name+" " +strens[1]+""+strens[2];
                }
                return suggested_name;
        }


        private List<String> getDrugCodeForName(EntityManager em, String name){
            
            Query queryDrugProduct = em.createQuery("select cds.drugCode from CdDrugSearch cds where cds.category = 13 and cds.name = (:NAME)");

            queryDrugProduct.setParameter("NAME", name);

            List<String> productList = queryDrugProduct.getResultList();

            return productList;
        }

 
        
        private List<String[]> getIngredInfoFromDrugCode(String drugCode){
                List<String[]> retls=new ArrayList();
                retls=ingred_info.get(drugCode);
                if(retls==null){
                    List<String[]> r=new ArrayList();
                    return r;
                }
                return retls;
        }
        private List<Object[]> getStrengthsFromDrugCode(EntityManager em, String drugCode){

            Query queryDrugProduct = em.createQuery("select cai.ingredient, cai.strength, cai.strengthUnit from CdActiveIngredients cai where cai.drugCode = (:drugCode)");

            queryDrugProduct.setParameter("drugCode", new Integer(drugCode));

            List<Object[]> productList = queryDrugProduct.getResultList();

            return productList;
        }
}
