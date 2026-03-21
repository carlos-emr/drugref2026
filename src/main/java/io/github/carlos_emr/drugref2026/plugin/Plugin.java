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
package io.github.carlos_emr.drugref2026.plugin;

import java.util.Hashtable;
import java.util.Vector;

/**
 * Interface defining the contract for a drug reference data source plugin.
 *
 * <p>A plugin encapsulates a named, versioned data source that can provide specific
 * drug-related attributes (e.g. interaction data, drug properties). Each plugin declares
 * the set of attributes it can provide via its "provides" map, and holds a reference to
 * the actual implementation object that services those requests.</p>
 *
 * <p>The {@link #register()} method returns a Vector containing the plugin's metadata
 * (name, version, provides map, and implementation) for registration with the
 * {@link DrugrefApi} framework.</p>
 *
 * @param <T> the type of the underlying plugin implementation (e.g. {@link Holbrook})
 * @author jackson
 */
interface Plugin <T> {

    /**
     * Sets the display name of this plugin.
     *
     * @param name the plugin name
     */
    public void setName (String name);

    /**
     * Sets the version string of this plugin.
     *
     * @param version the version identifier (e.g. "1.0")
     */
    public void setVersion(String version);

    /**
     * Sets the capabilities map declaring which attributes this plugin can provide.
     * Keys are attribute names; values describe how to obtain them.
     *
     * @param provides the capabilities hashtable
     */
    public void setProvides(Hashtable provides);

    /**
     * Sets the underlying plugin implementation instance.
     *
     * @param plugin the implementation object that services attribute requests
     */
    public void setPlugin(T plugin);

    /**
     * Returns the display name of this plugin.
     *
     * @return the plugin name
     */
    public String getName();

    /**
     * Returns the version string of this plugin.
     *
     * @return the version identifier
     */
    public String getVersion();

    /**
     * Returns the capabilities map declaring which attributes this plugin provides.
     *
     * @return the provides hashtable mapping attribute names to provider metadata
     */
    public Hashtable getProvides();

    /**
     * Returns the underlying plugin implementation instance.
     *
     * @return the implementation object
     */
    public T getPlugin();

    /**
     * Creates a registration vector containing this plugin's metadata for use by
     * the {@link DrugrefApi} framework.
     *
     * <p>The returned Vector contains, in order: name, version, provides map, plugin instance.</p>
     *
     * @return a Vector of registration data
     */
    public Vector register();


}
