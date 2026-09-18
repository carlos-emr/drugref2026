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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.carlos_emr.drugref2026.util.XmlRpcUtils;
import static org.assertj.core.api.Assertions.*;

class InactiveDateTest {
    private EntityManagerFactory factory;
    private EntityManager em;

    @BeforeEach void open() {
        factory = new HibernatePersistenceConfiguration("inactive-date-test")
                .managedClass(CdInactiveProducts.class)
                .managedClass(CdDrugSearch.class)
                .managedClass(CdDrugProduct.class)
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
    private TablesDao searchDao() {
        return new TablesDao() {
            @Override public java.util.List<Integer> getInactiveDrugs() { return java.util.List.of(-1); }
            @Override public String getFirstDinInAIGroup(String group) { return "02245547"; }
            @Override public java.util.Vector getInactiveDate(String din) { return TablesDao.findInactiveDates(em, din); }
        };
    }

    private void seedSearch() {
        em.getTransaction().begin();
        for (int category : new int[] {13, 18, 19}) {
            CdDrugSearch row = new CdDrugSearch();
            row.setName("TEST " + category);
            row.setCategory(category);
            row.setDrugCode("123");
            em.persist(row);
        }
        em.getTransaction().commit();
        em.clear();
    }

    @Test void completeSearchPreservesEveryResult() {
        seedSearch();
        seed(java.sql.Date.valueOf("2018-07-24"));
        assertThat(searchDao().listSearchElement4("TEST", true, em)).hasSize(3);
    }

    @Test void inactiveLookupFailureCannotReturnPartialSearch() {
        seedSearch();
        seed(null);
        assertThatThrownBy(() -> searchDao().listSearchElement4("TEST", true, em))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void failedSearchQueryCannotReturnNoMatches() {
        TablesDao dao = searchDao();
        em.close();
        assertThatThrownBy(() -> dao.listSearchElement4("TEST", true, em))
                .isInstanceOf(IllegalStateException.class);
        em = null;
    }

    @Test void emptyInactiveListIsAValidSuccessfulResult() {
        assertThat(TablesDao.findInactiveDrugCodes(em)).isEmpty();
    }

    @Test void inactiveCodeLookupReturnsStoredCodes() {
        em.getTransaction().begin();
        CdInactiveProducts product = new CdInactiveProducts();
        product.setDrugCode(123);
        em.persist(product);
        em.getTransaction().commit();
        assertThat(TablesDao.findInactiveDrugCodes(em)).containsExactly(123);
    }

    @Test void missingAiGroupIsAValidSuccessfulResult() {
        assertThat(TablesDao.findFirstDinInAiGroup(em, "missing")).isNull();
    }

    @Test void aiGroupLookupKeepsOldestMatchingProduct() {
        em.getTransaction().begin();
        for (int year : new int[] {2020, 2018}) {
            CdDrugProduct product = new CdDrugProduct();
            product.setAiGroupNo("123");
            product.setDrugIdentificationNumber("DIN" + year);
            product.setLastUpdateDate(java.sql.Date.valueOf(year + "-01-01"));
            em.persist(product);
        }
        em.getTransaction().commit();
        assertThat(TablesDao.findFirstDinInAiGroup(em, "123")).isEqualTo("DIN2018");
    }

    @Test void failedInactiveCodeLookupCannotBecomeAnEmptyCache() {
        seedSearch();
        EntityManager failed = factory.createEntityManager();
        failed.close();
        java.util.concurrent.atomic.AtomicReference<EntityManager> lookup = new java.util.concurrent.atomic.AtomicReference<>(failed);
        TablesDao dao = new TablesDao() {
            @Override public java.util.List<Integer> getInactiveDrugs() {
                return TablesDao.findInactiveDrugCodes(lookup.get());
            }
            @Override public String getFirstDinInAIGroup(String group) { return null; }
        };
        assertThatThrownBy(() -> dao.listSearchElement4("TEST", true, em))
                .isInstanceOf(IllegalStateException.class);
        lookup.set(em);
        assertThat(dao.listSearchElement4("TEST", true, em)).hasSize(3);
    }

    @Test void failedAiGroupLookupCannotReturnAnActiveOrPartialSearch() {
        seedSearch();
        EntityManager failed = factory.createEntityManager();
        failed.close();
        java.util.concurrent.atomic.AtomicReference<EntityManager> lookup = new java.util.concurrent.atomic.AtomicReference<>(failed);
        TablesDao dao = new TablesDao() {
            @Override public java.util.List<Integer> getInactiveDrugs() { return java.util.List.of(-1); }
            @Override public String getFirstDinInAIGroup(String group) {
                return TablesDao.findFirstDinInAiGroup(lookup.get(), group);
            }
        };
        assertThatThrownBy(() -> dao.listSearchElement4("TEST", true, em))
                .isInstanceOf(IllegalStateException.class);
        lookup.set(em);
        assertThat(dao.listSearchElement4("TEST", true, em)).hasSize(3);
    }

    private void seedExpandedSearch(int category) {
        em.getTransaction().begin();
        CdDrugSearch row = new CdDrugSearch();
        row.setName("TEST EXTRA DOSE");
        row.setCategory(category);
        row.setDrugCode("123+456");
        em.persist(row);
        em.getTransaction().commit();
        em.clear();
    }

    @ParameterizedTest
    @ValueSource(ints = {18, 19})
    void expandedGenericSearchChecksInactiveStatus(int category) {
        seedExpandedSearch(category);
        seed(java.sql.Date.valueOf("2018-07-24"));
        var results = searchDao().listSearchElement4("TEST DOSE", true, em);
        assertThat(results).hasSize(1);
        assertThat(((java.util.Hashtable<?, ?>) results.get(0)).get("isInactive")).isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(ints = {18, 19})
    void failedExpandedAiGroupLookupCannotReturnActiveResults(int category) {
        seedExpandedSearch(category);
        EntityManager failed = factory.createEntityManager();
        failed.close();
        TablesDao dao = new TablesDao() {
            @Override public java.util.List<Integer> getInactiveDrugs() { return java.util.List.of(-1); }
            @Override public String getFirstDinInAIGroup(String group) {
                return TablesDao.findFirstDinInAiGroup(failed, group);
            }
        };
        assertThatThrownBy(() -> dao.listSearchElement4("TEST DOSE", true, em))
                .isInstanceOf(IllegalStateException.class);
    }

}
