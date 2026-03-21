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
package io.github.carlos_emr.drugref2026.dinInteractionCheck;


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
import io.github.carlos_emr.drugref2026.ca.dpd.TablesDao;
import io.github.carlos_emr.drugref2026.util.MiscUtils;
import io.github.carlos_emr.drugref2026.util.SpringUtils;


/**
 * Core engine for checking drug-drug interactions using Medi-Span interaction data.
 *
 * <p>This class loads interaction data from a remote URL (served as a fixed-width text file),
 * parses it into in-memory data structures, and provides {@link #check(String, String)} methods
 * to find interactions between drug pairs identified by their DIN (Drug Identification Number).</p>
 *
 * <h3>Data structures</h3>
 * <ul>
 *   <li>{@code dinmap} -- maps DIN strings to {@link DinRecord} objects, linking marketed drug
 *       products to their substance classification codes (KDC).</li>
 *   <li>{@code drugFormulationMap} -- maps KDC codes to {@link DrugFormulation} objects that
 *       hold the therapeutic class indicator list for that formulation.</li>
 *   <li>{@code interactionMap} -- maps a composite key of two 5-character class codes
 *       (e.g. {@code "classA:classB"}) to an {@link InteractionRecord} describing the
 *       interaction between those two substance classes.</li>
 * </ul>
 *
 * <h3>Lookup algorithm</h3>
 * <ol>
 *   <li>Resolve each DIN to its KDC code via {@code dinmap} (falling back to "like" DIN
 *       lookups in the database if an exact match is not found).</li>
 *   <li>Expand each KDC code to a list of therapeutic class codes via {@code drugFormulationMap}.</li>
 *   <li>For every pair of class codes (one from each drug), check both orderings in
 *       {@code interactionMap} to find recorded interactions.</li>
 * </ol>
 *
 * <h3>Data file record types</h3>
 * The remote data file contains fixed-width records identified by a 4-character prefix:
 * <ul>
 *   <li>{@code V011} -- Copyright notice</li>
 *   <li>{@code V012} -- Publication metadata (year, quarter, publish date, expiry, release)</li>
 *   <li>{@code V019} -- Edition descriptor (DTMS edition text)</li>
 *   <li>{@code D011} -- Drug formulation record (primary)</li>
 *   <li>{@code D012} -- Drug formulation extension record (additional class codes)</li>
 *   <li>{@code I021} -- Substance name record (used to identify Food and Ethanol codes)</li>
 *   <li>{@code I031} -- DIN-to-substance mapping record</li>
 *   <li>{@code M011} -- Interaction master record (severity, onset, clinical significance)</li>
 *   <li>{@code M013} -- Interacting drug detail record</li>
 *   <li>{@code M019} -- Interaction text record (warnings, effects, mechanism, management, etc.)</li>
 *   <li>{@code ERRO} -- Error record returned by the data service</li>
 * </ul>
 */
public class InteractionsChecker {

	private static final Logger logger = MiscUtils.getLogger();

	/** DAO for querying "like" DINs when an exact DIN match is not found in the in-memory map. */
	TablesDao queryDao = (TablesDao) SpringUtils.getBean("tablesDao");


	/** Running count of records processed during data loading. */
	int count = 0;
	/** Total number of lines read from the data file. */
	int lineCount = 0;
	/** KDC substance code for Food (parsed from I021 records). */
	private String cFOOD = null;
	/** KDC substance code for Ethanol (parsed from I021 records). */
	private String cETHANOL = null;
	/** Copyright text from the Medi-Span data file (V011 record). */
	private String copyright = null;
	/** Two-digit year of issue from V012 record. */
	private String yearOfIssue = null;
	/** Single-digit quarter of issue from V012 record. */
	private String quarterOfIssue = null;
	/** Full publication date string in YYYYMMDD format from V012 record. */
	private String fullPubDate = null;
	/** Parsed publication date. */
	private Date publishDate = null;
	/** Day-of-year representation of the publication date from V012 record. */
	private String dayOfYearPubDate = null;
	/** Expiration description text from V012 record. */
	private String expiresText = null;
	/** Expiration date string in YYYYMM format from V012 record. */
	private String expiresDate = null;
	/** Release identifier from V012 record. */
	private String release = null;
	/** Disclaimer text retrieved from the data service. */
	private String disclaimer = null;

	/**
	 * Returns the release identifier string parsed from the data file's V012 record.
	 *
	 * @return the release identifier, or {@code null} if data has not been loaded
	 */
	public String getRelease() {
		return release;
	}

	/** Timestamp of the most recent successful data load. */
	private Date updated = null;
	/** List of error messages encountered during data loading (e.g. ERRO records from the service). */
	private List<String> errors = new ArrayList<String>();

	/** Edition description text from the V019 record (DTMS edition). */
	private String editionDTMS = null;

	/**
	 * Default constructor. Creates an empty InteractionsChecker with no loaded data.
	 * Use {@link #getInteractionsChecker(URL)} to create a fully populated instance.
	 */
	public InteractionsChecker(){

	}
	
	
	/**
	 * Checks whether interaction data has been successfully loaded and is available for lookups.
	 *
	 * @return {@code true} if the interaction map is non-empty (data is loaded), {@code false} otherwise
	 */
	public boolean interactionsCheckerActive(){
		return !interactionMap.isEmpty();
	}

	/**
	 * Returns the list of error messages accumulated during data loading.
	 *
	 * @return list of error strings, including ERRO records from the data service
	 */
	public List<String> getErrors(){
		return errors;
	}

	/** Map of DIN (Drug Identification Number) to its parsed {@link DinRecord}. */
	public Map<String,DinRecord> dinmap = new HashMap<String,DinRecord>();
	/** Map of KDC (substance classification code) to its {@link DrugFormulation} with therapeutic class list. */
	public Map<String,DrugFormulation> drugFormulationMap = new HashMap<String,DrugFormulation>();
	/** Map of composite interaction key ({@code "cnum1:cnum2"}) to its {@link InteractionRecord}. */
	public Map<String,InteractionRecord> interactionMap = new HashMap<String,InteractionRecord>();


	/**
	 * Returns the total number of interaction records loaded.
	 *
	 * @return size of the interaction map
	 */
	public int getNumberOfInteractions(){
		return interactionMap.size();
	}
	
	SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");	

	/**
	 * Resolves a DIN to its primary substance classification code (KDC1).
	 *
	 * <p>First attempts an exact lookup in the in-memory {@code dinmap}. If no exact match
	 * is found, falls back to a database query for "like" DINs (drugs with similar identifiers,
	 * e.g. different packaging of the same product) and checks each candidate against the map.</p>
	 *
	 * @param din the Drug Identification Number to look up
	 * @return the KDC1 substance classification code, or {@code null} if no match is found
	 */
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
	
	/**
	 * Finds the list of therapeutic class codes for a drug identified by its DIN.
	 *
	 * <p>Resolves the DIN to a KDC code via {@link #findClassByDinOrLikeDin(String)}, then
	 * retrieves the corresponding {@link DrugFormulation} and returns its class list.</p>
	 *
	 * @param din the Drug Identification Number
	 * @return list of therapeutic class code strings, or {@code null} if the DIN cannot be resolved
	 */
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
	
	/**
	 * Finds the list of therapeutic class codes for a substance identified by its KDC code.
	 *
	 * @param kdc the substance classification code (KDC)
	 * @return list of therapeutic class code strings, or {@code null} if the KDC is not found
	 */
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
	
	
	/**
	 * Checks for interactions between a drug (identified by DIN) and Food or Ethanol.
	 *
	 * <p>Resolves the drug's class codes, then cross-references them against the known
	 * Food and Ethanol substance class codes. Both orderings of the class pair key
	 * are checked in the interaction map (i.e. drug:food and food:drug).</p>
	 *
	 * @param din the Drug Identification Number of the drug to check
	 * @return list of {@link InteractionRecord} objects for any found food/ethanol interactions;
	 *         may be empty if no interactions are found
	 */
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
	
	/**
	 * Checks for drug-drug interactions between two drugs identified by their DINs.
	 *
	 * <p>The algorithm resolves each DIN to its list of therapeutic class codes, then
	 * checks every combination of class code pairs (from drug 1 and drug 2) against
	 * the interaction map. Both orderings of each pair are checked (classA:classB and
	 * classB:classA) since interactions may be recorded in either direction.</p>
	 *
	 * <p>The interaction map key uses the first 5 characters of each class code,
	 * which represents the substance-level classification.</p>
	 *
	 * @param din1 the DIN of the first drug
	 * @param din2 the DIN of the second drug
	 * @return list of {@link InteractionRecord} objects for found interactions,
	 *         or {@code null} if either DIN cannot be resolved to class codes
	 */
	public List<InteractionRecord> check(String din1, String din2){
		List<InteractionRecord> interactions = new ArrayList<InteractionRecord>();

		List<String> classesForDrugOne = findClassByDin(din1);
		List<String> classesForDrugTwo = findClassByDin(din2);

		if(classesForDrugOne == null || classesForDrugTwo == null){
			return null;
		}

		for(String classOne:classesForDrugOne){
			for (String classTwo: classesForDrugTwo){
				// Check classOne:classTwo ordering in the interaction map (first 5 chars = substance code)
				InteractionRecord interaction = interactionMap.get(classOne.substring(0, 5)+":"+classTwo.substring(0, 5));
				if(interaction != null){
					interactions.add(interaction);
				}
				// Check the reverse ordering classTwo:classOne as interactions may be stored either way
				interaction = interactionMap.get(classTwo.substring(0, 5)+":"+classOne.substring(0, 5));
				if(interaction != null){
					interactions.add(interaction);
				}

			}
		}

		return interactions;
	}
	
	/**
	 * Parses a V012 (publication metadata) record from the data file.
	 *
	 * <p>V012 fixed-width format fields:</p>
	 * <ul>
	 *   <li>Position 13, length 2: year of issue</li>
	 *   <li>Position 16, length 1: quarter of issue</li>
	 *   <li>Position 18, length 8: full publication date (YYYYMMDD)</li>
	 *   <li>Position 27, length 7: day-of-year publication date</li>
	 *   <li>Position 44, length 14: expiration text</li>
	 *   <li>Position 59, length 6: expiration date (YYYYMM)</li>
	 *   <li>Position 66, length 10: release identifier</li>
	 * </ul>
	 *
	 * @param line the raw fixed-width text line for the V012 record
	 */
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

	/**
	 * Returns the publication date of the loaded interaction data.
	 *
	 * @return the publish date, or {@code null} if not yet loaded or unparseable
	 */
	public Date getPublishDate() {
		return publishDate;
	}

	/**
	 * Returns the copyright notice from the Medi-Span data file.
	 *
	 * @return the copyright string, or {@code null} if not loaded
	 */
	public String getCopyright() {
		return copyright;
	}
	
	/**
	 * Returns the issue identifier as "year.quarter" (e.g. "24.3").
	 *
	 * @return the formatted issue string
	 */
	public String getIssue() {
		return yearOfIssue+"."+quarterOfIssue;
	}
	
	/**
	 * Factory method that downloads and parses the Medi-Span interaction data file from the
	 * given URL, returning a fully populated {@link InteractionsChecker} instance.
	 *
	 * <p>The data file is read line by line. Each line's first 4 characters identify the
	 * record type, which determines how the line is parsed:</p>
	 * <ul>
	 *   <li>{@code D011} -- Primary drug formulation record; stored in {@code drugFormulationMap}</li>
	 *   <li>{@code D012} -- Extension formulation record; class codes appended to the previous D011 entry</li>
	 *   <li>{@code I021} -- Substance name record; used to capture Food and Ethanol KDC codes</li>
	 *   <li>{@code I031} -- DIN record; stored in {@code dinmap}</li>
	 *   <li>{@code M011} -- Interaction master record; triggers processing of the previous interaction's
	 *       text list, then creates a new {@link InteractionRecord} keyed by {@code cnum1:cnum2}</li>
	 *   <li>{@code M013} -- Interacting drug detail; added to the current interaction record</li>
	 *   <li>{@code M019} -- Interaction text record; added to the current interaction record</li>
	 *   <li>{@code V011} -- Copyright notice</li>
	 *   <li>{@code V012} -- Publication metadata</li>
	 *   <li>{@code V019} -- Edition DTMS text</li>
	 *   <li>{@code ERRO} -- Error message from the data service</li>
	 * </ul>
	 *
	 * @param url the URL to download the interaction data file from
	 * @return a populated InteractionsChecker, or {@code null} if an I/O error occurs
	 */
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
			
		    // Process each line of the fixed-width data file; the first 4 characters identify the record type
		    while ((s = bufferedReader.readLine()) != null) {
		    	String recordType = s.substring(0,4);
	
				if("D011".equals(recordType)){
					// D011: Primary drug formulation -- store by KDC in the formulation map
					drugFormulation = DrugFormulation.parseString(s);
					ichecker.drugFormulationMap.put(drugFormulation.getKdc(),drugFormulation);
				}else if("D012".equals(recordType)){
					// D012: Extension formulation -- merge additional class codes into the previous D011 entry
					DrugFormulation drugFormulationExt = DrugFormulation.parseString(s);
					drugFormulation.getClassList().addAll(drugFormulationExt.getClassList());
				}else if("I031".equals(recordType)){
					// I031: DIN record -- map the DIN to its substance classification codes
					DinRecord dinRecord = DinRecord.parseString(s);
					ichecker.dinmap.put(dinRecord.getDin(),dinRecord);
				}else if("M011".equals(recordType)){
					// M011: New interaction master record -- finalize the previous interaction's text,
					// then parse and store this new interaction keyed by "cnum1:cnum2"
					if(interactionRecord != null){
						interactionRecord.processTextList(); //This will process all but the last record!
					}
					interactionRecord = InteractionRecord.parseString(s);
					ichecker.interactionMap.put(interactionRecord.getCnum1()+":"+interactionRecord.getCnum2(), interactionRecord);
				}else if("M013".equals(recordType)){
					// M013: Interacting drug detail -- add to the current interaction record
					InteractingDrugRecord interactingDrugRecord = InteractingDrugRecord.parseString(s);
					interactionRecord.addInteractionDrugList(interactingDrugRecord);
				}else if("M019".equals(recordType)){
					// M019: Interaction text segment -- accumulate for later assembly by processTextList()
					InteractionTextRecord interactionTextRecord = InteractionTextRecord.parseString(s);
					interactionRecord.addInteractionTextList(interactionTextRecord);
				}else if ("I021".equals(recordType)){
					// I021: Substance name record -- capture the KDC codes for Food and Ethanol
					// so they can be used for food/ethanol interaction checks
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
					// V011: Copyright notice -- extract the 75-char copyright text starting at position 5
					LineCursor starterIndex = new LineCursor();
					starterIndex.setCurrentPosition(5);
					ichecker.copyright =  LineCursor.getStringLengthStartingHere(s,starterIndex,75); //3 C RTYPE
				}else if ("V012".equals(recordType)){
					// V012: Publication metadata (year, quarter, dates, release)
					ichecker.parseV012(s);

				}else if ("V019".equals(recordType)){
					// V019: Edition DTMS text -- extract the 70-char edition description
					LineCursor starterIndex = new LineCursor();
					starterIndex.setCurrentPosition(5);
					ichecker.editionDTMS =  LineCursor.getStringLengthStartingHere(s,starterIndex,70); //3 C RTYPE

				}else if("ERRO".equals(recordType)){
					// ERRO: Error message from the data service
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
	
	/**
	 * Returns an audit summary string showing the number of lines processed and the sizes
	 * of the three main data maps (dinMap, drugFormulationMap, interactionMap).
	 *
	 * @return a formatted audit string for logging or remote verification
	 */
	public String getAudit(){
		return  "lineCount:"+lineCount+" dinMap:"+dinmap.size()+" drugFormulationMap:"+drugFormulationMap.size()+" interactionMap:"+interactionMap.size();
		
	}
	/**
	 * Returns the timestamp of the most recent successful data load.
	 *
	 * @return the date/time when data was last loaded, or {@code null} if never loaded
	 */
	public Date getUpdatedDate(){
		return updated;
	}
	
	SimpleDateFormat yearMonthDate = new SimpleDateFormat("yyyyMM");	
	
	/** Cached parsed expiration date (lazily parsed from {@code expiresDate}). */
	Date expireDateDate = null;

	/**
	 * Returns the expiration date of the loaded data set, parsed from the YYYYMM format
	 * expiration string in the V012 record. The result is cached after first parse.
	 *
	 * @return the expiration date, or {@code null} if the date string is absent or unparseable
	 */
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
	
	/**
	 * Returns the DTMS edition text from the V019 record.
	 *
	 * @return the edition descriptor string, or {@code null} if not loaded
	 */
	public String getEdition(){
		return editionDTMS;
	}


	/**
	 * Returns the disclaimer text retrieved from the data service.
	 *
	 * @return the disclaimer string, or {@code null} if not set
	 */
	public String getDisclaimer() {
		return disclaimer;
	}


	/**
	 * Sets the disclaimer text (typically retrieved from the data service's /disclaimer endpoint).
	 *
	 * @param disclaimer the disclaimer text to store
	 */
	public void setDisclaimer(String disclaimer) {
		this.disclaimer = disclaimer;
	}
	
	
}