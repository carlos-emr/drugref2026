/*
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 *
 * Originally: Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.carlos_emr.drugref2026.util.XmlRpcUtils;
import static org.assertj.core.api.Assertions.*;

class InactiveDateTest {
    private EntityManagerFactory factory;
    private EntityManager em;

    @BeforeEach void open() {
        factory = new HibernatePersistenceConfiguration("inactive-date-test")
                .managedClass(CdInactiveProducts.class)
                .jdbcUrl("jdbc:h2:mem:inactive_dates;DB_CLOSE_DELAY=-1")
                .jdbcCredentials("sa", "")
                .property("hibernate.hbm2ddl.auto", "create-drop")
                .createEntityManagerFactory();
        em = factory.createEntityManager();
    }

    @AfterEach void close() {
        if (em != null) em.close();
        if (factory != null) factory.close();
    }

    private void seed(java.util.Date date) {
        em.getTransaction().begin();
        CdInactiveProducts product = new CdInactiveProducts();
        product.setDrugIdentificationNumber("02245547");
        product.setHistoryDate(date);
        em.persist(product);
        em.getTransaction().commit();
        em.clear();
    }

    @Test void databaseDateSurvivesLookupAndXmlRpcSerialization() throws Exception {
        seed(java.sql.Date.valueOf("2018-07-24"));
        var dates = TablesDao.findInactiveDates(em, "02245547");
        assertThat(dates).hasSize(1);
        assertThat(XmlRpcUtils.buildResponse(dates)).contains("20180724T00:00:00");
    }

    @Test void noMatchReturnsEmptyVector() {
        assertThat(TablesDao.findInactiveDates(em, "00000000")).isEmpty();
    }

    @Test void brokenDatabaseNeverLooksLikeNoInactiveProduct() {
        em.close();
        assertThatThrownBy(() -> TablesDao.findInactiveDates(em, "02245547"))
                .isInstanceOf(IllegalStateException.class);
        em = null;
    }

    @Test void incompleteReferenceDataNeverLooksLikeNoInactiveProduct() {
        seed(null);
        assertThatThrownBy(() -> TablesDao.findInactiveDates(em, "02245547"))
                .isInstanceOf(IllegalStateException.class);
    }
}
