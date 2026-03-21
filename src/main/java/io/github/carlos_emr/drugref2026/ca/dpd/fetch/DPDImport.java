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
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.drugref2026.ca.dpd.CdActiveIngredients;
import io.github.carlos_emr.drugref2026.ca.dpd.CdDrugProduct;
import io.github.carlos_emr.drugref2026.ca.dpd.CdDrugSearch;
import io.github.carlos_emr.drugref2026.util.DrugrefProperties;
import io.github.carlos_emr.drugref2026.util.JpaUtils;
import io.github.carlos_emr.drugref2026.util.MiscUtils;

/**
 * Downloads and imports the Canadian Drug Product Database (DPD) from Health Canada's website.
 *
 * <p>This class orchestrates the full DPD import pipeline:</p>
 * <ol>
 *   <li><b>Download:</b> Fetches ZIP files from Health Canada containing CSV data for all DPD tables:
 *       {@code allfiles.zip} (active products), {@code Allfiles_ia-Oct10.zip} (inactive product details),
 *       and {@code inactive.zip} (inactive products table).</li>
 *   <li><b>Drop and recreate:</b> Drops existing DPD tables and recreates them with proper schema,
 *       including the {@code history} table if not already present.</li>
 *   <li><b>Parse and persist:</b> Extracts each CSV file from the ZIP archives and delegates to
 *       {@link RecordParser#getDPDObject(String, java.io.InputStream, jakarta.persistence.EntityManager)}
 *       to parse CSV records into JPA entities and persist them.</li>
 *   <li><b>Import interactions:</b> Loads the bundled {@code interactions-holbrook.txt} file
 *       containing Holbrook evidence-based drug-drug interaction data.</li>
 *   <li><b>Build search index:</b> Drops and recreates the {@code cd_drug_search} and
 *       {@code link_generic_brand} tables, then delegates to {@link ConfigureSearchData} to
 *       build the searchable drug name index from the raw DPD data.</li>
 *   <li><b>Create indexes:</b> Adds database indexes on drug_code columns and search table columns
 *       for query performance.</li>
 *   <li><b>Post-processing:</b> Applies ISMP TALLman lettering, decimal normalization, and
 *       AHFS category enrichment via {@link #setISMPmeds()} and related methods. Adds
 *       strength and descriptor information to brand name search entries.</li>
 * </ol>
 *
 * <p>The main entry point is {@link #doItDifferent()}, which executes the full pipeline
 * and returns the elapsed time in milliseconds.</p>
 *
 * @author jaygallagher
 */
public class DPDImport {


    private static Logger logger = MiscUtils.getLogger();
    String dpd_url = DrugrefProperties.getInstance().getProperty("DPD_BASE_URL", "https://www.canada.ca/content/dam/hc-sc/documents/services/drug-product-database");
    /**
     * Downloads the main DPD ZIP file ({@code allfiles.zip}) containing all active drug product
     * CSV data files from Health Canada's website.
     *
     * @return a temporary File containing the downloaded ZIP archive
     * @throws Exception if the download or file creation fails
     */
    public File getZipStream() throws Exception {
        String sUrl = dpd_url + "/allfiles.zip";
        return getZipStream(sUrl);
    }

    /**
     * Downloads the inactive products detail ZIP file ({@code Allfiles_ia-Oct10.zip}) from
     * Health Canada. This contains supplementary CSV data for inactive/discontinued products.
     *
     * <p>Note: This file is a legacy format from 2018. The code should be updated to handle
     * the newer {@code Allfiles_ia.zip} format.</p>
     *
     * @return a temporary File containing the downloaded ZIP archive
     * @throws Exception if the download or file creation fails
     */
    public File getInactiveZipStream() throws Exception {
            String sUrl = dpd_url + "/Allfiles_ia-Oct10.zip";
            // WARNING Allfiles_ia-Oct10.zip is data from 2018, code should be updated to handle new format in Allfiles_ia.zip
                    return getZipStream(sUrl);
    }

    /**
     * Downloads the inactive products summary ZIP file ({@code inactive.zip}) containing the
     * {@code inactive.txt} CSV file with basic discontinued product information (DIN, brand name,
     * cancellation date).
     *
     * @return a temporary File containing the downloaded ZIP archive
     * @throws Exception if the download or file creation fails
     */
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

    /**
     * Generates DROP TABLE statements for all DPD tables that currently exist in the database.
     * Only generates statements for tables that are actually present, to avoid errors.
     *
     * @return a list of SQL DROP TABLE statements
     */
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

    /**
     * Generates CREATE TABLE statements for all DPD tables plus the interactions and utility tables.
     * The utility table is only created if it does not already exist.
     *
     * @return a list of SQL CREATE TABLE statements
     */
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
    /**
     * Generates SQL statements to create indexes on all DPD tables (primarily on drug_code columns)
     * and performs a post-import UPDATE to populate the company_code column in cd_drug_product
     * by joining with cd_companies.
     *
     * @return a list of SQL CREATE INDEX and UPDATE statements
     */
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

    /**
     * Generates SQL statements to create indexes on the cd_drug_search table columns
     * (id, drug_code, category, name) for search query performance.
     *
     * @return a list of SQL CREATE INDEX statements
     */
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

    /**
     * Generates SQL UPDATE statements to apply ISMP Canada TALLman lettering to drug names
     * in the search table. TALLman lettering uses mixed case to highlight the distinguishing
     * parts of look-alike drug name pairs (e.g., "DOBUTamine" vs "DOPamine") to reduce
     * medication errors.
     *
     * @return a list of SQL UPDATE statements for TALLman name replacements
     */
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

    /**
     * Generates SQL UPDATE statements to normalize decimal formatting in drug dosage strings.
     * Cleans up leading decimal points (e.g., " .5" to " 0.5"), removes trailing ".0" and ".00",
     * and converts the micro sign ("µG") to "MCG" for consistency.
     *
     * @return a list of SQL UPDATE statements for decimal normalization
     */
    public List cleanDecimals() {
        List<String> arrList = new ArrayList();
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, ' .' , ' 0.') WHERE `name` LIKE '% .%';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, '.0 ' , ' ') WHERE `name` LIKE '%.0 %';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, '.00 ' , ' ') WHERE `name` LIKE '%.0 %';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, '.0MG' , 'MG') WHERE `name` LIKE '%.0MG%';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, 'µG' , 'MCG') WHERE `name` LIKE '%µG%';");
        arrList.add("UPDATE cd_drug_search SET `name` = REPLACE(`name`, '.0MCG' , 'MCG') WHERE `name` LIKE '%.0MCG%';");
        return arrList;
    }

    /**
     * Generates SQL UPDATE statements to enrich the therapeutic class table with AHFS category
     * codes from the utility table. This groups ATC codes into broader AHFS categories for
     * allergy cross-checking.
     *
     * @return a list of SQL UPDATE statements
     */
    public List addCategories() {
        List<String> arrList = new ArrayList();
        arrList.add("UPDATE cd_therapeutic_class INNER JOIN utility ON utility.tc_atc_number = cd_therapeutic_class.tc_atc_number SET cd_therapeutic_class.tc_ahfs_number = utility.tc_ahfs_number, cd_therapeutic_class.tc_ahfs = utility.tc_ahfs;");
        return arrList;
    }

    /**
     * Applies ISMP (Institute for Safe Medication Practices) Canada medication safety rules
     * to the search drug names. Executes TALLman lettering, decimal normalization, and
     * broad category assignment in sequence.
     */
    public void setISMPmeds() {
        EntityManager entityManager = JpaUtils.createEntityManager();
        try {
            EntityTransaction tx = entityManager.getTransaction();
 
            try{
                tx.begin();
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

    /**
     * Generates DROP TABLE statements for the search index tables (cd_drug_search and
     * link_generic_brand) if they exist.
     *
     * @return a list of SQL DROP TABLE statements
     */
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

    /**
     * Generates CREATE TABLE statements for the search index tables: {@code cd_drug_search}
     * (the main drug search index) and {@code link_generic_brand} (generic-to-brand mapping).
     *
     * @return a list of SQL CREATE TABLE statements
     */
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
    /**
     * Appends product descriptors to brand name search entries (category 13) where the
     * descriptor is not already present in the name.
     *
     * <p>For each brand name in the search table, looks up the corresponding drug product's
     * descriptor field. If the descriptor is non-empty and not already contained in the
     * search name, it is appended (e.g., "TYLENOL" becomes "TYLENOL EXTRA STRENGTH").</p>
     *
     * @return a list of search entry IDs that were modified
     */
    public List<Integer> addDescriptorToSearchName(){
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
    /**
     * Appends active ingredient strength information to brand name search entries that do
     * not already contain numeric strength values in their name.
     *
     * <p>Finds all search entries (excluding categories 18/19) whose names contain no digits,
     * then looks up their active ingredients and appends the strength+unit (e.g., "500MG")
     * or multiple strengths separated by "/" for combination products.</p>
     *
     * @return a list of search entry IDs that were modified
     */
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

    /**
     * Extracts CSV files from a downloaded DPD ZIP archive and parses each into JPA entities.
     *
     * <p>Handles nested ZIP files (ZIP-within-ZIP structure used by Health Canada) by extracting
     * inner ZIPs to temporary files and processing their contents. Each CSV file is identified
     * by its filename (e.g., "drug.txt", "ingred.txt") and dispatched to
     * {@link RecordParser#getDPDObject} for parsing and persistence.</p>
     *
     * @param drugFile the downloaded ZIP file to process
     * @param em the EntityManager for persisting parsed records
     * @throws Exception if file extraction or parsing fails
     */
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

    /**
     * Executes the complete DPD import pipeline: download, drop/create tables, parse CSVs,
     * import interactions, build search index, create indexes, and apply post-processing.
     *
     * <p>This is the main entry point for a full database refresh. The pipeline steps are:</p>
     * <ol>
     *   <li>Drop existing DPD tables and recreate with fresh schema</li>
     *   <li>Download and parse active products ({@code allfiles.zip})</li>
     *   <li>Download and parse inactive product details ({@code Allfiles_ia-Oct10.zip})</li>
     *   <li>Download and parse inactive products summary ({@code inactive.zip})</li>
     *   <li>Load Holbrook drug-drug interaction data from bundled resource</li>
     *   <li>Drop and recreate search index tables</li>
     *   <li>Add database indexes to all DPD tables</li>
     *   <li>Build search index via {@link ConfigureSearchData}</li>
     *   <li>Add indexes to search table</li>
     * </ol>
     *
     * @return elapsed time in milliseconds for the entire import
     */
    public long doItDifferent() {
        long startTime = System.currentTimeMillis();
        EntityManager entityManager = JpaUtils.createEntityManager();
        try {
            EntityTransaction tx = entityManager.getTransaction();
            tx.begin();

            // Step 1: Drop existing DPD tables and recreate with fresh schema
            if (!getDPDTablesDrop().isEmpty()) {
                p("tables exist");
                insertLines(entityManager, getDPDTablesDrop());
            }
            insertLines(entityManager, getDPDTables());

            // Ensure the history table exists (created once, never dropped)
            if(!isTablePresent("history")){
                insertLines(entityManager, getHistoryTable());
            }
            tx.commit();

            try {
                // Step 2: Download and parse active products from Health Canada
                createDrugRecords(getZipStream(), entityManager);
                // Step 3: Download and parse inactive product details
                createDrugRecords(getInactiveZipStream(), entityManager);
                // Step 4: Download and parse inactive products summary
                createDrugRecords(getInactiveTableZipStream(), entityManager);

                // Step 5: Load Holbrook drug-drug interaction data from bundled resource
                p("populate interactions table with data");
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
            catch(java.lang.IllegalStateException ee){
                ee.printStackTrace();
            }
            catch(Exception e){
                e.printStackTrace();
            }

            // Step 6: Drop and recreate search index tables (cd_drug_search, link_generic_brand)
            if (!dropSearchTables().isEmpty()) {
                insertLines(entityManager, dropSearchTables());
            }
            insertLines(entityManager, getCreateSearchTables());
            tx.commit();

            // Step 7: Add database indexes to all DPD tables for query performance
            tx.begin();
            insertLines(entityManager, addIndexToTables());
            tx.commit();

            // Step 8: Build the search index from raw DPD data (brand names, generics, ATC, ingredients)
            ConfigureSearchData searchData = new ConfigureSearchData();
            long beforeSD=System.currentTimeMillis();
            System.out.println("=============time spent before importing search data="+(beforeSD-startTime));
            searchData.importSearchData(entityManager);

            // Step 9: Add indexes to the search table for search query performance
            tx.begin();
            insertLines(entityManager, addIndexToSearchTable());
            tx.commit();
  
        } finally {

            JpaUtils.close(entityManager);

        }
        long endTime = System.currentTimeMillis();
        return endTime - startTime;
    }
    /**
     * Counts the number of rows in each DPD table and returns the counts as a map.
     * Useful for verifying a successful import by checking that tables are populated.
     *
     * @return a HashMap mapping entity class names to their row counts
     */
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
