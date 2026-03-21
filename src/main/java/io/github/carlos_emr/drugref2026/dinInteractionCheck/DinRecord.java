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
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * Data class representing a DIN (Drug Identification Number) record from the Medi-Span data.
 *
 * <p>Parsed from an I031 fixed-width record, this class links a specific marketed drug product
 * (identified by its DIN) to its substance classification codes (KDC) and administration route.
 * The primary classification code ({@code kdc1}) is used to look up the drug's therapeutic
 * classes in the {@link DrugFormulation} map.</p>
 *
 * <h3>I031 fixed-width record layout</h3>
 * <pre>
 * Pos  Len  Field        Description
 *   0    3  recordType   Record type prefix ("I03")
 *   3    1  subType      Record subtype ("1")
 *   4    5  kdc1         Primary substance classification code (key into drugFormulationMap)
 *   9    2  kdc2         Secondary classification code
 *  11    3  kdc3         Tertiary classification code
 *  14    1  actcode      Activity code
 *  15    2  route        Route of administration code
 *  17    8  din          Drug Identification Number (DIN)
 * </pre>
 */
public class DinRecord {

	/** Record type prefix (e.g. "I03"). */
	String recordType = null;
	/** Record subtype (e.g. "1" for I031). */
	String subType = null;
	/** Primary substance classification code (5 characters). Key into the drug formulation map. */
	String kdc1 = null;
	/** Secondary classification code (2 characters). */
	String kdc2 = null;
	/** Tertiary classification code (3 characters). */
	String kdc3 = null;
	/** Activity code (1 character). */
	String actcode = null;
	/** Route of administration code (2 characters). */
	String route = null;
	/** Drug Identification Number (8 characters). Unique identifier for a marketed drug product. */
	String din  = null;

	/**
	 * Parses an I031 fixed-width record string into a {@link DinRecord} instance.
	 *
	 * @param str the raw fixed-width text line for the I031 record
	 * @return a populated DinRecord
	 */
	public static DinRecord parseString(String str){
		DinRecord dinRecord = new DinRecord();
		LineCursor starterIndex = new LineCursor();
		starterIndex.setCurrentPosition(0);
		dinRecord.recordType = LineCursor.getStringLengthStartingHere(str,starterIndex,3);
		dinRecord.subType    = LineCursor.getStringLengthStartingHere(str,starterIndex,1);
		dinRecord.kdc1        = LineCursor.getStringLengthStartingHere(str,starterIndex,5);
		dinRecord.kdc2    = LineCursor.getStringLengthStartingHere(str,starterIndex,2);
		dinRecord.kdc3       = LineCursor.getStringLengthStartingHere(str,starterIndex,3);
		dinRecord.actcode       = LineCursor.getStringLengthStartingHere(str,starterIndex,1);
		dinRecord.route     = LineCursor.getStringLengthStartingHere(str,starterIndex,2);
		dinRecord.din     = LineCursor.getStringLengthStartingHere(str,starterIndex,8);
		return dinRecord;
	}

	/** @return the record type prefix */
	public String getRecordType() {
		return recordType;
	}

	/** @return the record subtype */
	public String getSubType() {
		return subType;
	}

	/** @return the primary substance classification code (5 characters) */
	public String getKdc1() {
		return kdc1;
	}

	/** @return the secondary classification code (2 characters) */
	public String getKdc2() {
		return kdc2;
	}

	/** @return the tertiary classification code (3 characters) */
	public String getKdc3() {
		return kdc3;
	}

	/** @return the activity code */
	public String getActcode() {
		return actcode;
	}

	/** @return the route of administration code */
	public String getRoute() {
		return route;
	}

	/** @return the Drug Identification Number (DIN) */
	public String getDin() {
		return din;
	}
	
	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	

}
