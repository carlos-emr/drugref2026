/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * Originally: Copyright (c) 2001-2002. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
package org.drugref.util;

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

public class SpringPropertyConfigurer extends PropertyPlaceholderConfigurer {

    public SpringPropertyConfigurer() {
        //setProperties(ConfigUtils.getProperties());
        //System.out.println(" SpringPropertyConfigurer is called");
        setProperties(DrugrefProperties.getInstance());
    }
}
