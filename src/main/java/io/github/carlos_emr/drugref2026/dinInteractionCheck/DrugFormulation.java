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
 * Data class representing a drug formulation record with therapeutic class indicators
 * from the Medi-Span interaction data.
 *
 * <p>Parsed from D011 (primary) and D012 (extension) fixed-width records, each instance
 * holds a formulation identified by its KDC (substance classification code) along with
 * up to eight therapeutic class codes. Extension records (D012) contribute additional
 * class codes that are merged into the primary record's {@code classList}.</p>
 *
 * <p>The {@code classList} is used by {@link InteractionsChecker} to expand a drug's KDC code
 * into the full set of therapeutic class codes needed for interaction lookups.</p>
 *
 * <h3>D011/D012 fixed-width record layout</h3>
 * <pre>
 * Pos  Len  Field        Description
 *   0    3  recordType   Record type prefix ("D01")
 *   3    1  subType      Record subtype ("1" for primary, "2" for extension)
 *   4    5  kdc          Substance classification code (KDC)
 *   9    2  ciCount      Count of class indicators in this record
 *  11    8  cOne         Therapeutic class code 1
 *  19    8  cTwo         Therapeutic class code 2
 *  27    8  cThree       Therapeutic class code 3
 *  35    8  cFour        Therapeutic class code 4
 *  43    8  cFive        Therapeutic class code 5
 *  51    8  cSix         Therapeutic class code 6
 *  59    8  cSeven       Therapeutic class code 7
 *  67    8  cEight       Therapeutic class code 8
 * </pre>
 */
public class DrugFormulation {

	/** Record type prefix (e.g. "D01"). */
	String recordType = null;
	/** Record subtype ("1" for primary, "2" for extension). */
	String subType    = null;
	/** Substance classification code (KDC, 5 characters). Key in the drug formulation map. */
	String kdc        = null;
	/** Count of class indicators present in this record. */
	String ciCount    = null;
	/** Therapeutic class code 1 (8 characters). */
	String cOne       = null;
	/** Therapeutic class code 2. */
	String cTwo       = null;
	/** Therapeutic class code 3. */
	String cThree     = null;
	/** Therapeutic class code 4. */
	String cFour      = null;
	/** Therapeutic class code 5. */
	String cFive      = null;
	/** Therapeutic class code 6. */
	String cSix       = null;
	/** Therapeutic class code 7. */
	String cSeven     = null;
	/** Therapeutic class code 8. */
	String cEight     = null;

	/** Aggregated list of all non-null therapeutic class codes from this record (and any extensions). */
	List<String> classList = new ArrayList<String>();

	/**
	 * Parses a D011 or D012 fixed-width record string into a {@link DrugFormulation} instance.
	 *
	 * <p>After parsing the fixed-width fields, all non-null class code fields (cOne through cEight)
	 * are added to the {@code classList} for convenient iteration.</p>
	 *
	 * @param str the raw fixed-width text line for the D011/D012 record
	 * @return a populated DrugFormulation
	 */
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

	/** @return the record type prefix */
	public String getRecordType() {
		return recordType;
	}

	/** @return the record subtype ("1" for primary, "2" for extension) */
	public String getSubType() {
		return subType;
	}

	/** @return the substance classification code (KDC) */
	public String getKdc() {
		return kdc;
	}

	/** @return the count of class indicators in this record */
	public String getCiCount() {
		return ciCount;
	}

	/** @return therapeutic class code 1 */
	public String getcOne() {
		return cOne;
	}

	/** @return therapeutic class code 2 */
	public String getcTwo() {
		return cTwo;
	}

	/** @return therapeutic class code 3 */
	public String getcThree() {
		return cThree;
	}

	/** @return therapeutic class code 4 */
	public String getcFour() {
		return cFour;
	}

	/** @return therapeutic class code 5 */
	public String getcFive() {
		return cFive;
	}

	/** @return therapeutic class code 6 */
	public String getcSix() {
		return cSix;
	}

	/** @return therapeutic class code 7 */
	public String getcSeven() {
		return cSeven;
	}

	/** @return therapeutic class code 8 */
	public String getcEight() {
		return cEight;
	}

	/**
	 * Returns the aggregated list of therapeutic class codes from this formulation record
	 * (including any extension records whose codes have been merged in).
	 *
	 * @return the list of class code strings
	 */
	public List<String> getClassList() {
		return classList;
	}

	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	
}
