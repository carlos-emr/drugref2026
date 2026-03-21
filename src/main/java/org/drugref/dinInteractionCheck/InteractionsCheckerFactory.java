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
package org.drugref.dinInteractionCheck;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.drugref.util.DrugrefProperties;
import org.drugref.util.MiscUtils;
import org.drugref.util.SpringUtils;
import org.springframework.scheduling.timer.TimerTaskExecutor;
import org.springframework.util.StringUtils;

public class InteractionsCheckerFactory {
	
	private static final Logger logger = MiscUtils.getLogger();
	private static InteractionsChecker interactionsChecker = new InteractionsChecker();  
	
	static String baseUrl = DrugrefProperties.getInstance().getProperty("interaction_base_url");
	static String licenceKey=  DrugrefProperties.getInstance().getProperty("licence_key");
	
	//private final ScheduledExecutorService scheduler =  Executors.newScheduledThreadPool(1);
	private static TimerTaskExecutor taskScheduler = null;
	
	
	public static void start(){
		if(StringUtils.isEmpty(licenceKey)) {
			return;
		}
		if(taskScheduler == null){
			taskScheduler = (TimerTaskExecutor) SpringUtils.getBean("taskScheduler");
			scheduler(null);
		}
	}
	
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
			taskScheduler.setDelay(delay);
			checkStatus();
		}
	}
	
	static int numCount = 0;
	private static int getNum(){
		numCount++;
		return numCount; 
	}
	
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
	
	
	public static InteractionsChecker getInteractionChecker(){
			if(interactionsChecker == null){
				logger.info("having to load interaction checker");
				load();
			}
			return interactionsChecker;
	}
	
		
}
