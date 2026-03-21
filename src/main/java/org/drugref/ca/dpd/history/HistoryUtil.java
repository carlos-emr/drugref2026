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
package org.drugref.ca.dpd.history;

import java.util.Calendar;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import org.drugref.ca.dpd.History;
import org.drugref.util.JpaUtils;

/**
 *
 * @author jackson
 */
public class HistoryUtil {
    public  static final String ACTION_UPDATE="update db";
    public boolean addUpdateHistory(){
        EntityManager entityManager = JpaUtils.createEntityManager();
        try {
            EntityTransaction tx = entityManager.getTransaction();
            tx.begin();
        
            History h=new History();
            h.setDateTime(Calendar.getInstance().getTime());
            h.setAction(ACTION_UPDATE);            
            entityManager.persist(h);
            entityManager.flush();
            entityManager.clear();
            tx.commit();

        }
        catch(Exception e){
            e.printStackTrace();
            return false;
        }
        finally{
            JpaUtils.close(entityManager);
        }
        return true;
    }
}
