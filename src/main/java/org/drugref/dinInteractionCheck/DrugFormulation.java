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

public class DrugFormulation {
	
	String recordType = null;
	String subType    = null;
	String kdc        = null;
	String ciCount    = null;
	String cOne       = null;
	String cTwo       = null;
	String cThree     = null;
	String cFour      = null;
	String cFive      = null;
	String cSix       = null;
	String cSeven     = null;
	String cEight     = null;
	
	List<String> classList = new ArrayList<String>();

	public static DrugFormulation parseString(String str){
		DrugFormulation drugFormulation = new DrugFormulation();
			
		LineCursor starterIndex = new LineCursor();
		starterIndex.setCurrentPosition(0);
		drugFormulation.recordType = LineCursor.getStringLengthStartingHere(str,starterIndex,3);
		drugFormulation.subType    = LineCursor.getStringLengthStartingHere(str,starterIndex,1);
		drugFormulation.kdc        = LineCursor.getStringLengthStartingHere(str,starterIndex,5);
		drugFormulation.ciCount    = LineCursor.getStringLengthStartingHere(str,starterIndex,2);
		drugFormulation.cOne       = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		drugFormulation.cTwo       = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		drugFormulation.cThree     = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		drugFormulation.cFour      = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		drugFormulation.cFive      = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		drugFormulation.cSix       = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		drugFormulation.cSeven     = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		drugFormulation.cEight     = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		
		if(drugFormulation.cOne != null){
			drugFormulation.classList.add(drugFormulation.cOne);
		}
		if(drugFormulation.cTwo != null){
			drugFormulation.classList.add(drugFormulation.cTwo);
		}
		if(drugFormulation.cThree != null){     
			drugFormulation.classList.add(drugFormulation.cThree);
		}
		if(drugFormulation.cFour != null){      
			drugFormulation.classList.add(drugFormulation.cFour);
		}
		if(drugFormulation.cFive != null){      
			drugFormulation.classList.add(drugFormulation.cFive);
		}
		if(drugFormulation.cSix != null){       
			drugFormulation.classList.add(drugFormulation.cSix);
		}
		if(drugFormulation.cSeven != null){     
			drugFormulation.classList.add(drugFormulation.cSeven);
		}
		if(drugFormulation.cEight != null){     
			drugFormulation.classList.add(drugFormulation.cEight);
		}
		
		
		return drugFormulation;
	}

	public String getRecordType() {
		return recordType;
	}

	public String getSubType() {
		return subType;
	}

	public String getKdc() {
		return kdc;
	}

	public String getCiCount() {
		return ciCount;
	}

	public String getcOne() {
		return cOne;
	}

	public String getcTwo() {
		return cTwo;
	}

	public String getcThree() {
		return cThree;
	}

	public String getcFour() {
		return cFour;
	}

	public String getcFive() {
		return cFive;
	}

	public String getcSix() {
		return cSix;
	}

	public String getcSeven() {
		return cSeven;
	}

	public String getcEight() {
		return cEight;
	}
	
	public List<String> getClassList() {
		return classList;
	}

	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	
}
