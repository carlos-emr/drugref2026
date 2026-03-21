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
 * Data class representing an interacting drug detail record from the Medi-Span interaction data.
 *
 * <p>Parsed from an M013 fixed-width record, this class contains up to nine item fields
 * ({@code idItem1} through {@code idItem9}), each 8 characters wide. These items represent
 * substance codes, route codes, or other identifiers that further specify which specific
 * drug products are involved in an interaction.</p>
 *
 * <h3>M013 fixed-width record layout</h3>
 * <pre>
 * Pos  Len  Field        Description
 *   0    3  recordType   Record type prefix ("M01")
 *   3    1  subType      Record subtype ("3")
 *   4    8  idItem1      Item identifier 1 (substance/route code)
 *  12    8  idItem2      Item identifier 2
 *  20    8  idItem3      Item identifier 3
 *  28    8  idItem4      Item identifier 4
 *  36    8  idItem5      Item identifier 5
 *  44    8  idItem6      Item identifier 6
 *  52    8  idItem7      Item identifier 7
 *  60    8  idItem8      Item identifier 8
 *  68    8  idItem9      Item identifier 9
 *  76    4  res          Reserved
 * </pre>
 */
public class InteractingDrugRecord {

	/** Record type prefix (e.g. "M01"). */
	String recordType = null;
	/** Record subtype (e.g. "3" for M013). */
	String subType = null;
	/** Item identifier 1 (substance/route code, 8 characters). */
	String idItem1 = null;
	/** Item identifier 2. */
	String idItem2 = null;
	/** Item identifier 3. */
	String idItem3 = null;
	/** Item identifier 4. */
	String idItem4 = null;
	/** Item identifier 5. */
	String idItem5 = null;
	/** Item identifier 6. */
	String idItem6 = null;
	/** Item identifier 7. */
	String idItem7 = null;
	/** Item identifier 8. */
	String idItem8 = null;
	/** Item identifier 9. */
	String idItem9 = null;
	/** Reserved field. */
	String res = null;


	/**
	 * Parses an M013 fixed-width record string into an {@link InteractingDrugRecord} instance.
	 *
	 * @param str the raw fixed-width text line for the M013 record
	 * @return a populated InteractingDrugRecord
	 */
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

	/** @return the record type prefix */
	public String getRecordType() {
		return recordType;
	}

	/** @return the record subtype */
	public String getSubType() {
		return subType;
	}

	/** @return item identifier 1 */
	public String getIdItem1() {
		return idItem1;
	}

	/** @return item identifier 2 */
	public String getIdItem2() {
		return idItem2;
	}

	/** @return item identifier 3 */
	public String getIdItem3() {
		return idItem3;
	}

	/** @return item identifier 4 */
	public String getIdItem4() {
		return idItem4;
	}

	/** @return item identifier 5 */
	public String getIdItem5() {
		return idItem5;
	}

	/** @return item identifier 6 */
	public String getIdItem6() {
		return idItem6;
	}

	/** @return item identifier 7 */
	public String getIdItem7() {
		return idItem7;
	}

	/** @return item identifier 8 */
	public String getIdItem8() {
		return idItem8;
	}

	/** @return item identifier 9 */
	public String getIdItem9() {
		return idItem9;
	}

	/** @return the reserved field value */
	public String getRes() {
		return res;
	}

	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	
}
