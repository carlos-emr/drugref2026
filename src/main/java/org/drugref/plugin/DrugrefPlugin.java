/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
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
package org.drugref.plugin;

/**
 *
 * @author jackson
 */

public class DrugrefPlugin extends PluginImpl {
        public DrugrefPlugin() {
            this.setName("Holbrook Drug Interactions");
            this.setVersion("1.0");
            Holbrook hb=new Holbrook();
            this.setPlugin(hb);
            this.setProvides(hb.listCapabilities());

        }
    }