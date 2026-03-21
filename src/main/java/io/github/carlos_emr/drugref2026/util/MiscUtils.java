/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * Originally: Copyright (c) 2001-2002. McMaster University Hamilton Ontario. All Rights Reserved.
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
package io.github.carlos_emr.drugref2026.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Collection of static utility methods used across the drugref2026 application.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link #getLogger()} -- auto-detecting logger factory using stack trace introspection</li>
 *   <li>{@link #toStringArrayList(List)} / {@link #toIntegerArrayList(List)} -- type conversion helpers</li>
 *   <li>{@link #isStringToInteger(String)} -- validation that a string represents a pure integer</li>
 * </ul>
 *
 * @author jackson
 */
public class MiscUtils {

    /**
     * Converts a List of arbitrary objects to an ArrayList of their String representations.
     *
     * @param intList the source list (elements are converted via {@code toString()})
     * @return a new ArrayList containing String representations of each element
     */
    public static ArrayList toStringArrayList(List intList) {
        ArrayList<String> strArrayList = new ArrayList<String>();

        for (int i = 0; i < intList.size(); i++) {
            String element = intList.get(i).toString();
            strArrayList.add(element);
        }
        return strArrayList;
    }

    /**
     * Converts a List of objects (whose {@code toString()} yields numeric strings)
     * to an ArrayList of Integers.
     *
     * @param strList the source list (elements are parsed via {@code Integer.parseInt()})
     * @return a new ArrayList of Integer values
     * @throws NumberFormatException if any element cannot be parsed as an integer
     */
    public static ArrayList toIntegerArrayList(List strList) {
        ArrayList<Integer> intArrayList = new ArrayList<Integer>();

        for (int i = 0; i < strList.size(); i++) {
            Integer element = Integer.parseInt(strList.get(i).toString());
            //System.out.println("in  toIntegerArrayList: "+element);
            intArrayList.add(element);
        }
        return intArrayList;
    }

    /**
     * Returns a Logger named after the calling class, using stack trace introspection.
     *
     * <p>Inspects the current thread's stack trace at depth 2 (the caller of this method)
     * to automatically determine the correct class name for the logger. This eliminates
     * the need to pass a class literal when obtaining a logger.
     *
     * @return a Log4j2 Logger instance named after the calling class
     */
    public static Logger getLogger(){
    		// Stack depth: [0]=getStackTrace, [1]=getLogger, [2]=calling class
    		StackTraceElement[] ste = Thread.currentThread().getStackTrace();
    		String caller = ste[2].getClassName();
    		return LogManager.getLogger(caller);
    }

    /**
     * Checks whether a string represents a valid integer (digits only, no other characters).
     *
     * <p>Uses regex matching to find a digit sequence, then verifies no non-digit
     * characters remain after removing the matched number. This rejects strings like
     * "123abc" or "12.34" while accepting "42" or "007".
     *
     * @param s the string to validate
     * @return {@code true} if the string is a pure integer representation, {@code false} if null or invalid
     */
    public static boolean isStringToInteger(String s){
        if(s==null) return false;

        boolean retBool=false;
        Pattern p1=Pattern.compile("\\d+");
        Matcher m1=p1.matcher(s);
        if(m1.find()){
            String numStr=s.substring(m1.start(), m1.end());
            String restStr=s.replace(numStr, "").trim();
            if(restStr!=null&&restStr.length()>0)
                retBool=false;
            else
                retBool=true;
        }else
            retBool=false;

        return retBool;

    }


}
