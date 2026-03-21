/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * Originally: Copyright (c) 2001-2012. Department of Family Medicine, McMaster University. All Rights Reserved.
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.drugref.ca.dpd.TablesDao;
import org.drugref.util.MiscUtils;
import org.drugref.util.SpringUtils;


public class InteractionsChecker {
	
	private static final Logger logger = MiscUtils.getLogger();
	
	TablesDao queryDao = (TablesDao) SpringUtils.getBean("tablesDao");
	
	
	int count = 0;
	int lineCount = 0;
	private String cFOOD = null;
	private String cETHANOL = null;
	private String copyright = null;
	private String yearOfIssue = null;
	private String quarterOfIssue = null;
	private String fullPubDate = null;
	private Date publishDate = null;
	private String dayOfYearPubDate = null;
	private String expiresText = null;
	private String expiresDate = null;
	private String release = null;
	private String disclaimer = null;
	
	public String getRelease() {
		return release;
	}

	private Date updated = null;
	private List<String> errors = new ArrayList<String>();
	
	private String editionDTMS = null;
	
	public InteractionsChecker(){
		
	}
	
	
	public boolean interactionsCheckerActive(){
		return !interactionMap.isEmpty();
	}
	public List<String> getErrors(){
		return errors;
	}
	 
	public Map<String,DinRecord> dinmap = new HashMap<String,DinRecord>();
	public Map<String,DrugFormulation> drugFormulationMap = new HashMap<String,DrugFormulation>();
	public Map<String,InteractionRecord> interactionMap = new HashMap<String,InteractionRecord>();
	
	
	public int getNumberOfInteractions(){
		return interactionMap.size();
	}
	
	SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");	

	//If din is not current need to find one that is current.
	public String findClassByDinOrLikeDin(String din){
		DinRecord dinRecord = dinmap.get(din);
		if(dinRecord != null){
			logger.debug(din+":din exact match  returning "+dinRecord.getKdc1());
			return dinRecord.getKdc1();
		}
		logger.debug(din+":din exact match not found");
		 System.out.println(queryDao);
		 List<String> likeDins = queryDao.findLikeDins( din);
		 if(likeDins != null){
			 for(String likeDin:likeDins){
				  dinRecord = dinmap.get(likeDin);
				  if(dinRecord != null){
					  logger.debug(din+":din like match found "+likeDin+  " returning "+dinRecord.getKdc1());
						return dinRecord.getKdc1();
				  }
				  logger.debug(din+":tried din with no luck finding match "+likeDin);
			 }
		 }
		 logger.debug(din+":not like match found");
	   return null;	 
	}
	
	public List<String> findClassByDin(String din){
		try{
			//List<String> classes = drugFormulationMap.get(dinmap.get(din).getKdc1()).getClassList();
			String kdc1 = findClassByDinOrLikeDin(din);
			List<String> classes = drugFormulationMap.get(kdc1).getClassList();
			StringBuilder sb = new StringBuilder();
			for(String s:classes){
				sb.append(s+",");
			}
			logger.debug("Din "+din+" kdc1 "+kdc1 +" classes "+sb.toString());
			return classes;
		}catch(Exception e){
			logger.error("Din "+din+" not found",e);
		}
		return null;
	}
	
	public List<String> findClassByKDC(String kdc){
		try{
			List<String> classes = drugFormulationMap.get(kdc).getClassList();
			StringBuilder sb = new StringBuilder();
			for(String s:classes){
				sb.append(s+",");
			}
			logger.debug("kdc1"+kdc+" classes "+sb.toString());
			return classes;
		}catch(Exception e){
			logger.error("KDC "+kdc+" not found",e);
		}
		return null;
	}
	
	
	public List<InteractionRecord> checkForFoodAndEthanol(String din){
		List<InteractionRecord> interactions = new ArrayList<InteractionRecord>();
		List<String> classesForDrug = findClassByDin(din);
		List<String> classesForFood = findClassByKDC(cFOOD);
		List<String> classesForEthanol = findClassByKDC(cETHANOL);
		if(classesForDrug != null){
			for(String classOne:classesForDrug){
				for(String classTwo: classesForFood){
					InteractionRecord interaction = interactionMap.get(classOne.substring(0, 5)+":"+classTwo.substring(0, 5));
					logger.debug("FOOD1 "+classOne.substring(0, 5)+":"+classTwo.substring(0, 5)+" -- "+interaction);
					if(interaction != null){
						interactions.add(interaction);
					}
					interaction = interactionMap.get(classTwo.substring(0, 5)+":"+classOne.substring(0, 5));
					logger.debug("FOOD2 "+classTwo.substring(0, 5)+":"+classOne.substring(0, 5)+" -- "+interaction);
					if(interaction != null){
						interactions.add(interaction);
					}
				}
				for(String classTwo: classesForEthanol){
					InteractionRecord interaction = interactionMap.get(classOne.substring(0, 5)+":"+classTwo.substring(0, 5));
					logger.debug("FOOD1 "+classOne.substring(0, 5)+":"+classTwo.substring(0, 5)+" -- "+interaction);
					if(interaction != null){
						interactions.add(interaction);
					}
					interaction = interactionMap.get(classTwo.substring(0, 5)+":"+classOne.substring(0, 5));
					logger.debug("FOOD2 "+classTwo.substring(0, 5)+":"+classOne.substring(0, 5)+" -- "+interaction);
					if(interaction != null){
						interactions.add(interaction);
					}
				}
			}
		}
		return interactions;
	}
	
	public List<InteractionRecord> check(String din1, String din2){
		List<InteractionRecord> interactions = new ArrayList<InteractionRecord>();
		
		List<String> classesForDrugOne = findClassByDin(din1);
		List<String> classesForDrugTwo = findClassByDin(din2);
		
		if(classesForDrugOne == null || classesForDrugTwo == null){
			return null;
		}
		
		for(String classOne:classesForDrugOne){
			for (String classTwo: classesForDrugTwo){
				//logger.debug(classOne.substring(0, 5)+":"+classTwo.substring(0, 5)+" === "+interactionMap.get(classOne.substring(0, 5)+":"+classTwo.substring(0, 5)));
				InteractionRecord interaction = interactionMap.get(classOne.substring(0, 5)+":"+classTwo.substring(0, 5)); 
				if(interaction != null){
					interactions.add(interaction);
				}
				//logger.debug(classTwo.substring(0, 5)+":"+classOne.substring(0, 5)+" === "+interactionMap.get(classTwo.substring(0, 5)+":"+classOne.substring(0, 5)));
				interaction = interactionMap.get(classTwo.substring(0, 5)+":"+classOne.substring(0, 5));
				if(interaction != null){
					interactions.add(interaction);
				}
				
			}
		}
		
		return interactions;
	}
	
	private void parseV012(String line){
		LineCursor starterIndex = new LineCursor();
		starterIndex.setCurrentPosition(13);
		yearOfIssue = LineCursor.getStringLengthStartingHere(line,starterIndex,2);
		starterIndex.setCurrentPosition(16);
		quarterOfIssue = LineCursor.getStringLengthStartingHere(line,starterIndex,1);
		starterIndex.setCurrentPosition(18);
		fullPubDate = LineCursor.getStringLengthStartingHere(line,starterIndex,8); //YYYYMMDD
		if(fullPubDate != null){
			try{
				publishDate = simpleDateFormat.parse(fullPubDate);
			}catch(ParseException pe){
				logger.error("Publish Date not parse able:"+fullPubDate,pe);
			}
		}
		starterIndex.setCurrentPosition(27);
		dayOfYearPubDate = LineCursor.getStringLengthStartingHere(line,starterIndex,7);
		starterIndex.setCurrentPosition(44);
		expiresText = LineCursor.getStringLengthStartingHere(line,starterIndex,14);
		starterIndex.setCurrentPosition(59);
		expiresDate = LineCursor.getStringLengthStartingHere(line,starterIndex,6);
		starterIndex.setCurrentPosition(66);
		release = LineCursor.getStringLengthStartingHere(line,starterIndex,10);
	}

	public Date getPublishDate() {
		return publishDate;
	}

	public String getCopyright() {
		return copyright;
	}
	
	public String getIssue() {
		return yearOfIssue+"."+quarterOfIssue;
	}
	
	public static InteractionsChecker getInteractionsChecker(URL url){
		InteractionsChecker ichecker = new InteractionsChecker();
		BufferedReader bufferedReader  = null;
		InputStreamReader inputStreamReader = null;
		InputStream inputStream = null;		
		try {
			inputStream = url.openStream();
			inputStreamReader = new InputStreamReader(inputStream);
			bufferedReader = new BufferedReader(inputStreamReader);
		    String s;
		    
		    DrugFormulation drugFormulation = null;
			InteractionRecord interactionRecord = null;
			
		    while ((s = bufferedReader.readLine()) != null) {
		    	String recordType = s.substring(0,4);
	
				if("D011".equals(recordType)){
					drugFormulation = DrugFormulation.parseString(s);
					ichecker.drugFormulationMap.put(drugFormulation.getKdc(),drugFormulation);
				}else if("D012".equals(recordType)){
					DrugFormulation drugFormulationExt = DrugFormulation.parseString(s);
					drugFormulation.getClassList().addAll(drugFormulationExt.getClassList());
				}else if("I031".equals(recordType)){
					DinRecord dinRecord = DinRecord.parseString(s);
					ichecker.dinmap.put(dinRecord.getDin(),dinRecord);
				}else if("M011".equals(recordType)){
					if(interactionRecord != null){
						interactionRecord.processTextList(); //This will process all but the last record!
					}
					interactionRecord = InteractionRecord.parseString(s);
					ichecker.interactionMap.put(interactionRecord.getCnum1()+":"+interactionRecord.getCnum2(), interactionRecord);
				}else if("M013".equals(recordType)){
					InteractingDrugRecord interactingDrugRecord = InteractingDrugRecord.parseString(s); 
					interactionRecord.addInteractionDrugList(interactingDrugRecord);
				}else if("M019".equals(recordType)){
					InteractionTextRecord interactionTextRecord = InteractionTextRecord.parseString(s);
					interactionRecord.addInteractionTextList(interactionTextRecord);
				}else if ("I021".equals(recordType)){
					LineCursor starterIndex = new LineCursor();
					starterIndex.setCurrentPosition(30);
					String drugName =  LineCursor.getStringLengthStartingHere(s,starterIndex,50); 
					starterIndex.setCurrentPosition(4);
					if(drugName.equalsIgnoreCase("Food")){
						ichecker.cFOOD = LineCursor.getStringLengthStartingHere(s,starterIndex,5);
						logger.debug("FOOD :"+ichecker.cFOOD);
					}else if(drugName.equalsIgnoreCase("Ethanol")){
						ichecker.cETHANOL = LineCursor.getStringLengthStartingHere(s,starterIndex,5);
						logger.debug("ETHANOL: "+ichecker.cETHANOL);
					}
					
				}else if ("V011".equals(recordType)){
					LineCursor starterIndex = new LineCursor();
					starterIndex.setCurrentPosition(5);
					ichecker.copyright =  LineCursor.getStringLengthStartingHere(s,starterIndex,75); //3 C RTYPE 
				}else if ("V012".equals(recordType)){
					ichecker.parseV012(s);
					
				}else if ("V019".equals(recordType)){
					LineCursor starterIndex = new LineCursor();
					starterIndex.setCurrentPosition(5);
					ichecker.editionDTMS =  LineCursor.getStringLengthStartingHere(s,starterIndex,70); //3 C RTYPE 
				
				}else if("ERRO".equals(recordType)){
					ichecker.errors.add(s);
				}
				ichecker.count++;
		        ichecker.lineCount++;
		    }
		    bufferedReader.close();
		} catch (IOException e) {
			logger.error("Error loading",e);
			return null;
		} finally {
			IOUtils.closeQuietly(bufferedReader);
			IOUtils.closeQuietly(inputStreamReader);
			IOUtils.closeQuietly(inputStream);
		}
		logger.info(ichecker.getAudit());
		logger.info("copyright "+ichecker.copyright);
		logger.info("year of issue: "+ichecker.yearOfIssue +" quarterOfIssue "+ichecker.quarterOfIssue+" fullPubDate "+ichecker.fullPubDate+" dayOfYearPubDate "+ichecker.dayOfYearPubDate+" expires Text "+ichecker.expiresText+" expiresDate "+ichecker.expiresDate+" release " +ichecker.release);
		ichecker.updated = new Date();
		return ichecker;
	}
	
	public String getAudit(){
		return  "lineCount:"+lineCount+" dinMap:"+dinmap.size()+" drugFormulationMap:"+drugFormulationMap.size()+" interactionMap:"+interactionMap.size();
		
	}
	public Date getUpdatedDate(){
		return updated;
	}
	
	SimpleDateFormat yearMonthDate = new SimpleDateFormat("yyyyMM");	
	
	Date expireDateDate = null;
	public Date getExpiryDate(){
		if(expireDateDate == null){
			try{
				expireDateDate = yearMonthDate.parse(expiresDate);
			}catch(Exception e){
				logger.error("Error Processing expireDate ",e);
			}
		}
		return expireDateDate;
	}
	
	public String getEdition(){
		return editionDTMS;
	}


	public String getDisclaimer() {
		return disclaimer;
	}


	public void setDisclaimer(String disclaimer) {
		this.disclaimer = disclaimer;
	}
	
	
}