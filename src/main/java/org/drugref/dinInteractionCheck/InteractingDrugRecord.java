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
import org.apache.commons.lang.builder.ReflectionToStringBuilder;





public class InteractingDrugRecord {

	String recordType = null;
	String subType = null;
	String idItem1 = null;
	String idItem2 = null;
	String idItem3 = null;
	String idItem4 = null;
	String idItem5 = null;
	String idItem6 = null;
	String idItem7 = null;
	String idItem8 = null;
	String idItem9 = null;
	String res = null;

	
	public static InteractingDrugRecord parseString(String str){
		InteractingDrugRecord interactionRecord = new InteractingDrugRecord();
		LineCursor starterIndex = new LineCursor();
		starterIndex.setCurrentPosition(0);
		interactionRecord.recordType = LineCursor.getStringLengthStartingHere(str,starterIndex,3); 
		interactionRecord.subType = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		
		interactionRecord.idItem1 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.idItem2 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.idItem3 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.idItem4 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.idItem5 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.idItem6 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.idItem7 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.idItem8 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.idItem9 = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		interactionRecord.res = LineCursor.getStringLengthStartingHere(str,starterIndex,4);
		
		return interactionRecord;
	}

	public String getRecordType() {
		return recordType;
	}

	public String getSubType() {
		return subType;
	}

	
	
	public String getIdItem1() {
		return idItem1;
	}

	public String getIdItem2() {
		return idItem2;
	}

	public String getIdItem3() {
		return idItem3;
	}

	public String getIdItem4() {
		return idItem4;
	}

	public String getIdItem5() {
		return idItem5;
	}

	public String getIdItem6() {
		return idItem6;
	}

	public String getIdItem7() {
		return idItem7;
	}

	public String getIdItem8() {
		return idItem8;
	}

	public String getIdItem9() {
		return idItem9;
	}

	public String getRes() {
		return res;
	}

	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	
}
