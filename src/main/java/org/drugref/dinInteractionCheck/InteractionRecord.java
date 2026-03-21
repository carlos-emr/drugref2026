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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.builder.ReflectionToStringBuilder;

public class InteractionRecord {

	String recordType = null;
	String subType = null;
	String cnum1 = null;
	String dur1 = null ;
	String schedule1 = null ;
	String cn1cc = null ;
	String idcount1  = null; 
	String res  = null;
	String cnum2  = null;
	String dur2  = null;
	String schedule2 = null ;
	String cn2cc  = null;
	String idcount2  = null; 
	String intid  = null;
	String onset  = null;
	String severity  = null;
	String doc  = null;
	String mgmt  = null;
	String actcode1  = null; 
	String actcode2  = null; 
	String ctr  = null;
	String res2  = null;
	String warcc  = null;
	String effcc  = null;
	String meccc  = null;
	String mancc  = null;
	String discc  = null;
	String refcc  = null;
	String inttype  = null;
	String res3  = null;
	
	String cN1 = null; 
	String cN2 = null; 
	String wAR = null;  
	String eFF = null; 
	String mEC = null; 
	String mAN = null; 
	String dIS = null; 
	String rEF = null; 
	
	
	
	
	List<InteractingDrugRecord> interactingDrugList = new ArrayList<InteractingDrugRecord>();
	List<InteractionTextRecord> interactionTextList = new ArrayList<InteractionTextRecord>();


	
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
	

	public String getRecordType() {
		return recordType;
	}

	public String getSubType() {
		return subType;
	}

	public String getCnum1() {
		return cnum1;
	}

	public String getDur1() {
		return dur1;
	}

	public String getSchedule1() {
		return schedule1;
	}

	public String getCn1cc() {
		return cn1cc;
	}

	public String getIdcount1() {
		return idcount1;
	}

	public String getRes() {
		return res;
	}

	public String getCnum2() {
		return cnum2;
	}

	public String getDur2() {
		return dur2;
	}

	public String getSchedule2() {
		return schedule2;
	}

	public String getCn2cc() {
		return cn2cc;
	}

	public String getIdcount2() {
		return idcount2;
	}

	public String getIntid() {
		return intid;
	}

	public String getOnset() {
		return onset;
	}

	public String getSeverity() {
		return severity;
	}

	public String getDoc() {
		return doc;
	}

	public String getMgmt() {
		return mgmt;
	}

	public String getActcode1() {
		return actcode1;
	}

	public String getActcode2() {
		return actcode2;
	}

	public String getCtr() {
		return ctr;
	}

	public String getRes2() {
		return res2;
	}

	public String getWarcc() {
		return warcc;
	}

	public String getEffcc() {
		return effcc;
	}

	public String getMeccc() {
		return meccc;
	}

	public String getMancc() {
		return mancc;
	}

	public String getDiscc() {
		return discc;
	}

	public String getRefcc() {
		return refcc;
	}

	public String getInttype() {
		return inttype;
	}

	public String getRes3() {
		return res3;
	}
	
	
	public String getCN1() {
		return cN1;
	}

	public String getCN2() {
		return cN2;
	}

	public String getWAR() {
		return wAR;
	}

	public String getEFF() {
		return eFF;
	}

	public String getMEC() {
		return mEC;
	}

	public String getMAN() {
		return mAN;
	}

	public String getDIS() {
		return dIS;
	}

	public String getREF() {
		return rEF;
	}

	public List<InteractingDrugRecord> getInteractingDrugList() {
		return interactingDrugList;
	}
	
	public void addInteractionDrugList(InteractingDrugRecord interactingDrugRecord){
		interactingDrugList.add(interactingDrugRecord);
	}
	
	public List<InteractionTextRecord> getInteractionTextList() {
		return interactionTextList;
	}
	
	public void addInteractionTextList(InteractionTextRecord interactionTextRecord){
		interactionTextList.add(interactionTextRecord);
	}
	
	
	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	
}
