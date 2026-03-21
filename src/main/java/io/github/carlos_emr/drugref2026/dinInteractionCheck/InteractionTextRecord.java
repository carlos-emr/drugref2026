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
 * Data class representing a single interaction text record from the Medi-Span interaction data.
 *
 * <p>Parsed from an M019 fixed-width record, each instance holds one segment of clinical
 * text for a drug interaction. Multiple M019 records with the same {@code textName} are
 * concatenated by {@link InteractionRecord#processTextList()} to form the complete text.</p>
 *
 * <h3>Text name codes</h3>
 * <ul>
 *   <li>{@code CN1} -- Drug name for substance 1</li>
 *   <li>{@code CN2} -- Drug name for substance 2</li>
 *   <li>{@code WAR} -- Warning/title text describing the interaction</li>
 *   <li>{@code EFF} -- Clinical effect description</li>
 *   <li>{@code MEC} -- Mechanism of the interaction</li>
 *   <li>{@code MAN} -- Clinical management recommendations</li>
 *   <li>{@code DIS} -- Detailed discussion/explanation</li>
 *   <li>{@code REF} -- Literature references</li>
 * </ul>
 *
 * <h3>M019 fixed-width record layout</h3>
 * <pre>
 * Pos  Len  Field        Description
 *   0    3  recordType   Record type prefix ("M01")
 *   3    1  subType      Record subtype ("9")
 *   4    3  textName     Text section identifier (CN1, CN2, WAR, EFF, MEC, MAN, DIS, REF)
 *   7    1  res          Reserved
 *   8   72  text         Text content (up to 72 characters per record)
 * </pre>
 */
public class InteractionTextRecord {

	/** Record type prefix (e.g. "M01"). */
	String recordType = null;
	/** Record subtype (e.g. "9" for M019). */
	String subType = null;
	/** Text section identifier (CN1, CN2, WAR, EFF, MEC, MAN, DIS, or REF). */
	String textName = null;
	/** Reserved field. */
	String res = null;
	/** Text content for this record segment (up to 72 characters). */
	String text = null;

	/**
	 * Parses an M019 fixed-width record string into an {@link InteractionTextRecord} instance.
	 *
	 * @param str the raw fixed-width text line for the M019 record
	 * @return a populated InteractionTextRecord
	 */
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

	/** @return the record type prefix */
	public String getRecordType() {
		return recordType;
	}

	/** @return the record subtype */
	public String getSubType() {
		return subType;
	}

	/** @return the reserved field value */
	public String getRes() {
		return res;
	}

	/** @return the text section identifier (CN1, CN2, WAR, EFF, MEC, MAN, DIS, or REF) */
	public String getTextName() {
		return textName;
	}

	/** @return the text content for this record segment */
	public String getText() {
		return text;
	}

	@Override
    public String toString(){
		return(ReflectionToStringBuilder.toString(this));
	}
	
}
