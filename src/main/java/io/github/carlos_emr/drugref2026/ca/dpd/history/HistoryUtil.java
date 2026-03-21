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
package io.github.carlos_emr.drugref2026.ca.dpd.history;

import java.util.Calendar;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import io.github.carlos_emr.drugref2026.ca.dpd.History;
import io.github.carlos_emr.drugref2026.util.JpaUtils;

/**
 * Utility class for recording database update events in the {@link History} table.
 *
 * <p>After a successful DPD import or data refresh, this class is used to record a timestamped
 * entry indicating when the database was last updated. This allows the application and its
 * users to know how current the drug data is.</p>
 *
 * @author jackson
 */
public class HistoryUtil {
    /** The action string recorded for a database update event. */
    public  static final String ACTION_UPDATE="update db";

    /**
     * Records a database update event with the current timestamp.
     *
     * <p>Creates a new {@link History} entry with the current date/time and the
     * {@link #ACTION_UPDATE} action string, and persists it to the database.</p>
     *
     * @return {@code true} if the history record was successfully persisted,
     *         {@code false} if an exception occurred
     */
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
