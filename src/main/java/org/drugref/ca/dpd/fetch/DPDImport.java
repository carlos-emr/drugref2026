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
package org.drugref.ca.dpd.fetch;

import java.io.*;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.drugref.ca.dpd.CdActiveIngredients;
import org.drugref.ca.dpd.CdDrugProduct;
import org.drugref.ca.dpd.CdDrugSearch;
import org.drugref.util.DrugrefProperties;
import org.drugref.util.JpaUtils;
import org.drugref.util.MiscUtils;

/**
 *
 * @author jaygallagher
 */

 
public class DPDImport {


    private static Logger logger = MiscUtils.getLogger();
    String dpd_url = DrugrefProperties.getInstance().getProperty("DPD_BASE_URL", "https://www.canada.ca/content/dam/hc-sc/documents/services/drug-product-database");
    public File getZipStream() throws Exception {
        String sUrl = dpd_url + "/allfiles.zip";
        return getZipStream(sUrl);
    }

    public File getInactiveZipStream() throws Exception {
            String sUrl = dpd_url + "/Allfiles_ia-Oct10.zip";
            // WARNING Allfiles_ia-Oct10.zip is data from 2018, code should be updated to handle new format in Allfiles_ia.zip
                    return getZipStream(sUrl);
    }

    public File getInactiveTableZipStream() throws Exception {
        String sUrl = dpd_url + "/inactive.zip";
        return getZipStream(sUrl);
    }

    private File getZipStream(String sUrl) throws IOException {
        // Get stream from URL
        URL url = new URL(sUrl);
        InputStream is = url.openStream();

        // Create output on local disk
        File f = File.createTempFile("stream", ".zip");
        FileOutputStream fos = new FileOutputStream(f);

        // Copy contents to disk
        try {
            IOUtils.copy(is, fos);
        } catch(IOException e) {
            System.out.println("Could not open stream temp file");
        } finally {
            is.close();
            fos.flush();
            fos.close();
        }
        return f;
    }

    public List getDPDTablesDrop() {
        List<String> arrList = new ArrayList();
        //table names are case sensitive.
        String[] tableNames = {"cd_drug_product", "cd_companies", "cd_active_ingredients", "cd_drug_status", "cd_form", "cd_inactive_products",
            "cd_packaging", "cd_pharmaceutical_std", "cd_route", "cd_schedule", "cd_therapeutic_class", "cd_veterinary_species", "interactions"};
        for (String tableName : tableNames) {
            if (isTablePresent(tableName)) {
                String statement = "DROP TABLE " + tableName;
                arrList.add(statement);
            } else {
            }
        }

        p("arrList", arrList.toString());
        return arrList;
    }

    private boolean isTablePresent(String tableName) {//check if a table exists in the database
        boolean bool = false;
        Connection con = null;
        DrugrefProperties dp=DrugrefProperties.getInstance();
        String dbURL = dp.getDbUrl()+"?serverTimezone=UTC";
        String dbUser = dp.getDbUser();
        String dbPassword = dp.getDbPassword();

        try {
            con = DriverManager.getConnection(dbURL, dbUser, dbPassword);

        } catch (SQLException e) {
            System.out.println("Connection Failed.");
            e.printStackTrace();
            bool = false;
        }
        try {
            DatabaseMetaData dbm = con.getMetaData();
            ResultSet rs = dbm.getTables(null, null, tableName, null);
            if (rs.next()) {
                bool = true;
            } else {
                bool = false;
            }
            con.close();//release resources
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bool;
    }

    public List getDPDTables() {
        List<String> arrList = new ArrayList();

        arrList.add("CREATE TABLE  cd_drug_product  (id serial  PRIMARY KEY,drug_code  int default NULL,product_categorization  varchar(80) default NULL,   class  varchar(40) default NULL,   drug_identification_number  varchar(8) default NULL,   brand_name  varchar(200) default NULL, descriptor varchar(150) default NULL, pediatric_flag  char(1) default NULL,   accession_number  varchar(5) default NULL,   number_of_ais  varchar(10) default NULL,   last_update_date  date default NULL,ai_group_no  varchar(10) default NULL,company_code int);");
        arrList.add("CREATE TABLE  cd_companies  (id serial  PRIMARY KEY,   drug_code   int default NULL,   mfr_code  varchar(5) default NULL,   company_code   int default NULL,   company_name  varchar(80) default NULL,   company_type  varchar(40) default NULL,   address_mailing_flag  char(1) default NULL,   address_billing_flag  char(1) default NULL,   address_notification_flag  char(1) default NULL,   address_other  varchar(20) default NULL,   suite_number  varchar(20) default NULL,   street_name  varchar(80) default NULL,   city_name  varchar(60) default NULL,   province  varchar(40) default NULL,   country  varchar(40) default NULL,   postal_code  varchar(20) default NULL,   post_office_box  varchar(15) default NULL);");
        arrList.add("CREATE TABLE  cd_active_ingredients  ( id serial  PRIMARY KEY,  drug_code   int default NULL,   active_ingredient_code   int default NULL,   ingredient  varchar(240) default NULL,   ingredient_supplied_ind  char(1) default NULL,   strength  varchar(20) default NULL,   strength_unit  varchar(40) default NULL,   strength_type  varchar(40) default NULL,   dosage_value  varchar(20) default NULL,   base  char(1) default NULL,   dosage_unit  varchar(40) default NULL,   notes  text);");
        arrList.add("CREATE TABLE  cd_drug_status  (id serial  PRIMARY KEY,   drug_code   int default NULL,   current_status_flag  char(1) default NULL,   status  varchar(40) default NULL,   history_date  date default NULL);");
        arrList.add("CREATE TABLE  cd_form  (id serial  PRIMARY KEY,   drug_code   int default NULL,   pharm_cd_form_code   int default NULL,   pharmaceutical_cd_form  varchar(65) default NULL);");
        arrList.add("CREATE TABLE  cd_inactive_products  (id serial  PRIMARY KEY,   drug_code   int default NULL,   drug_identification_number  varchar(255) default NULL,   brand_name  varchar(200) default NULL,   history_date  date default NULL);");
        arrList.add("CREATE TABLE  cd_packaging  (id serial  PRIMARY KEY,   drug_code   int default NULL,   upc  varchar(12) default NULL,   package_size_unit  varchar(40) default NULL,   package_type  varchar(40) default NULL,   package_size  varchar(5) default NULL,   product_inforation  varchar(80) default NULL);");
        arrList.add("CREATE TABLE  cd_pharmaceutical_std  (id serial  PRIMARY KEY,   drug_code   int default NULL,   pharmaceutical_std  varchar(40) default NULL);");
        arrList.add("CREATE TABLE  cd_route  (id serial  PRIMARY KEY,   drug_code   int default NULL,   route_of_administration_code   int default NULL,   route_of_administration  varchar(40) default NULL);");
        arrList.add("CREATE TABLE  cd_schedule  (id serial  PRIMARY KEY,   drug_code   int default NULL,   schedule  varchar(40) default NULL);");
        arrList.add("CREATE TABLE  cd_therapeutic_class  (id serial  PRIMARY KEY,   drug_code   int default NULL,   tc_atc_number  varchar(8) default NULL,   tc_atc  varchar(120) default NULL,   tc_ahfs_number  varchar(20) default NULL,   tc_ahfs  varchar(80) default NULL,	tc_atc_f  varchar(240) default NULL);");
        arrList.add("CREATE TABLE  cd_veterinary_species  (id serial  PRIMARY KEY,   drug_code   int default NULL,   vet_species  varchar(80) default NULL,   vet_sub_species  varchar(80) default NULL);");
      
        arrList.add("CREATE TABLE  interactions  (id serial PRIMARY KEY, affectingatc varchar(8), affectedatc varchar(8) default NULL, effect char(1) default NULL, significance char(1) default NULL, evidence char(1) default NULL, comment text default NULL, affectingdrug text default NULL, affecteddrug text default NULL, CONSTRAINT UNQ_ATC_EFFECT UNIQUE (affectingatc, affectedatc, effect));");
        if (!isTablePresent("utility")) {
            arrList.add("CREATE TABLE `utility` (`id` serial PRIMARY KEY, `drug_identification_number` varchar(8) DEFAULT NULL, `brand_name` varchar(200) DEFAULT NULL, `descriptor` varchar(150) DEFAULT NULL,  `tc_atc_number` varchar(8) DEFAULT NULL,  `tc_atc` varchar(120) DEFAULT NULL,  `tc_ahfs_number` varchar(20) DEFAULT NULL,  `tc_ahfs` varchar(80) DEFAULT NULL);");
        }
        return arrList;
    }
    private List getHistoryTable(){
        List<String> l=new ArrayList();
        l.add("CREATE TABLE history (id serial PRIMARY KEY,date_time datetime,action varchar(20))");
        return l;

    }
    public List addIndexToTables() {
        List<String> arrList = new ArrayList();

        arrList.add("create index  cd_active_ingredients_drug_code_idx on   cd_active_ingredients(drug_code);");
       arrList.add("create index  cd_drug_status_drug_code_idx on   cd_drug_status(drug_code);");
       arrList.add("create index  cd_form_drug_code_idx on    cd_form(drug_code);");
       arrList.add("create index  cd_inactive_products_drug_code_idx on    cd_inactive_products(drug_code);");
       arrList.add("create index  cd_packaging_drug_code_idx on   cd_packaging(drug_code);");
       arrList.add("create index  cd_pharmaceutical_std_drug_code_idx on   cd_pharmaceutical_std(drug_code);");
       arrList.add("create index  cd_route_drug_code_idx on     cd_route(drug_code);");
       arrList.add("create index  cd_schedule_drug_code_idx on     cd_schedule(drug_code);");
       arrList.add("create index  cd_therapeutic_class_drug_code_idx on     cd_therapeutic_class(drug_code);");
       arrList.add("create index  cd_veterinary_species_drug_code_idx on     cd_veterinary_species(drug_code);");

       arrList.add("CREATE INDEX idx_cd_inactive_products_idNumber on cd_inactive_products (drug_identification_number(8));");
       arrList.add("CREATE INDEX idx_cd_drug_product_aiGroupNo on cd_drug_product (ai_group_no);"); 

        arrList.add("create index cd_company_drug_code_idx on cd_companies(drug_code);");
        arrList.add("create index cd_drug_code_idx on cd_drug_product(drug_code);");
        arrList.add("update cd_drug_product join cd_companies using (drug_code) set cd_drug_product.company_code = cd_companies.company_code");

        return arrList;
    }

    public List addIndexToSearchTable(){
        List<String> arrList = new ArrayList();
        //add indexing to every column in cd_drug_search
       arrList.add("create index  cd_drug_search_id_idx on  cd_drug_search(id);");
       arrList.add("create index  cd_drug_search_drug_code_idx on cd_drug_search(drug_code);");
       arrList.add("create index  cd_drug_search_category_idx on cd_drug_search(category);");
       DrugrefProperties dp=DrugrefProperties.getInstance();
       if(dp.isPostgres())
            arrList.add("create index  cd_drug_search_name_idx on cd_drug_search(name);");
       else if(dp.isMysql())
            arrList.add("create index  cd_drug_search_name_idx on cd_drug_search(name(70));");
       return arrList;

    }

    public List addTALLman() {
        List<String> arrList = new ArrayList();
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AFATINIB' , 'AFAtinib') WHERE `name` LIKE '%AFATINIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AXITINIB' , 'aXitinib') WHERE `name` LIKE '%AXITINIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AMLODIPINE' , 'amLODIPine') WHERE `name` LIKE '%AMLODIPINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AZACITIDINE' , 'azaCITIDine') WHERE `name` LIKE '%AZACITIDINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'AZATHIOPRINE' , 'azaTHIOprine') WHERE `name` LIKE '%AZATHIOPRINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'CARBOPLATIN' , 'CARBOplatin') WHERE `name` LIKE '%CARBOPLATIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'CISPLATIN' , 'CISplatin') WHERE `name` LIKE '%CISPLATIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'CYCLOSERINE' , 'cycloSERINE') WHERE `name` LIKE '%CYCLOSERINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'CYCLOSPORINE' , 'cycloSPORINE') WHERE `name` LIKE '%CYCLOSPORINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DABRAFENIB' , 'daBRAFenib') WHERE `name` LIKE '%DABRAFENIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DASATINIB' , 'daSATinib') WHERE `name` LIKE '%DASATINIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DACTINOMYCIN' , 'DACTINomycin') WHERE `name` LIKE '%DACTINOMYCIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DAUNORUBICIN' , 'DAUNOrubicin') WHERE `name` LIKE '%DAUNORUBICIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOXORUBICIN' , 'DOXOrubicin') WHERE `name` LIKE '%DOXORUBICIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DEXMEDETOMIDINE' , 'dexmedeTOMidine') WHERE `name` LIKE '%DEXMEDETOMIDINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DILTIAZEM' , 'dilTIAZem') WHERE `name` LIKE '%DILTIAZEM%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DIMENHYDRINATE' , 'dimenhyDRINATE') WHERE `name` LIKE '%DIMENHYDRINATE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DIPHENHYDRAMINE' , 'diphenhydrAMINE') WHERE `name` LIKE '%DIPHENHYDRAMINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOBUTAMINE' , 'DOBUTamine') WHERE `name` LIKE '%DOBUTAMINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOPAMINE' , 'DOPamine') WHERE `name` LIKE '%DOPAMINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOCETAXEL' , 'DOCEtaxel') WHERE `name` LIKE '%DOCETAXEL%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'PACLITAXEL' , 'PACLitaxel') WHERE `name` LIKE '%PACLITAXEL%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'DOXORUBICIN' , 'DOXOrubicin') WHERE `name` LIKE '%DOXORUBICIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'IDARUBICIN' , 'IDArubicin') WHERE `name` LIKE '%IDARUBICIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'EPHEDRINE' , 'ePHEDrine') WHERE `name` LIKE '%EPHEDRINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'ERIBULIN' , 'eriBULin') WHERE `name` LIKE '%ERIBULIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'SUFENTANIL' , 'SUFentanil') WHERE `name` LIKE '%SUFENTANIL%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'HYDROMORPHONE' , 'HYDROmorphone') WHERE `name` LIKE '%HYDROMORPHONE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'HYDROXYUREA' , 'hydroxyUREA') WHERE `name` LIKE '%HYDROXYUREA%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'IBRUTINIB' , 'iBRUtinib') WHERE `name` LIKE '%IBRUTINIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'IMATINIB' , 'iMAtinib') WHERE `name` LIKE '%IMATINIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'INFLIXIMAB' , 'inFLIXimab') WHERE `name` LIKE '%INFLIXIMAB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'RITUXIMAB' , 'riTUXimab') WHERE `name` LIKE '%RITUXIMAB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'LAMIVUDINE' , 'lamiVUDine') WHERE `name` LIKE '%LAMIVUDINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'LAMOTRIGINE' , 'lamoTRIgine') WHERE `name` LIKE '%LAMOTRIGINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'MITOXANTRONE' , 'mitoXANTRONE') WHERE `name` LIKE '%MITOXANTRONE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'NILOTINIBB' , 'niLOtinib') WHERE `name` LIKE '%NILOTINIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'NILUTAMIDE' , 'niLUTAmide') WHERE `name` LIKE '%NILUTAMIDE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'OBINUTUZUMAB' , 'oBINutuzumab') WHERE `name` LIKE '%OBINUTUZUMAB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'OFATUMUMAB' , 'oFAtumumab') WHERE `name` LIKE '%OFATUMUMAB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'PANITUMUMAB' , 'PANitumumab') WHERE `name` LIKE '%PANITUMUMAB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'PERTUZUMAB' , 'PERTuzumab') WHERE `name` LIKE '%PERTUZUMAB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'QUINIDINE' , 'quiNIDine') WHERE `name` LIKE '%QUINIDINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'QUININE' , 'quiNINE') WHERE `name` LIKE '%QUININE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'SAXAGLIPTIN' , 'sAXagliptin') WHERE `name` LIKE '%SAXAGLIPTIN%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'SORAFENIB' , 'SORAfenib') WHERE `name` LIKE '%SORAFENIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'SUNITINIB' , 'SUNItinib') WHERE `name` LIKE '%SUNITINIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'VANDETANIB' , 'vanDETanib') WHERE `name` LIKE '%VANDETANIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'VEMURAFENIB' , 'vemURAFenib') WHERE `name` LIKE '%VEMURAFENIB%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'VINBLASTINE' , 'vinBLAStine') WHERE `name` LIKE '%VINBLASTINE%' ;");
        arrList.add("UPDATE `cd_drug_search` SET `name` = REPLACE(`name`, 'VINCRISTINE' , 'vinCRIStine') WHERE `name` LIKE '%VINCRISTINE%' ;");
        return arrList;
    }

    public List cleanDecimals() { 
	//normalise drug dosing per IPAL 
        List<String> arrList = new ArrayList();
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, ' .' , ' 0.') WHERE `name` LIKE '% .%';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, '.0 ' , ' ') WHERE `name` LIKE '%.0 %';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, '.00 ' , ' ') WHERE `name` LIKE '%.0 %';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, '.0MG' , 'MG') WHERE `name` LIKE '%.0MG%';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, 'µG' , 'MCG') WHERE `name` LIKE '%µG%';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, '.0MCG' , 'MCG') WHERE `name` LIKE '%.0MCG%';");
        return arrList;
    }

    public List addCategories() { 
	//group ATC's into broad catagories for allergy checking 
        List<String> arrList = new ArrayList();
        arrList.add("UPDATE cd_therapeutic_class INNER JOIN utility ON utility.tc_atc_number = cd_therapeutic_class.tc_atc_number SET cd_therapeutic_class.tc_ahfs_number = utility.tc_ahfs_number, cd_therapeutic_class.tc_ahfs = utility.tc_ahfs;");
        return arrList;
    }

    public void setISMPmeds() {
    	// apply ISMP Canada medication safety rules to search medications
        EntityManager entityManager = JpaUtils.createEntityManager();
        try {
            EntityTransaction tx = entityManager.getTransaction();
 
            try{
                tx.begin();
            }
            catch(org.apache.openjpa.persistence.InvalidStateException ise){
                ise.printStackTrace();
            }
            catch(java.lang.IllegalStateException ee){
                ee.printStackTrace();
            }
            catch(Exception e){
                e.printStackTrace();
            }

            insertLines(entityManager, addTALLman());//rename search drug to use Canadian TALLman list
            insertLines(entityManager, cleanDecimals());//normalise drug dosing
            insertLines(entityManager, addCategories());//add broad categories
            
            tx.commit();
 
        } finally {

            JpaUtils.close(entityManager);

        }
        return;
    }

    public List dropSearchTables() {
        List<String> arrList = new ArrayList();
        String[] tableNames = {"cd_drug_search", "link_generic_brand"};
        for (String tableName : tableNames) {
            if (isTablePresent(tableName)) {
                String statement = "DROP TABLE " + tableName;
                arrList.add(statement);
            } else {
            }
        }
        return arrList;
    }

    public List getCreateSearchTables() {
        List<String> arrList = new ArrayList();

        arrList.add("CREATE TABLE  cd_drug_search  (id serial  PRIMARY KEY,   drug_code  varchar(30),   category   int,   name  text default NULL);");
        arrList.add("CREATE TABLE  link_generic_brand (pk_id serial  PRIMARY KEY,   id integer,    drug_code varchar(30));");
        return arrList;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {


        /*DPDImport imp = new DPDImport();
        long timeTaken = imp.doItDifferent();  // executeOn(entities);
        System.out.println("GOING OUT after " + timeTaken);*/
        //DPDImport imp = new DPDImport();
        //imp.addStrengthToBrandName();
       // imp.addDescriptorToSearchName();
    }

    private void insertLines(EntityManager entityManager, List<String> sqlLines) {

        for (String sql : sqlLines) {
            p("sql", sql);
            logger.debug(sql);
            Query query = entityManager.createNativeQuery(sql);
            try {
                query.executeUpdate();
            } catch (Exception e) { //org.postgresql.util.PSQLException
                //String getMsg = e.getMessage();
                // System.out.println("ERROR :"+getMsg);
                e.printStackTrace();
            }
        }
    }

    public void p(String str, String s) {
        System.out.println(str + "=" + s);
    }

    public void p(String str) {
        System.out.println(str);
    }
    public List<Integer> addDescriptorToSearchName(){
        //select all search drugs for each row check if there is a descriptor according to drugcode, check if descriptor is contained in the search name.
        //append descriptor.
        EntityManager em=JpaUtils.createEntityManager();
        EntityTransaction tx=em.getTransaction();
        tx.begin();
        String q="select cds from CdDrugSearch cds where cds.category=13";
        Query qy=em.createQuery(q);
        List<CdDrugSearch> r=qy.getResultList();
        String drugcode,drugName,descriptor;
        StringBuffer newName=new StringBuffer();
        int count=0;
        List<Integer> changedDrugName=new ArrayList<Integer>();
        for(CdDrugSearch cds:r){
            drugcode=cds.getDrugCode();
            if(isNumber(drugcode)){
                drugName=cds.getName();
                q="select cdp from CdDrugProduct cdp where cdp.drugCode=(:drugCode)";
                qy=em.createQuery(q);
                qy.setParameter("drugCode", Integer.parseInt(drugcode));
                List<CdDrugProduct> p=qy.getResultList();
                for(CdDrugProduct cdp: p){
                    descriptor=cdp.getDescriptor();
                    if(descriptor!=null){
                        descriptor=descriptor.trim();
                        if(descriptor.length()>0&&!drugName.contains(descriptor)){
                            //update cd drug search row
                            newName.append(drugName).append(" ").append(descriptor);
                            //System.out.println("**new name of drug search="+newName.toString()+"--drugCode="+cds.getDrugCode());
                            changedDrugName.add(cds.getId());
                            qy=em.createQuery("update CdDrugSearch cds set cds.name=(:name) where cds.id=(:id)");
                            qy.setParameter("name", newName.toString());
                            qy.setParameter("id", cds.getId());
                            qy.executeUpdate();
                            count++;
                            em.flush();
                        }
                    }
                    newName.setLength(0);//reuse newName
                }
            }
        }

        System.out.println("number of new name with descriptor is "+count);
            em.clear();
            tx.commit();
            JpaUtils.close(em);

            return changedDrugName;

    }
    private boolean isNumber(String s){
        Pattern p=Pattern.compile("^\\n*[0-9]+\\n*$");
        Matcher m=p.matcher(s);
        if(m.matches()){
            return true;
        }else return false;
    }
     public List<Integer> addStrengthToBrandName(){
        EntityManager em=JpaUtils.createEntityManager();
        EntityTransaction tx=em.getTransaction();
        tx.begin();
        String q="select cds from CdDrugSearch cds where cds.category<>18 and cds.category<>19 and  cds.name not like '%0%' and cds.name not like '%1%' "
                + "and cds.name not like '%2%' and cds.name not like '%3%' and cds.name not like '%4%' and cds.name not like '%5%' and cds.name not like '%6%'"
                + " and cds.name not like '%7%' and cds.name not like '%8%' and cds.name not like '%9%'";
        Query qy=em.createQuery(q);
        List<CdDrugSearch> r=qy.getResultList();
        String drugcode,brandname;
        StringBuffer sb;
        String q2,q3;
         List<CdActiveIngredients> r2;
         List<Integer> changedDrugName=new ArrayList<Integer>();
       try{
           for(CdDrugSearch cds:r){
                 drugcode=cds.getDrugCode();
                 if(isNumber(drugcode)){
                      brandname=cds.getName();
                      q2="select cai from CdActiveIngredients cai where cai.drugCode=(:drugcode)";
                      qy=em.createQuery(q2);
                      qy.setParameter("drugcode", Integer.parseInt(drugcode));
                      r2=qy.getResultList();
                      sb=new StringBuffer();
                      for(CdActiveIngredients cai:r2){
                          if(brandname.contains(cai.getStrength())){
                            //do nothing if brandname already contain strength
                          }else{
                                  if(sb.length()==0){
                                      sb.append(" ");
                                  }else{
                                      sb.append("/");
                                  }
                                    //check if it's already in the name, if it is, don't need to add
                                    sb.append(cai.getStrength()).append(cai.getStrengthUnit());
                          }
                      }
                      if(sb.length()>0){
                        brandname+=sb.toString();
                        /*if(brandname.contains("'")){
                            brandname=brandname.replace("'", "\\'");
                        }*/

                        //System.out.println("** new name after adding strength="+brandname+"--drugcode="+cds.getDrugCode());
                        changedDrugName.add(cds.getId());

                        q3="update CdDrugSearch cds set cds.name=(:bn) where cds.id=(:cdsid)";
                        Query qy3=em.createQuery(q3);
                        qy3.setParameter("bn", brandname);
                        qy3.setParameter("cdsid", cds.getId());
                        //System.out.println("q3="+q3);
                        qy3.executeUpdate();
                        em.flush();
                      }

                    em.clear();
                 }else{;}
           }
        }catch (Exception e) {
            e.printStackTrace();
        } finally {
            tx.commit();
            JpaUtils.close(em);

        }
        System.out.println("number of drug names added strength="+changedDrugName.size());
        return changedDrugName;
    }

    private void createDrugRecords(File drugFile, EntityManager em)
            throws Exception {
        // Parse into Zip File
        ZipFile drugZip = null;

        // Enumerate entries contained within Zip
        try {
        	drugZip = new ZipFile(drugFile);
            Enumeration<? extends ZipEntry> drugEntries = drugZip.entries();
            while (drugEntries.hasMoreElements()) {
                // Get entry and stream
                ZipEntry entry = drugEntries.nextElement();
               
                if(entry.getName().endsWith(".zip")) {
	                //copy the zip to file, and then open it up as a ZipFile
	                File f = File.createTempFile(entry.getName(), "-item.zip");
	                FileOutputStream fos = null;
	                try {
	                	fos = new FileOutputStream(f);
	                	IOUtils.copy(drugZip.getInputStream(entry),fos);
	                } catch(IOException e) {
	                	System.err.println("Failed to write to file " + f);
	                	continue;
	                } finally {
	                	IOUtils.closeQuietly(fos);
	                }
	                
	                ZipFile entryZip = null;
	                
	                try {
	                	entryZip = new ZipFile(f);
	                
		                Enumeration<? extends ZipEntry> entryZipEntries = entryZip.entries();
		                while (entryZipEntries.hasMoreElements()) {
		                	ZipEntry innerEntry = entryZipEntries.nextElement();
		                	
		                	// Parse the entry
		                    if (!innerEntry.isDirectory()) {
		                    	RecordParser.getDPDObject(innerEntry.getName(), entryZip.getInputStream(innerEntry), em);
		                    }
		                    
		                }
	                } finally {
	                	if(entryZip != null) {
	                		entryZip.close();
	                	}
	                }
                } else {
                	 if (!entry.isDirectory()) {
                         InputStream is = null;
                         try {
	                         is = drugZip.getInputStream(entry);
	                         RecordParser.getDPDObject(entry.getName(), is, em);
                         }catch(Exception e) {
                        	 MiscUtils.getLogger().error("Error",e);
                         } finally {
                        	 IOUtils.closeQuietly(is);
                         }
                     }
                }
                
            }
        } finally {
        	if(drugZip != null) {
        		drugZip.close();
        	}
        }

        // Remove temporary file
        if(!drugFile.delete()) {
            System.err.println("Could not delete temporary stream file");
        }
    }

    public long doItDifferent() {
        long startTime = System.currentTimeMillis();
        EntityManager entityManager = JpaUtils.createEntityManager();
        try {
            EntityTransaction tx = entityManager.getTransaction();
            tx.begin();

            //drop tables only if they exist
            if (!getDPDTablesDrop().isEmpty()) {//if some tables are present
                p("tables exist");
                insertLines(entityManager, getDPDTablesDrop());//drop tables here
            }
            insertLines(entityManager, getDPDTables());//create table here

            //create table if history is not present, do nothing if present.
            if(!isTablePresent("history")){
                insertLines(entityManager, getHistoryTable());
            }
            tx.commit();
            //p("%%3",""+tx.isActive());

            try {
                createDrugRecords(getZipStream(), entityManager);
                createDrugRecords(getInactiveZipStream(), entityManager);
                createDrugRecords(getInactiveTableZipStream(), entityManager);

                p("populate interactions table with data");

                // Stream to read file
                String url="/interactions-holbrook.txt";
                InputStream ins=this.getClass().getResourceAsStream(url);
                if (ins==null) System.out.println("ins is null");
                RecordParser.getDPDObject("interactions-holbrook.txt", ins, entityManager);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try{
                tx.begin();
            }
            catch(org.apache.openjpa.persistence.InvalidStateException ise){
                ise.printStackTrace();
            }
            catch(java.lang.IllegalStateException ee){
                ee.printStackTrace();
            }
            catch(Exception e){
                e.printStackTrace();
            }

            //drop tables only if they exist
            if (!dropSearchTables().isEmpty()) {
                insertLines(entityManager, dropSearchTables());
            }


            insertLines(entityManager, getCreateSearchTables());

            tx.commit();
            //add indexes to tables.
            tx.begin();
            insertLines(entityManager, addIndexToTables());
            tx.commit();
            //import search data
            ConfigureSearchData searchData = new ConfigureSearchData();
            long beforeSD=System.currentTimeMillis();
            System.out.println("=============time spent before importing search data="+(beforeSD-startTime));
            searchData.importSearchData(entityManager);

            tx.begin();
            insertLines(entityManager, addIndexToSearchTable());
            tx.commit();
  
        } finally {

            JpaUtils.close(entityManager);

        }
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }
    public HashMap<String,Long> numberTableRows(){
        EntityManager em=JpaUtils.createEntityManager();
        HashMap<String,Long> hm=new HashMap<String,Long>();
        List<String> tableNames=new ArrayList();
        tableNames.add("CdActiveIngredients");
        tableNames.add("CdCompanies");
        tableNames.add("CdDrugProduct");
        tableNames.add("CdDrugSearch");
        tableNames.add("CdDrugStatus");
        tableNames.add("CdForm");
        tableNames.add("CdInactiveProducts");
        tableNames.add("CdPackaging");
        tableNames.add("CdPharmaceuticalStd");
        tableNames.add("CdRoute");
        tableNames.add("CdSchedule");
        tableNames.add("CdTherapeuticClass");
        tableNames.add("CdVeterinarySpecies");
        tableNames.add("Interactions");
        tableNames.add("LinkGenericBrand");
        for(String s:numberRowsQuery()){
            Query sql=em.createQuery(s);
            String tablename="";
            for(String ss:tableNames){
                if(s.contains(ss))
                    tablename=ss;
            }
            List<Long> l = sql.getResultList();
            Long n=0L;
            for(Long i:l){
                n=i;
            }
            hm.put(tablename, n);
        }
        JpaUtils.close(em);
        return hm;
    }

    private List<String> numberRowsQuery(){
        List<String> retList=new ArrayList();
        retList.add("select count(t) from CdActiveIngredients t");
        retList.add("select count(t) from CdCompanies t");
        retList.add("select count(t) from CdDrugProduct t");
        retList.add("select count(t) from CdDrugSearch t");
        retList.add("select count(t) from CdDrugStatus t");
        retList.add("select count(t) from CdForm t");
        retList.add("select count(t) from CdInactiveProducts t");
        retList.add("select count(t) from CdPackaging t");
        retList.add("select count(t) from CdPharmaceuticalStd t");
        retList.add("select count(t) from CdRoute t");
        retList.add("select count(t) from CdSchedule t");
        retList.add("select count(t) from CdTherapeuticClass t");
        retList.add("select count(t) from CdVeterinarySpecies t");
        retList.add("select count(t) from Interactions t");
        retList.add("select count(t) from LinkGenericBrand t");
        return retList;
    }
}
