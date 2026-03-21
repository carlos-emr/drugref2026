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
import org.apache.logging.log4j.Logger;
import org.drugref.util.MiscUtils;

public class LineCursor {
	private static final Logger logger = MiscUtils.getLogger();

	int currentPosition = 0;

	public int getCurrentPosition() {
		return currentPosition;
	}

	public void setCurrentPosition(int currentPosition) {
		this.currentPosition = currentPosition;
	}
	
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