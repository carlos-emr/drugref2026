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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Data class representing a single drug-drug interaction from the Medi-Span interaction data.
 *
 * <p>Parsed from an M011 fixed-width record, this class holds metadata about a drug interaction
 * pair identified by two substance class numbers ({@code cnum1} and {@code cnum2}). The composite
 * key {@code cnum1:cnum2} is used to look up interactions in the
 * {@link InteractionsChecker#interactionMap}.</p>
 *
 * <h3>Clinical metadata fields</h3>
 * <ul>
 *   <li>{@code onset} -- Onset timing code (e.g. rapid, delayed)</li>
 *   <li>{@code severity} -- Severity level code (e.g. major, moderate, minor)</li>
 *   <li>{@code doc} -- Documentation level code (quality of supporting evidence)</li>
 *   <li>{@code mgmt} -- Clinical management level code</li>
 * </ul>
 *
 * <h3>Text record references</h3>
 * <p>Character counts (cc fields) indicate the expected length of associated text sections.
 * The actual text content is assembled from {@link InteractionTextRecord} entries via
 * {@link #processTextList()}:</p>
 * <ul>
 *   <li>{@code WAR} (warcc) -- Warning/title text for the interaction</li>
 *   <li>{@code EFF} (effcc) -- Clinical effect description</li>
 *   <li>{@code MEC} (meccc) -- Mechanism of interaction</li>
 *   <li>{@code MAN} (mancc) -- Clinical management recommendations</li>
 *   <li>{@code DIS} (discc) -- Detailed discussion text</li>
 *   <li>{@code REF} (refcc) -- Literature references</li>
 * </ul>
 *
 * <p>Text records use placeholders {@code @A@} and {@code @B@} for drug names, which are
 * replaced with the actual drug names (CN1 and CN2) during {@link #processTextList()}.</p>
 *
 * <h3>M011 fixed-width record layout</h3>
 * <pre>
 * Pos  Len  Field        Description
 *   0    3  recordType   Record type prefix ("M01")
 *   3    1  subType      Record subtype ("1")
 *   4    5  cnum1        Substance class number for drug 1
 *   9    3  dur1         DUR code for drug 1
 *  12    3  schedule1    Schedule code for drug 1
 *  15    2  cn1cc        Character count for CN1 drug name
 *  17    2  idcount1     ID count for drug 1
 *  19    4  res          Reserved
 *  23    5  cnum2        Substance class number for drug 2
 *  28    3  dur2         DUR code for drug 2
 *  31    3  schedule2    Schedule code for drug 2
 *  34    2  cn2cc        Character count for CN2 drug name
 *  36    2  idcount2     ID count for drug 2
 *  38    4  intid        Interaction identifier
 *  42    1  onset        Onset code
 *  43    1  severity     Severity code
 *  44    1  doc          Documentation level code
 *  45    1  mgmt         Management level code
 *  46    1  actcode1     Action code for drug 1
 *  47    1  actcode2     Action code for drug 2
 *  48    1  ctr          Contraindication flag
 *  49    1  res2         Reserved
 *  50    4  warcc        Warning text character count
 *  54    4  effcc        Effect text character count
 *  58    4  meccc        Mechanism text character count
 *  62    4  mancc        Management text character count
 *  66    4  discc        Discussion text character count
 *  70    4  refcc        Reference text character count
 *  74    1  inttype      Interaction type code
 *  75    5  res3         Reserved
 * </pre>
 */
public class InteractionRecord {

	/** Record type prefix (e.g. "M01"). */
	String recordType = null;
	/** Record subtype (e.g. "1" for M011). */
	String subType = null;
	/** Substance class number for drug 1 (5 characters). Used as part of the interaction map key. */
	String cnum1 = null;
	/** DUR (Drug Utilization Review) code for drug 1. */
	String dur1 = null ;
	/** Schedule code for drug 1. */
	String schedule1 = null ;
	/** Character count for the CN1 (drug 1 name) text. */
	String cn1cc = null ;
	/** Number of interacting drug detail records (M013) for drug 1. */
	String idcount1  = null;
	/** Reserved field. */
	String res  = null;
	/** Substance class number for drug 2 (5 characters). Used as part of the interaction map key. */
	String cnum2  = null;
	/** DUR (Drug Utilization Review) code for drug 2. */
	String dur2  = null;
	/** Schedule code for drug 2. */
	String schedule2 = null ;
	/** Character count for the CN2 (drug 2 name) text. */
	String cn2cc  = null;
	/** Number of interacting drug detail records (M013) for drug 2. */
	String idcount2  = null;
	/** Unique interaction identifier. */
	String intid  = null;
	/** Onset timing code (e.g. rapid, delayed). */
	String onset  = null;
	/** Severity level code (e.g. major, moderate, minor). */
	String severity  = null;
	/** Documentation level code (quality of supporting evidence). */
	String doc  = null;
	/** Clinical management level code. */
	String mgmt  = null;
	/** Action code for drug 1. */
	String actcode1  = null;
	/** Action code for drug 2. */
	String actcode2  = null;
	/** Contraindication flag. */
	String ctr  = null;
	/** Reserved field. */
	String res2  = null;
	/** Character count for warning (WAR) text section. */
	String warcc  = null;
	/** Character count for effect (EFF) text section. */
	String effcc  = null;
	/** Character count for mechanism (MEC) text section. */
	String meccc  = null;
	/** Character count for management (MAN) text section. */
	String mancc  = null;
	/** Character count for discussion (DIS) text section. */
	String discc  = null;
	/** Character count for reference (REF) text section. */
	String refcc  = null;
	/** Interaction type code. */
	String inttype  = null;
	/** Reserved field. */
	String res3  = null;

	/** Assembled drug name for drug 1 (from CN1 text records). */
	String cN1 = null;
	/** Assembled drug name for drug 2 (from CN2 text records). */
	String cN2 = null;
	/** Assembled warning/title text (from WAR text records, with drug name placeholders replaced). */
	String wAR = null;
	/** Assembled clinical effect text (from EFF text records, with drug name placeholders replaced). */
	String eFF = null;
	/** Assembled mechanism of interaction text (from MEC text records, with drug name placeholders replaced). */
	String mEC = null;
	/** Assembled clinical management text (from MAN text records, with drug name placeholders replaced). */
	String mAN = null;
	/** Assembled discussion text (from DIS text records, with drug name placeholders replaced). */
	String dIS = null;
	/** Assembled literature reference text (from REF text records, with drug name placeholders replaced). */
	String rEF = null;


	/** List of interacting drug detail records (M013) associated with this interaction. */
	List<InteractingDrugRecord> interactingDrugList = new ArrayList<InteractingDrugRecord>();
	/** List of interaction text records (M019) to be assembled into the text fields. */
	List<InteractionTextRecord> interactionTextList = new ArrayList<InteractionTextRecord>();


	
	/**
	 * Parses an M011 fixed-width record string into an {@link InteractionRecord} instance.
	 *
	 * <p>Uses a {@link LineCursor} to sequentially extract each field from the fixed-width
	 * record according to the M011 layout specification.</p>
	 *
	 * @param str the raw fixed-width text line for the M011 record
	 * @return a populated InteractionRecord
	 */
	public static InteractionRecord parseString(String str){
		InteractionRecord interactionRecord = new InteractionRecord();
		LineCursor starterIndex = new LineCursor();
		starterIndex.setCurrentPosition(0);
		interactionRecord.recordType = LineCursor.getStringLengthStartingHere(str,starterIndex,3);  
		interactionRecord.subType = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
	
	
		interactionRecord.cnum1 = LineCursor.getStringLengthStartingHere(str,starterIndex,5); 
		interactionRecord.dur1 = LineCursor.getStringLengthStartingHere(str,starterIndex,3); 
		interactionRecord.schedule1 = LineCursor.getStringLengthStartingHere(str,starterIndex,3); 
		interactionRecord.cn1cc = LineCursor.getStringLengthStartingHere(str,starterIndex,2); 
		interactionRecord.idcount1 = LineCursor.getStringLengthStartingHere(str,starterIndex,2); 
		interactionRecord.res = LineCursor.getStringLengthStartingHere(str,starterIndex,4);
		interactionRecord.cnum2 = LineCursor.getStringLengthStartingHere(str,starterIndex,5); 
		interactionRecord.dur2 = LineCursor.getStringLengthStartingHere(str,starterIndex,3); 
		interactionRecord.schedule2 = LineCursor.getStringLengthStartingHere(str,starterIndex,3); 
		interactionRecord.cn2cc = LineCursor.getStringLengthStartingHere(str,starterIndex,2); 
		interactionRecord.idcount2 = LineCursor.getStringLengthStartingHere(str,starterIndex,2); 
		interactionRecord.intid = LineCursor.getStringLengthStartingHere(str,starterIndex,4);  
		interactionRecord.onset = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		interactionRecord.severity = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		interactionRecord.doc = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		interactionRecord.mgmt = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		interactionRecord.actcode1 = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		interactionRecord.actcode2 = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		interactionRecord.ctr = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		interactionRecord.res2 = LineCursor.getStringLengthStartingHere(str,starterIndex,1);
		interactionRecord.warcc = LineCursor.getStringLengthStartingHere(str,starterIndex,4); 
		interactionRecord.effcc = LineCursor.getStringLengthStartingHere(str,starterIndex,4); 
		interactionRecord.meccc = LineCursor.getStringLengthStartingHere(str,starterIndex,4);
		interactionRecord.mancc = LineCursor.getStringLengthStartingHere(str,starterIndex,4);
		interactionRecord.discc = LineCursor.getStringLengthStartingHere(str,starterIndex,4); 
		interactionRecord.refcc = LineCursor.getStringLengthStartingHere(str,starterIndex,4); 
		interactionRecord.inttype = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		interactionRecord.res3 = LineCursor.getStringLengthStartingHere(str,starterIndex,5);
		return interactionRecord;
	}
	
	
	/**
	 * Assembles the clinical text fields from the accumulated {@link InteractionTextRecord} entries.
	 *
	 * <p>Each text record has a {@code textName} that identifies which section it belongs to
	 * (CN1, CN2, WAR, EFF, MEC, MAN, DIS, REF). Multiple records of the same type are
	 * concatenated. After assembly, the placeholders {@code @A@} and {@code @B@} in the
	 * text are replaced with the actual drug names (CN1 and CN2 respectively).</p>
	 *
	 * <p>This method must be called after all M019 text records for this interaction have
	 * been added via {@link #addInteractionTextList(InteractionTextRecord)}.</p>
	 */
	public void processTextList(){
		StringBuilder wARbuilder = new StringBuilder();  
		StringBuilder eFFbuilder = new StringBuilder(); 
		StringBuilder mECbuilder = new StringBuilder(); 
		StringBuilder mANbuilder = new StringBuilder(); 
		StringBuilder dISbuilder = new StringBuilder(); 
		StringBuilder rEFbuilder = new StringBuilder(); 
		
		for (InteractionTextRecord textRecord: interactionTextList){
			if (textRecord.textName.equals("CN1")){
				cN1 = textRecord.getText();
			}
			if (textRecord.textName.equals("CN2")){
				cN2 = textRecord.getText();
			}
			if (textRecord.textName.equals("WAR")){
				wARbuilder.append(textRecord.getText());
			}
			if (textRecord.textName.equals("EFF")){
				eFFbuilder.append(textRecord.getText());
			}
			if (textRecord.textName.equals("MEC")){
				mECbuilder.append(textRecord.getText());
			}
			if (textRecord.textName.equals("MAN")){
				mANbuilder.append(textRecord.getText());
			}
			if (textRecord.textName.equals("DIS")){
				dISbuilder.append(textRecord.getText());
			}
			if (textRecord.textName.equals("REF")){
				rEFbuilder.append(textRecord.getText());
			}	
		}
		
		wAR = wARbuilder.toString().replaceAll("@A@", cN1).replaceAll("@B@", cN2); 
		eFF = eFFbuilder.toString().replaceAll("@A@", cN1).replaceAll("@B@", cN2); 
		mEC = mECbuilder.toString().replaceAll("@A@", cN1).replaceAll("@B@", cN2);
		mAN = mANbuilder.toString().replaceAll("@A@", cN1).replaceAll("@B@", cN2);
		dIS = dISbuilder.toString().replaceAll("@A@", cN1).replaceAll("@B@", cN2);
		rEF = rEFbuilder.toString().replaceAll("@A@", cN1).replaceAll("@B@", cN2);
		
	}
	

	/** @return the record type prefix (e.g. "M01") */
	public String getRecordType() {
		return recordType;
	}

	/** @return the record subtype (e.g. "1" for M011) */
	public String getSubType() {
		return subType;
	}

	/** @return the substance class number for drug 1 (5 characters) */
	public String getCnum1() {
		return cnum1;
	}

	/** @return the DUR code for drug 1 */
	public String getDur1() {
		return dur1;
	}

	/** @return the schedule code for drug 1 */
	public String getSchedule1() {
		return schedule1;
	}

	/** @return the character count for drug 1 name text */
	public String getCn1cc() {
		return cn1cc;
	}

	/** @return the ID count for drug 1 detail records */
	public String getIdcount1() {
		return idcount1;
	}

	/** @return reserved field value */
	public String getRes() {
		return res;
	}

	/** @return the substance class number for drug 2 (5 characters) */
	public String getCnum2() {
		return cnum2;
	}

	/** @return the DUR code for drug 2 */
	public String getDur2() {
		return dur2;
	}

	/** @return the schedule code for drug 2 */
	public String getSchedule2() {
		return schedule2;
	}

	/** @return the character count for drug 2 name text */
	public String getCn2cc() {
		return cn2cc;
	}

	/** @return the ID count for drug 2 detail records */
	public String getIdcount2() {
		return idcount2;
	}

	/** @return the unique interaction identifier */
	public String getIntid() {
		return intid;
	}

	/** @return the onset timing code */
	public String getOnset() {
		return onset;
	}

	/** @return the severity level code */
	public String getSeverity() {
		return severity;
	}

	/** @return the documentation level code */
	public String getDoc() {
		return doc;
	}

	/** @return the clinical management level code */
	public String getMgmt() {
		return mgmt;
	}

	/** @return the action code for drug 1 */
	public String getActcode1() {
		return actcode1;
	}

	/** @return the action code for drug 2 */
	public String getActcode2() {
		return actcode2;
	}

	/** @return the contraindication flag */
	public String getCtr() {
		return ctr;
	}

	/** @return reserved field value */
	public String getRes2() {
		return res2;
	}

	/** @return the warning text character count */
	public String getWarcc() {
		return warcc;
	}

	/** @return the effect text character count */
	public String getEffcc() {
		return effcc;
	}

	/** @return the mechanism text character count */
	public String getMeccc() {
		return meccc;
	}

	/** @return the management text character count */
	public String getMancc() {
		return mancc;
	}

	/** @return the discussion text character count */
	public String getDiscc() {
		return discc;
	}

	/** @return the reference text character count */
	public String getRefcc() {
		return refcc;
	}

	/** @return the interaction type code */
	public String getInttype() {
		return inttype;
	}

	/** @return reserved field value */
	public String getRes3() {
		return res3;
	}


	/** @return the assembled name of drug 1 (from CN1 text records) */
	public String getCN1() {
		return cN1;
	}

	/** @return the assembled name of drug 2 (from CN2 text records) */
	public String getCN2() {
		return cN2;
	}

	/** @return the assembled warning/title text (with drug name placeholders replaced) */
	public String getWAR() {
		return wAR;
	}

	/** @return the assembled clinical effect text (with drug name placeholders replaced) */
	public String getEFF() {
		return eFF;
	}

	/** @return the assembled mechanism of interaction text (with drug name placeholders replaced) */
	public String getMEC() {
		return mEC;
	}

	/** @return the assembled clinical management text (with drug name placeholders replaced) */
	public String getMAN() {
		return mAN;
	}

	/** @return the assembled discussion text (with drug name placeholders replaced) */
	public String getDIS() {
		return dIS;
	}

	/** @return the assembled literature reference text (with drug name placeholders replaced) */
	public String getREF() {
		return rEF;
	}

	/**
	 * Returns the list of interacting drug detail records (M013) for this interaction.
	 *
	 * @return the list of {@link InteractingDrugRecord} entries
	 */
	public List<InteractingDrugRecord> getInteractingDrugList() {
		return interactingDrugList;
	}

	/**
	 * Adds an interacting drug detail record (parsed from an M013 line) to this interaction.
	 *
	 * @param interactingDrugRecord the detail record to add
	 */
	public void addInteractionDrugList(InteractingDrugRecord interactingDrugRecord){
		interactingDrugList.add(interactingDrugRecord);
	}

	/**
	 * Returns the list of interaction text records (M019) for this interaction.
	 *
	 * @return the list of {@link InteractionTextRecord} entries
	 */
	public List<InteractionTextRecord> getInteractionTextList() {
		return interactionTextList;
	}

	/**
	 * Adds an interaction text record (parsed from an M019 line) to this interaction.
	 *
	 * @param interactionTextRecord the text record to add
	 */
	public void addInteractionTextList(InteractionTextRecord interactionTextRecord){
		interactionTextList.add(interactionTextRecord);
	}
	
	
	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	
}
