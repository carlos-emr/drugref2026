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

import java.io.InputStream;
import org.drugref.ca.dpd.CdVeterinarySpecies;
import com.Ostermiller.util.CSVParser;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import org.drugref.ca.dpd.CdActiveIngredients;
import org.drugref.ca.dpd.CdCompanies;
import org.drugref.ca.dpd.CdDrugProduct;
import org.drugref.ca.dpd.CdDrugStatus;
import org.drugref.ca.dpd.CdForm;
import org.drugref.ca.dpd.CdInactiveProducts;
import org.drugref.ca.dpd.CdPackaging;
import org.drugref.ca.dpd.CdPharmaceuticalStd;
import org.drugref.ca.dpd.CdRoute;
import org.drugref.ca.dpd.CdSchedule;
import org.drugref.ca.dpd.CdTherapeuticClass;
import org.drugref.ca.dpd.Interactions;
import org.drugref.util.DrugrefProperties;

/**
 *
 * @author jaygallagher
 */
public class RecordParser {

    static public String memString() {
        // Get current size of heap in bytes
        long heapSize = Runtime.getRuntime().totalMemory();

        // Get maximum size of heap in bytes. The heap cannot grow beyond this size.
        // Any attempt will result in an OutOfMemoryException.
        long heapMaxSize = Runtime.getRuntime().maxMemory();

        // Get amount of free memory within the heap in bytes. This size will increase
        // after garbage collection and decrease as new objects are created.
        long heapFreeSize = Runtime.getRuntime().freeMemory();
        String ret = heapSize + "/" + heapMaxSize + " :" + heapFreeSize;
        return ret;
    }

    static public String looksLike(String[] s) {
        StringBuffer sb = new StringBuffer();
        if (s == null) {
            return "";
        }
        int count = 0;
        for (String str : s) {
            sb.append(count + ".");
            sb.append(str + "-- ");
            count++;
        }
        sb.append(memString());
        return sb.toString();
//        return "";
    }

    static public Date getDate(String s) throws Exception {
    // drug.txt "10000","","Veterinary","00813761","LINCOMIX","","N","69580","1","03-DEC-2018","0105826008","Vétérinaire","",""
    // dd-MMM-yy format will correctly handle both 03-DEC-2018 and 03-DEC-18
        DateFormat formatter = new SimpleDateFormat("dd-MMM-yy");
        Date date = (Date) formatter.parse(s);
        return date;
    }

    public static void p(String str, String s) {
        System.out.println(str + "=" + s);
    }

    public static void p(String str) {
        System.out.println(str);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
    public static Object getDPDObject(String type, InputStream is, EntityManager em) throws Exception {
        p("TYPE",type);
        InputStreamReader isr = new InputStreamReader(is, "UTF-8");
        Reader in = new BufferedReader(isr);
        CSVParser csv = new CSVParser(is);
        String[] items = null;
        EntityTransaction tx = em.getTransaction();
        tx.begin();
        long start=System.currentTimeMillis();
        //System.out.println(csv.getLine());
       //System.out.println(csv.getLine().length);
        //if(csv.getLine().length>0)
          //  System.out.println(csv.getLine()[0]);
        
        if ("vet.txt".equals(type) || "vet_ia.txt".equals(type)) {

            while ((items = csv.getLine()) != null) {
                //System.out.println(looksLike(items));
            	items[0] = items[0].replaceAll("\\p{C}", "");     
            	if(items[0].startsWith("\"")) {
            		items[0] = items[0].replaceAll("\"", "");
            	}
            	
                CdVeterinarySpecies vet = new CdVeterinarySpecies();

                vet.setDrugCode(new Integer(items[0]));
                vet.setVetSpecies(items[1]);
                vet.setVetSubSpecies(items[2]);
                em.persist(vet);
                //em.flush();
                //em.clear();
                vet = null;
            }
        } else if ("comp.txt".equals(type) || "comp_ia.txt".equals(type)) {
            //change encoding from ISO-8859-1 to UTF-8
            String str = "";
            try {
                StringBuffer buf = new StringBuffer();
                int ch;
                while ((ch = in.read()) > -1) {
                    buf.append((char) ch);
                }
                //in.close();
                str = buf.toString();
                //   p("** ",str);
            } catch (Exception e) {
                e.printStackTrace();
            }

            InputStream ins = new ByteArrayInputStream(str.getBytes("UTF-8"));
            CSVParser csv2 = new CSVParser(ins);
            int count=0;
            while ((items = csv2.getLine()) != null) {
            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
            	
            	//System.out.println("reading comps,line number="+count);
                //System.out.println(looksLike(items));
                CdCompanies vet = new CdCompanies();
                /*
                DRUG_CODE                              NOT NULL  NUMBER(8)
                MFR_CODE                                         VARCHAR2(5)
                COMPANY_CODE                                     NUMBER(6)
                COMPANY_NAME                                     VARCHAR2(80)
                COMPANY_TYPE                                     VARCHAR2(40)
                ADDRESS_MAILING_FLAG				  VARCHAR2(1)
                ADDRESS_BILLING_FLAG				  VARCHAR2(1)
                ADDRESS_NOTIFICATION_FLAG			  VARCHAR2(1)
                ADDRESS_OTHER					  VARCHAR2(1)
                SUITE_NUMBER                                     VARCHAR2(20)
                STREET_NAME                                      VARCHAR2(80)
                CITY_NAME                                        VARCHAR2(60)
                PROVINCE                                         VARCHAR2(40)
                COUNTRY                                          VARCHAR2(40)
                POSTAL_CODE                                      VARCHAR2(20)
                POST_OFFICE_BOX                                  VARCHAR2(15)
                 */
                //loop to set character encoding


                vet.setDrugCode(new Integer(items[0]));
                vet.setMfrCode(items[1]);
                vet.setCompanyCode(new Integer(items[2]));
                vet.setCompanyName(items[3]);
                vet.setCompanyType(items[4]);
                vet.setAddressMailingFlag(items[5]);//.charAt(0));
                vet.setAddressBillingFlag(items[6]);//.charAt(0));
                vet.setAddressNotificationFlag(items[7]);//.charAt(0));
                vet.setAddressOther(items[8]);
                vet.setSuiteNumber(items[9]);
                vet.setStreetName(items[10]);
                vet.setCityName(items[11]);
                vet.setProvince(items[12]);
                vet.setCountry(items[13]);
                vet.setPostalCode(items[14]);
                vet.setPostOfficeBox(items[15]);

                //  System.out.println("addrBillingFlag>"+vet.getAddressBillingFlag()+"<");

                em.persist(vet);
                //em.flush();
                //em.clear();
                vet = null;
                count++;
            }
        } else if ("drug.txt".equals(type) || "drug_ia.txt".equals(type)) {
            //change encoding from ISO-8859-1 to UTF-8
            String str = "";
            try {
                StringBuffer buf = new StringBuffer();
                int ch;
                while ((ch = in.read()) > -1) {
                    buf.append((char) ch);
                }
                //in.close();
                str = buf.toString();
                //   p("** ",str);
            } catch (Exception e) {
                e.printStackTrace();
            }
            InputStream ins = new ByteArrayInputStream(str.getBytes("UTF-8"));
            CSVParser csv2 = new CSVParser(ins);
            DrugrefProperties dp=DrugrefProperties.getInstance();
            while ((items = csv2.getLine()) != null) {
                //System.out.println(looksLike(items));
                CdDrugProduct prod = new CdDrugProduct();

                String str2 = items[4];
              //  if (str2.startsWith("LAIT SOLAIRE PROTECTION M")) {
                    //p("#################################### insert before", str2);
              //  }
                if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
                prod.setDrugCode(new Integer(items[0]));
                prod.setProductCategorization(items[1]);
                prod.setClass1(items[2]);
                prod.setCompanyCode(0);
                prod.setDrugIdentificationNumber(items[3]);
                prod.setBrandName(items[4]);
                prod.setDescriptor(items[5]);
                prod.setPediatricFlag(items[6]);//.charAt(0));
                prod.setAccessionNumber(items[7]);
                prod.setNumberOfAis(items[8]);
                prod.setLastUpdateDate(getDate(items[9]));
                prod.setAiGroupNo(items[10]);
                
                String all_drug_classes = dp.getAllDrugClasses();    //getProperty("all_drug_classes");

                if("HUMAN".equalsIgnoreCase(prod.getClass1()) || ("YES".equalsIgnoreCase(all_drug_classes))  ){
                    //p("true if in cdp");
                    em.persist(prod);
                    //em.flush();
                    //em.clear();
                }
                prod = null;

            }
            //System.out.println("check the ENCODING now");
        } else if ("form.txt".equals(type) || "form_ia.txt".equals(type)) {
            /*
            DRUG_CODE                                      NOT NULL  NUMBER(8)
            PHARM_FORM_CODE                                NUMBER(7)
            PHARMACEUTICAL_FORM                            VARCHAR2(40)
             */
            while ((items = csv.getLine()) != null) {
            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
            	CdForm vet = new CdForm();
                vet.setDrugCode(new Integer(items[0]));
                vet.setPharmCdFormCode(new Integer((items[1])));
                vet.setPharmaceuticalCdForm(items[2]);
                em.persist(vet);
                //em.flush();
                //em.clear();
                vet = null;
            }
        } else if ( "ingred.txt".equals(type) ||"ingred_ia.txt".equals(type)) {
            //change encoding from ISO-8859-1 to UTF-8
            /*
            DRUG_CODE                                NOT NULL NUMBER(8)
            ACTIVE_INGREDIENT_CODE                   NUMBER(6)
            INGREDIENT                               VARCHAR2(240)
            INGREDIENT_SUPPLIED_IND                  VARCHAR2(1)
            STRENGTH                                 VARCHAR2(20)
            STRENGTH_UNIT                            VARCHAR2(40)
            STRENGTH_TYPE                            VARCHAR2(40)
            DOSAGE_VALUE                             VARCHAR2(20)
            BASE                                     VARCHAR2(1)
            DOSAGE_UNIT                              VARCHAR2(40)
            NOTES                                    VARCHAR2(2000)
             */
            String str = "";
            try {
                StringBuffer buf = new StringBuffer();
                int ch;
                while ((ch = in.read()) > -1) {
                    buf.append((char) ch);
                }
                //in.close();
                str = buf.toString();
                //   p("** ",str);
            } catch (Exception e) {
                e.printStackTrace();
            }
            InputStream ins = new ByteArrayInputStream(str.getBytes("UTF-8"));
            CSVParser csv2 = new CSVParser(ins);

            while ((items = csv2.getLine()) != null) {

            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
                //        System.out.println(looksLike(items));
                CdActiveIngredients vet = new CdActiveIngredients();
                vet.setDrugCode(new Integer(items[0]));
                vet.setActiveIngredientCode(new Integer((items[1])));
                vet.setIngredient(items[2]);
                vet.setIngredientSuppliedInd(items[3]);//.charAt(0));
                vet.setStrength(items[4]);
                vet.setStrengthUnit(items[5]);
                vet.setStrengthType(items[6]);
                vet.setDosageValue(items[7]);
                vet.setBase(items[8]);//.charAt(0));
                vet.setDosageUnit(items[9]);
                vet.setNotes(items[10]);
                em.persist(vet);
                //em.flush();
                //em.clear();
                vet = null;
            }
        } else if ("package.txt".equals(type) || "package_ia.txt".equals(type)) {
            /*
            DRUG_CODE                              NOT NULL  NUMBER(8)
            UPC                                              VARCHAR2(12)
            PACKAGE_SIZE_UNIT                                VARCHAR2(40)
            PACKAGE_TYPE                                     VARCHAR2(40)
            PACKAGE_SIZE                                     VARCHAR2(5)
            PRODUCT_INFORMATION                              VARCHAR2(80)
             */
            while ((items = csv.getLine()) != null) {
            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
                //    System.out.println(looksLike(items));
                CdPackaging vet = new CdPackaging();
                vet.setDrugCode(new Integer(items[0]));
                vet.setUpc(items[1]);
                vet.setPackageSizeUnit(items[2]);
                vet.setPackageType(items[3]);
                vet.setPackageSize(items[4]);
                vet.setProductInforation(items[5]);
                em.persist(vet);
                //em.flush();
                //em.clear();
            }
        } else if ("pharm.txt".equals(type) || "pharm_ia.txt".equals(type)) {
            /*
            DRUG_CODE                              NOT NULL  NUMBER(8)
            PHARMACEUTICAL_STD                               VARCHAR2(40)
             */
            while ((items = csv.getLine()) != null) {
                //      System.out.println(looksLike(items));
            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
                CdPharmaceuticalStd vet = new CdPharmaceuticalStd();
                vet.setDrugCode(new Integer(items[0]));
                vet.setPharmaceuticalStd(items[1]);
                em.persist(vet);
                //em.flush();
                //em.clear();
            }
        } else if ("route.txt".equals(type) || "route_ia.txt".equals(type)) {
            /*
            DRUG_CODE                              NOT NULL  NUMBER(8)
            ROUTE_OF_ADMINISTRATION_CODE		 	  NUMBER(6)
            ROUTE_OF_ADMINISTRATION                          VARCHAR2(40)
             */
            while ((items = csv.getLine()) != null) {
            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
                //System.out.println(looksLike(items));
                CdRoute vet = new CdRoute();
                vet.setDrugCode(new Integer(items[0]));
                vet.setRouteOfAdministrationCode(new Integer(items[1]));
                vet.setRouteOfAdministration(items[2]);
                em.persist(vet);
                //em.flush();
                //em.clear();
            }
               // tx.commit();
        } else if ("schedule.txt".equals(type) || "schedule_ia.txt".equals(type)) {
            /*
            DRUG_CODE                              NOT NULL  NUMBER(8)
            SCHEDULE                                         VARCHAR2(40)
             */
            while ((items = csv.getLine()) != null) {
            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
                //   System.out.println(looksLike(items));
                CdSchedule vet = new CdSchedule();
                vet.setDrugCode(new Integer(items[0]));
                vet.setSchedule(items[1]);
                em.persist(vet);
                //em.flush();
                //em.clear();
            }
        } else if ("status.txt".equals(type) || "status_ia.txt".equals(type)) {
            /*
            DRUG_CODE                              NOT NULL  NUMBER(8)
            CURRENT_STATUS_FLAG                              VARCHAR2(1)
            STATUS                                           VARCHAR2(40)
            HISTORY_DATE                                     DATE

             */
            while ((items = csv.getLine()) != null) {
            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
                //    System.out.println(looksLike(items));
                CdDrugStatus vet = new CdDrugStatus();
                vet.setDrugCode(new Integer(items[0]));
                vet.setCurrentStatusFlag(items[1]);//.charAt(0));
                vet.setStatus(items[2]);
                vet.setHistoryDate(getDate(items[3]));
                em.persist(vet);
                //em.flush();
                //em.clear();
            }
        } else if ("ther.txt".equals(type) || "ther_ia.txt".equals(type)) {
            /* as of July 2022
            DRUG_CODE 	NOT NULL 	NUMBER(8)
            TC_ATC_NUMBER 	NULLABLE 	VARCHAR2(8)
            TC_ATC 	NULLABLE 	VARCHAR2(120)
            TC_ATC_F 	NULLABLE 	VARCHAR2(240)           
             */
             //"778","C03AA03","HYDROCHLOROTHIAZIDE",""
            while ((items = csv.getLine()) != null) {
                //    System.out.println(looksLike(items));
            	if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
            		items[0] = items[0].substring(1).replaceAll("\"", "");
            	}
                CdTherapeuticClass vet = new CdTherapeuticClass();
                vet.setDrugCode(new Integer(items[0]));
                vet.setTcAtcNumber(items[1]);
                vet.setTcAtc(items[2]);
                vet.setTcAtcf(items[3]);
                em.persist(vet);
                //em.flush();
                //em.clear();
            }

        }else if ("inactive.txt".equals(type)) {
            
             while ((items = csv.getLine()) != null) {
                //    System.out.println(looksLike(items));
            	 if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
             		items[0] = items[0].substring(1).replaceAll("\"", "");
             	}
                CdInactiveProducts vet = new CdInactiveProducts();
                vet.setDrugCode(new Integer(items[0]));
                vet.setDrugIdentificationNumber(items[1]);
                vet.setBrandName(items[2]);
                vet.setHistoryDate(getDate(items[3]));
                em.persist(vet);
                //em.flush();
                //em.clear();
            }

        }else if("interactions-holbrook.txt".equals(type)){
           while ((items = csv.getLine()) != null) {
        	   if("EFBBBF".equals(bytesToHex(items[0].substring(0, 1).getBytes()))) {
           		items[0] = items[0].substring(1).replaceAll("\"", "");
           	}
                Interactions inter = new Interactions();
                inter.setId(Integer.parseInt(items[0]));
                inter.setAffectingatc(items[1]);
                inter.setAffectedatc(items[2]);
                inter.setEffect(items[3]);
                inter.setSignificance(items[4]);
                inter.setEvidence(items[5]);
                inter.setComment(items[6]);
                inter.setAffectingdrug(items[7]);
                inter.setAffecteddrug(items[8]);                
                em.persist(inter);
                //em.flush();
                //em.clear();
            }
        }
       tx.commit();
        long end=System.currentTimeMillis();
        System.out.println("========time spent on type "+type+" is "+(end-start));
        return null;
    }
}
