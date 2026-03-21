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

import java.util.Hashtable;
import java.util.Vector;

/**
 *
 * @author jackson
 */
public class PluginImpl <T> implements Plugin<T>{
    private String name;
    private String version;
    private Hashtable provides;
    private T plugin;

    public PluginImpl(){
        this.name=null;
        this.version=null;
        this.provides=new Hashtable();
        //this.plugin=new T();
    }
    public void setName (String name){
        this.name=name;
    }
    public void setVersion(String version){
        this.version=version;
    }
    public void setPlugin(T plugin){
        this.plugin=plugin;
    }
    public void setProvides(Hashtable provides){
        this.provides=provides;
    }
    public String getName(){
        return this.name;
    }
    public String getVersion(){
        return this.version;
    }
    public T getPlugin(){
        return this.plugin;
    }
    public Hashtable getProvides(){
        return this.provides;
    }

    public Vector register(){
        Vector vec=new Vector();
        vec.addElement(this.name);
        vec.addElement(this.version);
        vec.addElement(this.provides);
        vec.addElement(this.plugin);
        return vec;
    };

}
