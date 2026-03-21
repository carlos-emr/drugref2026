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
package org.drugref.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

/**
 *
 * @author jackson
 */
public class DrugrefProperties extends Properties{
    private static Logger logger = MiscUtils.getLogger();
    private static final long serialVersionUID = -5965807410049845132L;
	private static final String DEFAULT_PROPERTIES = "/drugref.properties";
    private static DrugrefProperties drugrefProperties = new DrugrefProperties();
    private static boolean loaded = false;
    static{
            logger.debug("static initializer of drugrefproperties");
            readFromFile(DEFAULT_PROPERTIES,drugrefProperties);
    }
    

    private DrugrefProperties() {
		logger.debug("DRUGREF PROPS CONSTRUCTOR");
	}

    public static DrugrefProperties getInstance() {
    	return getInstance(DEFAULT_PROPERTIES);
    }
    	/**
	 * @return DrugrefProperties the instance of DrugrefProperties
	 */
	public static DrugrefProperties getInstance(String url) {
            Enumeration em=drugrefProperties.propertyNames();
            while(em.hasMoreElements()){
                String ss=(String)em.nextElement();
//                logger.debug("property="+ss);
//                logger.debug("value="+drugrefProperties.getProperty(ss));

        }

		return drugrefProperties;
	}
	
	
	
    private static void readFromFile(String url, Properties p) {
    	
		InputStream is = null;
	
		try {
			is = DrugrefProperties.class.getResourceAsStream(url);
			if (is == null) {
				is = new FileInputStream(url);
			}
			p.load(is);
			
		} catch (FileNotFoundException e1) {
			logger.error("file not found at:  " + url, e1);
		} catch (IOException e1) {
			logger.error("IO while retrieving: " + url, e1);
		} finally {
			try {
				if(is != null) {
					is.close();
				}
			} catch (IOException e) {
				logger.error("IO Error: ", e);
			}
		}

	}

	public void loader(InputStream propertyStream) {
		if (!loaded) {
			try {
				load(propertyStream);
				propertyStream.close();
				loaded = true;
			} catch (IOException ex) {
				logger.error("IO Error: " + ex.getMessage());
			}
		}
	}

	public void loader(String propFileName) throws java.io.FileNotFoundException {
		if (!loaded) {
			FileInputStream fis2 = new FileInputStream(propFileName);
			try {
				load(fis2);
				fis2.close();
				loaded = true;
			} catch (IOException ex) {
				logger.error("IO Error: " + ex.getMessage());
			}
		}
	}

        public String getDbUrl(){
            return getProperty("db_url");
        }
        public String getDbUser(){
            return getProperty("db_user");
        }
        public String getDbPassword(){
            return getProperty("db_password");
        }

        public String getAllDrugClasses(){
            return getProperty("all_drug_classes");
        }

        public boolean isMysql(){
            if(getDbUrl().contains("mysql"))
                return true;
            else
                return false;
        }
        public boolean isPostgres(){
            if(getDbUrl().contains("postgresql"))
                return true;
            else
                return false;
        }

}

