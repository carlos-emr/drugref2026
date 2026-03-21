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
 * Abstract base implementation of the {@link Plugin} interface.
 *
 * <p>Manages plugin metadata (name, version) and capability registration. Subclasses
 * (such as {@link DrugrefPlugin}) configure themselves by calling the setter methods
 * in their constructors to declare their name, version, capabilities, and implementation.</p>
 *
 * @param <T> the type of the underlying plugin implementation
 * @author jackson
 */
public class PluginImpl <T> implements Plugin<T>{
    /** The display name of this plugin. */
    private String name;
    /** The version string of this plugin. */
    private String version;
    /** Map of attribute names to provider metadata, declaring what this plugin can provide. */
    private Hashtable provides;
    /** The underlying implementation instance that services attribute requests. */
    private T plugin;

    /**
     * Default constructor. Initializes the plugin with no name, no version, an empty
     * provides map, and no implementation instance.
     */
    public PluginImpl(){
        this.name=null;
        this.version=null;
        this.provides=new Hashtable();
        //this.plugin=new T();
    }

    /** {@inheritDoc} */
    public void setName (String name){
        this.name=name;
    }

    /** {@inheritDoc} */
    public void setVersion(String version){
        this.version=version;
    }

    /** {@inheritDoc} */
    public void setPlugin(T plugin){
        this.plugin=plugin;
    }

    /** {@inheritDoc} */
    public void setProvides(Hashtable provides){
        this.provides=provides;
    }

    /** {@inheritDoc} */
    public String getName(){
        return this.name;
    }

    /** {@inheritDoc} */
    public String getVersion(){
        return this.version;
    }

    /** {@inheritDoc} */
    public T getPlugin(){
        return this.plugin;
    }

    /** {@inheritDoc} */
    public Hashtable getProvides(){
        return this.provides;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a Vector containing [name, version, provides, plugin] for framework registration.</p>
     */
    public Vector register(){
        Vector vec=new Vector();
        vec.addElement(this.name);
        vec.addElement(this.version);
        vec.addElement(this.provides);
        vec.addElement(this.plugin);
        return vec;
    };

}
