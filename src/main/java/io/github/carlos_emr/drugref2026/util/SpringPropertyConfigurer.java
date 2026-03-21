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
package io.github.carlos_emr.drugref2026.util;

import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * Bridges {@link DrugrefProperties} into Spring's property placeholder resolution system.
 *
 * <p>By extending {@link PropertySourcesPlaceholderConfigurer} and injecting the
 * {@link DrugrefProperties} singleton, this class enables Spring XML configuration
 * files (e.g., {@code spring_config.xml}) to use property placeholders like
 * {@code ${db_url}}, {@code ${db_user}}, and {@code ${db_password}} that resolve
 * to values from the drugref properties file.
 *
 * <p>This bean is typically declared in the Spring application context XML and is
 * instantiated early in the Spring lifecycle to make properties available to other
 * bean definitions.
 */
public class SpringPropertyConfigurer extends PropertySourcesPlaceholderConfigurer {

    /**
     * Constructs the configurer and sets the property source to the
     * {@link DrugrefProperties} singleton instance, making all drugref
     * properties available for {@code ${...}} placeholder resolution in Spring.
     */
    public SpringPropertyConfigurer() {
        //setProperties(ConfigUtils.getProperties());
        //System.out.println(" SpringPropertyConfigurer is called");
        setProperties(DrugrefProperties.getInstance());
    }
}
