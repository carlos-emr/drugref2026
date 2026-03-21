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
package io.github.carlos_emr.drugref2026.dinInteractionCheck;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.drugref2026.util.DrugrefProperties;
import io.github.carlos_emr.drugref2026.util.MiscUtils;
import io.github.carlos_emr.drugref2026.util.SpringUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

/**
 * Factory and lifecycle manager for the {@link InteractionsChecker} singleton.
 *
 * <p>This class is responsible for:</p>
 * <ul>
 *   <li>Validating the Medi-Span licence key before attempting to load data.</li>
 *   <li>Performing the initial download and parsing of interaction data via {@link #load()}.</li>
 *   <li>Scheduling periodic status checks with the remote server to detect when new data
 *       versions are available, and triggering a reload when needed.</li>
 *   <li>Providing thread-safe access to the current {@link InteractionsChecker} instance
 *       via {@link #getInteractionChecker()}.</li>
 * </ul>
 *
 * <h3>Scheduling logic</h3>
 * <p>After the initial load, the factory enters a polling loop driven by the remote server's
 * status endpoint. The {@code /status} endpoint returns one of:</p>
 * <ul>
 *   <li>{@code "NEW_VERSION_AVAILABLE"} -- triggers an immediate reload</li>
 *   <li>A numeric delay in milliseconds -- schedules the next status check after that delay</li>
 *   <li>A string starting with {@code "ERROR"} -- records the error and stops polling</li>
 * </ul>
 *
 * <p>The {@code scheduled_timer} property from {@link DrugrefProperties} provides the initial
 * polling interval after the first successful load.</p>
 */
public class InteractionsCheckerFactory {

	private static final Logger logger = MiscUtils.getLogger();
	/** The current active InteractionsChecker instance. Replaced atomically on successful reload. */
	private static InteractionsChecker interactionsChecker = new InteractionsChecker();

	/** Base URL for the Medi-Span interaction data service (from configuration). */
	static String baseUrl = DrugrefProperties.getInstance().getProperty("interaction_base_url");
	/** Licence key required to authenticate with the Medi-Span data service. */
	static String licenceKey=  DrugrefProperties.getInstance().getProperty("licence_key");

	/** Single-thread scheduler used for delayed status check polling. */
	private static final ScheduledExecutorService delayScheduler = Executors.newScheduledThreadPool(1);
	/** Thread pool executor used to run load and status-check tasks off the main thread. */
	private static ExecutorService taskScheduler = null;


	/**
	 * Starts the interaction data loading and periodic refresh cycle.
	 *
	 * <p>Does nothing if no licence key is configured. On first call, obtains the Spring-managed
	 * thread pool executor and initiates the first data load via the scheduler.</p>
	 */
	public static void start(){
		if(!StringUtils.hasText(licenceKey)) {
			return;
		}
		if(taskScheduler == null){
			ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) SpringUtils.getBean("taskScheduler");
			taskScheduler = executor.getThreadPoolExecutor();
			scheduler(null);
		}
	}
	
	/**
	 * Central scheduling dispatcher that determines the next action based on the status response.
	 *
	 * @param stat the status string: {@code null} for initial load, {@code "NEW_VERSION_AVAILABLE"}
	 *        to trigger a reload, a numeric string (milliseconds) to schedule the next check,
	 *        or an {@code "ERROR..."} string to record the error
	 */
	private static void scheduler(String stat){
		logger.debug("stat="+stat);
		if(stat == null){
			callLoadIn();
		}else if("NEW_VERSION_AVAILABLE".equals(stat)){
			callLoadIn();
		}else if(stat.startsWith("ERROR")){
			interactionsChecker.getErrors().add("<br>"+stat);
		}else{
			int delay = Integer.parseInt(stat);
			delayScheduler.schedule(() -> checkStatus(), delay, TimeUnit.MILLISECONDS);
		}
	}
	
	/** Monotonically increasing counter for logging/identifying scheduled task invocations. */
	static int numCount = 0;

	/**
	 * Returns a unique incrementing task number for logging purposes.
	 *
	 * @return the next task sequence number
	 */
	private static int getNum(){
		numCount++;
		return numCount; 
	}
	
	/**
	 * Submits a data load task to the task scheduler thread pool.
	 * After loading completes, re-enters the scheduling loop with the configured timer delay.
	 */
	private static void callLoadIn(){
		try{
			taskScheduler.execute(new Runnable(){
				int counta = getNum();
				public void run() {
					
					logger.info("running"+counta);
					load();
					scheduler(DrugrefProperties.getInstance().getProperty("scheduled_timer")); //five minutes = 300000
				}
			});
			}catch(Exception e){
				logger.error("Error Running callLoadIn",e);
			}

	}
	
	/**
	 * Submits a status check task to the thread pool. Queries the remote server's
	 * {@code /status} endpoint with the current release version and licence key,
	 * then feeds the response back into {@link #scheduler(String)}.
	 */
	private static void checkStatus(){
		
		try{
			taskScheduler.execute(new Runnable(){
				int counta = getNum();

				public void run() {
					logger.info("checking"+counta);
					String retval = null;
					try{
						URL webStream2 = new URL(baseUrl+"/status?release="+URLEncoder.encode(interactionsChecker.getRelease(),"utf-8")+"&licenceKey="+URLEncoder.encode(licenceKey, "utf-8"));
						retval = readStream(webStream2);
						logger.info("check status response:"+ retval);
					}catch(Exception e){
						logger.error("Error loading stream ",e);
						//interacerrors.add("<br>ERROR: Could not process Interactions file. "+e.getMessage());
					}
					
					
					scheduler(retval);
				}
				
			});
			}catch(Exception e){
				logger.error("uh oh",e);
			}
		
	}
	
	
	/**
	 * Downloads the full interaction data file from the remote server and replaces the
	 * current {@link InteractionsChecker} instance if the new data is valid (contains
	 * at least one interaction record).
	 *
	 * <p>The load process performs three HTTP requests:</p>
	 * <ol>
	 *   <li>{@code /file} -- downloads the complete fixed-width interaction data file</li>
	 *   <li>{@code /audit} -- sends an audit summary back to the server for verification</li>
	 *   <li>{@code /disclaimer} -- retrieves the disclaimer text to attach to the checker</li>
	 * </ol>
	 *
	 * @return {@code true} if the data was successfully downloaded and parsed, {@code false} on error
	 */
	public static boolean load(){
		InteractionsChecker ichecker = null; 
		List<String>  errors = new ArrayList<String>();
		boolean retval = false;
		logger.info("Going to download file");
		try{
			URL webStream = new URL(baseUrl+"/file?currentVersion=0&licenceKey="+URLEncoder.encode(licenceKey, "utf-8"));
			ichecker = InteractionsChecker.getInteractionsChecker(webStream);
		
			URL webStream2 = new URL(baseUrl+"/audit?audit="+URLEncoder.encode(ichecker.getAudit(),"utf-8")+"&licenceKey="+URLEncoder.encode(licenceKey, "utf-8"));
			logger.info("audit response:"+ readStream(webStream2));
			
			URL webStream3 = new URL(baseUrl+"/disclaimer?licenceKey="+URLEncoder.encode(licenceKey, "utf-8"));
			ichecker.setDisclaimer(readStream(webStream3));
			retval = true;
		}catch(Exception e){
			logger.error("Error loading stream ",e);
			errors.add("<br>ERROR: Could not process Interactions file. "+e.getMessage());
		}
		logger.info("file loaded");
		for(String s:errors){
			logger.info(s);
		}
		if(ichecker != null && ichecker.getNumberOfInteractions() > 0 ){
			interactionsChecker = ichecker;
		}
		
		return retval;
	}
	
	/**
	 * Reads the entire content of a URL as a single string.
	 *
	 * @param url the URL to read from
	 * @return the content as a string, or {@code null} if an I/O error occurs
	 */
	public static String readStream(URL  url){
		StringBuilder sb = new StringBuilder();
		BufferedReader bufferedReader  = null;
		InputStreamReader inputStreamReader = null;
		InputStream inputStream = null;		
		try {
			inputStream = url.openStream();
			inputStreamReader = new InputStreamReader(inputStream);
			bufferedReader = new BufferedReader(inputStreamReader);
		    String str;
		    while ((str = bufferedReader.readLine()) != null) {
		        sb.append(str);
		    }
		    bufferedReader.close();
		} catch (IOException e) {
			logger.error("Error loading",e);
			return null;
		}
		finally {
			IOUtils.closeQuietly(bufferedReader);
			IOUtils.closeQuietly(inputStreamReader);
			IOUtils.closeQuietly(inputStream);
		}
		return sb.toString();
	}
	
	
	/**
	 * Returns the current {@link InteractionsChecker} singleton instance.
	 * If the instance has not been initialized yet, triggers an immediate {@link #load()}.
	 *
	 * @return the active InteractionsChecker instance
	 */
	public static InteractionsChecker getInteractionChecker(){
			if(interactionsChecker == null){
				logger.info("having to load interaction checker");
				load();
			}
			return interactionsChecker;
	}
	
		
}
