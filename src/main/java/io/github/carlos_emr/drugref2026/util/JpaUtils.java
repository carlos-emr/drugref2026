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

/**
 * Utility class for JPA {@link EntityManager} lifecycle management.
 *
 * <p>Provides factory methods to create {@link EntityManager} instances from the
 * Spring-managed {@link EntityManagerFactory}, and a safe close method that
 * automatically rolls back any active transaction before closing.
 *
 * <p>This class is used throughout the application to ensure consistent EntityManager
 * handling and to prevent resource leaks or uncommitted transaction issues.
 *
 * @author jackson
 */
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

public class JpaUtils {

    /** The JPA EntityManagerFactory obtained from the Spring application context. */
    private static EntityManagerFactory entityManagerFactory = (EntityManagerFactory) SpringUtils.getBean("entityManagerFactory");

    /**
     * Safely closes an EntityManager, rolling back any active transaction first.
     *
     * <p>This prevents partially committed data from persisting if an error occurred
     * during processing. Always call this in a {@code finally} block.
     *
     * @param entityManager the EntityManager to close; must not be {@code null}
     */
    public static void close(EntityManager entityManager) {
        EntityTransaction tx = entityManager.getTransaction();
        if (tx != null && tx.isActive()) {
            tx.rollback();
        }
        entityManager.close();
    }

    /**
     * Creates a new JPA EntityManager from the Spring-managed EntityManagerFactory.
     *
     * <p>Each call returns a new, independent EntityManager. Callers are responsible
     * for closing it via {@link #close(EntityManager)} when done.
     *
     * @return a new EntityManager instance
     */
    public static EntityManager createEntityManager() {
     //   System.out.println("in createEntityManager()");
        if (entityManagerFactory==null)
            System.out.println("entityManagerFactory is null");

        return (entityManagerFactory.createEntityManager());
    }

   
}

