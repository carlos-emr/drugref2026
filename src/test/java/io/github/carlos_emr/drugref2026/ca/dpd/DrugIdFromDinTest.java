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
package io.github.carlos_emr.drugref2026.ca.dpd;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NonUniqueResultException;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * DIN-to-drug-code lookup behind get_drug_by_DIN: an unknown DIN is an empty answer, not a
 * NoResultException logged as a server error (carlos-emr/carlos#5006).
 */
class DrugIdFromDinTest {
    private EntityManagerFactory factory;
    private EntityManager em;

    @BeforeEach void open() {
        factory = new HibernatePersistenceConfiguration("drug-id-from-din-test")
                .managedClass(CdDrugProduct.class)
                .jdbcUrl("jdbc:h2:mem:drug_id_from_din;DB_CLOSE_DELAY=-1")
                .jdbcCredentials("sa", "")
                .property("hibernate.hbm2ddl.auto", "create-drop")
                .createEntityManagerFactory();
        em = factory.createEntityManager();
    }

    @AfterEach void close() {
        if (em != null) em.close();
        if (factory != null) factory.close();
    }

    private void seed(String din, int drugCode) {
        em.getTransaction().begin();
        CdDrugProduct product = new CdDrugProduct();
        product.setDrugIdentificationNumber(din);
        product.setDrugCode(drugCode);
        em.persist(product);
        em.getTransaction().commit();
        em.clear();
    }

    @Test void knownDinReturnsItsDrugCode() {
        seed("02245547", 123);
        assertThat(TablesDao.findDrugIdFromDIN(em, "02245547")).isEqualTo(123);
    }

    @Test void unknownDinIsAnEmptyLookupNotAnException() {
        seed("02245547", 123);
        assertThat(TablesDao.findDrugIdFromDIN(em, "00000000")).isNull();
    }

    @Test void duplicatedDinStaysObservable() {
        seed("02245547", 123);
        seed("02245547", 456);
        assertThatThrownBy(() -> TablesDao.findDrugIdFromDIN(em, "02245547"))
                .isInstanceOf(NonUniqueResultException.class);
    }

    @Test void brokenDatabaseNeverLooksLikeAnUnknownDin() {
        em.close();
        assertThatThrownBy(() -> TablesDao.findDrugIdFromDIN(em, "02245547"))
                .isInstanceOf(IllegalStateException.class);
        em = null;
    }

    @Test void unknownDinGivesNoProductKey() {
        TablesDao dao = new TablesDao() {
            @Override public Integer getDrugIdFromDIN(String din) { return TablesDao.findDrugIdFromDIN(em, din); }
        };
        assertThat(dao.getDrugpKeyFromDIN("00000000")).isZero();
    }
}
