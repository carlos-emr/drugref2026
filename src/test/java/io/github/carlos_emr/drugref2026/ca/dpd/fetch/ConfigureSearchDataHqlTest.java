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
package io.github.carlos_emr.drugref2026.ca.dpd.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.drugref2026.ca.dpd.CdDrugSearch;

/**
 * Compiles and runs the one HQL statement that killed every database update
 * after the Hibernate 7 upgrade, against the real entity mapping. The failure
 * was a query-compilation {@code SemanticException}, so it reproduces on any
 * database, including this in-memory one.
 */
class ConfigureSearchDataHqlTest {

    private static EntityManagerFactory emf;

    @BeforeAll
    static void openFactory() {
        emf = new HibernatePersistenceConfiguration("drugref-hql-test")
                .managedClass(CdDrugSearch.class)
                .jdbcUrl("jdbc:h2:mem:hqltest;DB_CLOSE_DELAY=-1")
                .jdbcCredentials("sa", "")
                .property("hibernate.hbm2ddl.auto", "create-drop")
                .property("hibernate.show_sql", "false")
                .createEntityManagerFactory();
    }

    @AfterAll
    static void closeFactory() {
        if (emf != null) {
            emf.close();
        }
    }

    @Test
    void shouldAssignOwnIdAsDrugCode_forGenericCategoriesOnly() {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            CdDrugSearch single = search(11, "AMOXICILLIN", null);
            CdDrugSearch composite = search(12, "AMOXICILLIN/ CLAVULANIC ACID", null);
            CdDrugSearch brand = search(13, "CLAVULIN", "48975");
            em.persist(single);
            em.persist(composite);
            em.persist(brand);
            em.flush();
            em.clear();

            int updated = ConfigureSearchData.assignGenericDrugCodes(em);
            em.getTransaction().commit();
            em.clear();

            assertThat(updated).isEqualTo(2);
            assertThat(em.find(CdDrugSearch.class, single.getId()).getDrugCode())
                    .isEqualTo(String.valueOf(single.getId()));
            assertThat(em.find(CdDrugSearch.class, composite.getId()).getDrugCode())
                    .isEqualTo(String.valueOf(composite.getId()));
            assertThat(em.find(CdDrugSearch.class, brand.getId()).getDrugCode()).isEqualTo("48975");
        } finally {
            em.close();
        }
    }

    private static CdDrugSearch search(int category, String name, String drugCode) {
        CdDrugSearch row = new CdDrugSearch();
        row.setCategory(category);
        row.setName(name);
        row.setDrugCode(drugCode);
        return row;
    }
}
