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

/**
 *
 * @author jackson
 */
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

public class JpaUtils {

    private static EntityManagerFactory entityManagerFactory = (EntityManagerFactory) SpringUtils.getBean("entityManagerFactory");
    /**
     * This method will close the entity manager.
     * Any active transaction will be rolled back.
     */
    public static void close(EntityManager entityManager) {
        EntityTransaction tx = entityManager.getTransaction();
        if (tx != null && tx.isActive()) {
            tx.rollback();
        }
        entityManager.close();
    }

    public static EntityManager createEntityManager() {
     //   System.out.println("in createEntityManager()");
        if (entityManagerFactory==null)
            System.out.println("entityManagerFactory is null");

        return (entityManagerFactory.createEntityManager());
    }

   
}

