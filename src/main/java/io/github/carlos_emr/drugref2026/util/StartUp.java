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

//import java.io.File;
//import java.io.InputStream;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.drugref2026.dinInteractionCheck.InteractionsCheckerFactory;

/**
 * Servlet context listener that initializes the drugref2026 application on deployment.
 *
 * <p>Performs the following startup tasks:
 * <ol>
 *   <li>Loads the external properties file whose path is constructed from the
 *       {@code DRUGREF_PROPERTIES_PATH} init parameter (set in {@code web.xml})
 *       combined with the servlet context name (e.g., {@code /opt/drugref2026.properties}).</li>
 *   <li>Falls back to a relative path ({@code ../../} prefix) if the primary path
 *       is not found, to accommodate different deployment directory structures.</li>
 *   <li>Initializes the Medi-Span drug interaction checker via
 *       {@link InteractionsCheckerFactory#start()}, which loads the interaction
 *       database file if a valid license key is configured.</li>
 * </ol>
 *
 * <p>Configured in {@code web.xml} as a {@code <listener>}.
 *
 * @author Jay Gallagher
 * @author Dennis Warren (modifications, March 2015)
 */
public class StartUp implements ServletContextListener {
	private static Logger logger = MiscUtils.getLogger();

	/** Default constructor. */
	public StartUp() {
		// Default
	}

	/**
	 * Called by the servlet container when the web application is deployed.
	 * Loads configuration properties and starts the interaction checker.
	 *
	 * @param sc the servlet context event providing access to context parameters
	 */
	public void contextInitialized(ServletContextEvent sc) {

		logger.info("contextInit called");

		// Build the properties file path from the web.xml init parameter and context name.
		// Example: if DRUGREF_PROPERTIES_PATH="/opt/" and context name="drugref2026",
		// the resulting path is "/opt/drugref2026.properties".
		String contextPath = sc.getServletContext().getServletContextName();
		String filePath = sc.getServletContext().getInitParameter("DRUGREF_PROPERTIES_PATH");
		String propertiesFilePath =  filePath + contextPath + ".properties";

		logger.info( "Looking for properties file at: " + propertiesFilePath );

		DrugrefProperties drugRefProperties = DrugrefProperties.getInstance(propertiesFilePath);

		try {
			// Attempt to load from the primary path (absolute path from web.xml config)
			drugRefProperties.loader(propertiesFilePath);
		} catch (java.io.FileNotFoundException ex) {
	        logger.error( "properties file not found at" + propertiesFilePath, ex);
            try {
            	// Fallback: try a relative path two directories up, to handle cases where
            	// the working directory differs from the expected deployment root
            	propertiesFilePath = "../../" + filePath  + contextPath + ".properties";
            	drugRefProperties.loader(propertiesFilePath);
            } catch (java.io.FileNotFoundException exc) {
                logger.error( "properties file not found at" + propertiesFilePath, exc);
			}
		}

		// Initialize the Medi-Span interaction checker. This reads the licence_key property
		// and loads the interaction database file in the background if licensed.
		logger.info("About to start Interactions checker with key"+drugRefProperties.getProperty("licence_key"));
		InteractionsCheckerFactory.start(); //Get the file loading
		logger.info("LAST LINE IN contextInitialized");

	}

	/**
	 * Called by the servlet container when the web application is being shut down.
	 * Performs any necessary cleanup.
	 *
	 * @param arg0 the servlet context event
	 */
	public void contextDestroyed(ServletContextEvent arg0) {
		logger.info("DRUGREF SHUTTING DOWN");
	}

}
