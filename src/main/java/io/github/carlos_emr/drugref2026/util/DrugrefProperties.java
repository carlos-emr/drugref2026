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
package io.github.carlos_emr.drugref2026.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.logging.log4j.Logger;

/**
 * Singleton holder for application configuration properties.
 *
 * <p>Extends {@link java.util.Properties} and provides centralized access to all
 * drugref2026 configuration values including database connection parameters
 * ({@code db_url}, {@code db_user}, {@code db_password}), license keys, and feature flags.
 *
 * <p><b>Property loading order:</b>
 * <ol>
 *   <li>On class load, the static initializer reads the bundled default properties file
 *       ({@code /drugref.properties}) from the classpath.</li>
 *   <li>At application startup, {@link StartUp} calls {@link #loader(String)} with the
 *       path derived from the servlet context (typically
 *       {@code DRUGREF_PROPERTIES_PATH + contextName + ".properties"}), which overlays
 *       environment-specific settings onto the defaults.</li>
 * </ol>
 *
 * <p>The singleton pattern ensures a single shared configuration instance across
 * all components. The {@code loaded} flag prevents the external properties file
 * from being loaded more than once.
 *
 * @author jackson
 */
public class DrugrefProperties extends Properties{
    private static Logger logger = MiscUtils.getLogger();
    private static final long serialVersionUID = -5965807410049845132L;

    /** Classpath resource path for the bundled default properties file. */
	private static final String DEFAULT_PROPERTIES = "/drugref.properties";

    /** The singleton instance, created eagerly at class load time. */
    private static DrugrefProperties drugrefProperties = new DrugrefProperties();

    /** Guards against loading the external properties file more than once. */
    private static boolean loaded = false;
    static{
            logger.debug("static initializer of drugrefproperties");
            readFromFile(DEFAULT_PROPERTIES,drugrefProperties);
    }


    /** Private constructor enforcing the singleton pattern. */
    private DrugrefProperties() {
		logger.debug("DRUGREF PROPS CONSTRUCTOR");
	}

    /**
     * Returns the singleton instance using the default properties file path.
     *
     * @return the singleton DrugrefProperties instance
     */
    public static DrugrefProperties getInstance() {
    	return getInstance(DEFAULT_PROPERTIES);
    }
    /**
     * Returns the singleton instance. The {@code url} parameter is accepted for
     * compatibility but does not trigger a reload; use {@link #loader(String)}
     * to load an external properties file.
     *
     * @param url the properties file path (logged for debugging but not loaded here)
     * @return the singleton DrugrefProperties instance
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
	
	
	
    /**
     * Reads properties from a file, first trying the classpath and then the filesystem.
     *
     * @param url the classpath resource path or filesystem path to the properties file
     * @param p the Properties object to load values into
     */
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

	/**
	 * Loads properties from an InputStream. Only executes once; subsequent calls are no-ops
	 * (guarded by the {@code loaded} flag) to prevent overwriting configuration at runtime.
	 *
	 * @param propertyStream the InputStream to read properties from
	 */
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

	/**
	 * Loads properties from a file path. Only executes once; subsequent calls are no-ops
	 * (guarded by the {@code loaded} flag) to prevent overwriting configuration at runtime.
	 *
	 * @param propFileName the absolute filesystem path to the properties file
	 * @throws java.io.FileNotFoundException if the file does not exist at the given path
	 */
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

        /**
         * Returns the JDBC database URL from the {@code db_url} property.
         *
         * @return the database connection URL string
         */
        public String getDbUrl(){
            return getProperty("db_url");
        }

        /**
         * Returns the database username from the {@code db_user} property.
         *
         * @return the database username
         */
        public String getDbUser(){
            return getProperty("db_user");
        }

        /**
         * Returns the database password from the {@code db_password} property.
         *
         * @return the database password
         */
        public String getDbPassword(){
            return getProperty("db_password");
        }

        /**
         * Returns the configured drug classes string from the {@code all_drug_classes} property.
         *
         * @return a comma-separated list of all drug class identifiers, or {@code null} if not set
         */
        public String getAllDrugClasses(){
            return getProperty("all_drug_classes");
        }

        /**
         * Checks whether the configured database is MySQL based on the JDBC URL.
         *
         * @return {@code true} if the db_url contains "mysql"
         */
        public boolean isMysql(){
            if(getDbUrl().contains("mysql"))
                return true;
            else
                return false;
        }
        /**
         * Checks whether the configured database is PostgreSQL based on the JDBC URL.
         *
         * @return {@code true} if the db_url contains "postgresql"
         */
        public boolean isPostgres(){
            if(getDbUrl().contains("postgresql"))
                return true;
            else
                return false;
        }

}

