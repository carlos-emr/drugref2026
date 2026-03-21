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


public class InteractionTextRecord {

	String recordType = null;
	String subType = null;
	String textName = null;
	String res = null;
	String text = null;
	
	public static InteractionTextRecord parseString(String str){
		InteractionTextRecord interactionRecord = new InteractionTextRecord();
		LineCursor starterIndex = new LineCursor();
		starterIndex.setCurrentPosition(0);
		interactionRecord.recordType = LineCursor.getStringLengthStartingHere(str,starterIndex,3);
		interactionRecord.subType = LineCursor.getStringLengthStartingHere(str,starterIndex,1); 
		
		interactionRecord.textName = LineCursor.getStringLengthStartingHere(str,starterIndex,3);
		interactionRecord.res = LineCursor.getStringLengthStartingHere(str,starterIndex,1);
		interactionRecord.text = LineCursor.getStringLengthStartingHere(str,starterIndex,72);
				
		return interactionRecord;
	}

	public String getRecordType() {
		return recordType;
	}

	public String getSubType() {
		return subType;
	}
	
	public String getRes() {
		return res;
	}

	public String getTextName() {
		return textName;
	}

	public String getText() {
		return text;
	}

	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	
}
