/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package co.rsk.util;

public class NumberUtils {

	/**
	 * respond if a string has only number optionally prefixed with a minus sign 
	 * 
	 */
    public static boolean isNumber(final String str) {
        
    	int beginIdx = 0;
    	
        if (str.charAt(0) == '-') {
            if (str.length() == 1) {
                return false;
            }
            beginIdx = 1;
        }
    	
        for (int i = beginIdx; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        
        return true;
    }

	
}
