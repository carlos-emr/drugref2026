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
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.drugref2026.util.MiscUtils;

/**
 * Utility class for parsing fixed-width text records by tracking a cursor position within a string.
 *
 * <p>This class implements a simple cursor pattern for sequential extraction of fixed-width
 * fields from a text line. The cursor maintains its current position, and each call to
 * {@link #getStringLengthStartingHere(String, LineCursor, int)} extracts a substring of
 * the specified length and automatically advances the cursor past the extracted field.</p>
 *
 * <p>Usage pattern:</p>
 * <pre>
 *   LineCursor cursor = new LineCursor();
 *   cursor.setCurrentPosition(0);
 *   String field1 = LineCursor.getStringLengthStartingHere(line, cursor, 5);  // cursor now at 5
 *   String field2 = LineCursor.getStringLengthStartingHere(line, cursor, 3);  // cursor now at 8
 * </pre>
 *
 * <p>The cursor can also be repositioned with {@link #setCurrentPosition(int)} to skip
 * fields or jump to a known offset in the record.</p>
 */
public class LineCursor {
	private static final Logger logger = MiscUtils.getLogger();

	/** The current character position (0-based index) within the string being parsed. */
	int currentPosition = 0;

	/**
	 * Returns the current cursor position within the string.
	 *
	 * @return the 0-based character index
	 */
	public int getCurrentPosition() {
		return currentPosition;
	}

	/**
	 * Sets the cursor to a specific position within the string.
	 *
	 * @param currentPosition the 0-based character index to move to
	 */
	public void setCurrentPosition(int currentPosition) {
		this.currentPosition = currentPosition;
	}

	/**
	 * Extracts a substring of the specified length starting at the cursor's current position,
	 * then advances the cursor past the extracted field.
	 *
	 * <p>If the cursor is already at the end of the string, returns {@code null}.
	 * If the requested length extends beyond the string's end, the extraction is
	 * truncated to the available characters.</p>
	 *
	 * @param str          the source string to extract from
	 * @param starterIndex the LineCursor tracking the current read position (will be advanced)
	 * @param length       the number of characters to extract
	 * @return the extracted substring, or {@code null} if the cursor is at the end of the string
	 */
	public static String getStringLengthStartingHere(String str,LineCursor starterIndex, int length){
		
		String retval = null;
		if (str.length() == starterIndex.getCurrentPosition()){
			return null;
		}
		try{
			
			int endPosition = starterIndex.getCurrentPosition()+length;
			if(endPosition > str.length()){
				endPosition = str.length();
			}
			retval = str.substring(starterIndex.getCurrentPosition(),endPosition);
			starterIndex.setCurrentPosition(endPosition);
		}catch(Exception e){
			logger.error("String = "+str+" index "+starterIndex,e);
		}
		return retval;
	}
}