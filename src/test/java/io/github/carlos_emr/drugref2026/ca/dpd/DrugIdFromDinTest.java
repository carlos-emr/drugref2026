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
import io.github.carlos_emr.drugref2026.DinXmlRpcTest;
import org.hibernate.jpa.HibernatePersistenceConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.Hashtable;
import java.util.UUID;
import java.util.Vector;

import static org.assertj.core.api.Assertions.*;

/**
 * DIN lookups with independent keys for products and search rows.
 */
class DrugIdFromDinTest {
    private EntityManagerFactory factory;
    private EntityManager em;

    @BeforeEach void open() {
        factory = catalogue("jdbc:h2:mem:drug_id_from_din;DB_CLOSE_DELAY=-1", "sa", "");
        em = factory.createEntityManager();
    }

    private static EntityManagerFactory catalogue(String url, String user, String password) {
        return new HibernatePersistenceConfiguration("drug-id-from-din-test")
                .managedClass(CdDrugProduct.class)
                .managedClass(CdDrugSearch.class)
                .managedClass(CdActiveIngredients.class)
                .managedClass(CdForm.class)
                .managedClass(CdTherapeuticClass.class)
                .jdbcUrl(url)
                .jdbcCredentials(user, password)
                .property("hibernate.hbm2ddl.auto", "create-drop")
                .createEntityManagerFactory();
    }

    @AfterEach void close() {
        if (em != null) em.close();
        if (factory != null) factory.close();
    }

    private int seed(String din, int drugCode) {
        return seed(em, din, drugCode);
    }

    private static int seed(EntityManager em, String din, int drugCode) {
        em.getTransaction().begin();
        CdDrugProduct product = new CdDrugProduct();
        product.setDrugIdentificationNumber(din);
        product.setDrugCode(drugCode);
        product.setBrandName("DPD BRAND " + drugCode);
        em.persist(product);
        em.getTransaction().commit();
        em.clear();
        return product.getId();
    }

    @Test void knownDinReturnsItsDrugCode() {
        seed("02245547", 123);
        assertThat(TablesDao.findDrugIdFromDIN(em, "02245547")).isEqualTo(123);
    }

    @Test void unknownDinIsAnEmptyLookupNotAnException() {
        seed("02245547", 123);
        assertThat(TablesDao.findDrugIdFromDIN(em, "00000000")).isNull();
        assertThat(TablesDao.getDrugByDIN(em, "00000000")).isEmpty();
    }

    @Test void duplicatedDinStaysObservable() {
        seed("02245547", 123);
        seed("02245547", 456);
        assertThatThrownBy(() -> TablesDao.findDrugIdFromDIN(em, "02245547"))
                .isInstanceOf(NonUniqueResultException.class);
        assertThatThrownBy(() -> TablesDao.getDrugByDIN(em, "02245547"))
                .isInstanceOf(NonUniqueResultException.class);
    }

    @Test void usesTheDpdCodeEvenWhenTheProductIdPointsAtAnotherSearchEntry() throws Exception {
        int productId = seed("02247521", 71771);
        seed("00629367", 6529);

        em.getTransaction().begin();
        int unrelatedSearchId = searchRow(6529, 13, "DIPROLENE 0.05%");
        searchRow(71771, 18, "EZETIMIBE 10MG TABLET");
        int brandSearchId = searchRow(71771, 13, "EZETROL 10MG");
        CdActiveIngredients ingredient = new CdActiveIngredients();
        ingredient.setDrugCode(71771);
        ingredient.setIngredient("EZETIMIBE");
        ingredient.setStrength("10");
        ingredient.setStrengthUnit("MG");
        em.persist(ingredient);
        CdForm form = new CdForm();
        form.setDrugCode(71771);
        form.setPharmaceuticalCdForm("TABLET");
        em.persist(form);
        CdTherapeuticClass atc = new CdTherapeuticClass();
        atc.setDrugCode(71771);
        atc.setTcAtcNumber("C10AX09");
        em.persist(atc);
        em.getTransaction().commit();
        em.clear();

        assertThat(productId).isEqualTo(unrelatedSearchId).isNotEqualTo(brandSearchId);
        Vector<Hashtable<String, Object>> result = TablesDao.getDrugByDIN(em, "02247521");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst())
                .containsEntry("regional_identifier", "02247521")
                .containsEntry("product", "EZETROL 10MG")
                .containsEntry("name", "EZETIMIBE")
                .containsEntry("atc", "C10AX09")
                .containsEntry("drugForm", "TABLET");
        Vector<?> components = (Vector<?>) result.getFirst().get("components");
        assertThat(components).hasSize(1);
        var component = (Hashtable<?, ?>) components.getFirst();
        assertThat(component.get("strength")).isEqualTo(10f);
        assertThat(component.get("unit")).isEqualTo("MG");

        var catalogue = new TablesDao() {
            @Override
            public Vector<Hashtable<String, Object>> getDrugByDIN(String din) {
                return TablesDao.getDrugByDIN(em, din);
            }
        };
        Vector<?> reply = (Vector<?>) DinXmlRpcTest.lookupEzetrol(catalogue);
        assertThat(reply).hasSize(1);
        var wireProduct = (Hashtable<?, ?>) reply.getFirst();
        assertThat(wireProduct.get("regional_identifier")).isEqualTo("02247521");
        assertThat(wireProduct.get("product")).isEqualTo("EZETROL 10MG");
        var wireComponents = (Vector<?>) wireProduct.get("components");
        assertThat(((Hashtable<?, ?>) wireComponents.getFirst()).get("strength")).isEqualTo("10.0");
    }

    @Test void readsTheExactProductWithoutABrandSearchIndexEntry() {
        seed("02247521", 71771);
        assertThat(TablesDao.getDrugByDIN(em, "02247521")).singleElement().satisfies(drug -> {
            assertThat(drug).containsEntry("regional_identifier", "02247521")
                    .containsEntry("product", "DPD BRAND 71771");
        });
    }

    @Test void rejectsAProductCodeSharedByDifferentDins() {
        seed("02247521", 71771);
        seed("02247522", 71771);
        assertThatThrownBy(() -> TablesDao.getDrugByDIN(em, "02247521"))
                .isInstanceOf(NonUniqueResultException.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DRUGREF_TEST_MARIADB_URL", matches = ".+")
    void distinguishesExactAndPaddedDinsOnMariaDb() throws Exception {
        String base = System.getenv("DRUGREF_TEST_MARIADB_URL");
        String user = System.getenv("DRUGREF_TEST_MARIADB_USER");
        String password = System.getenv("DRUGREF_TEST_MARIADB_PASSWORD");
        String schema = "drugref_din_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base, user, password);
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            try (var mariaFactory = catalogue(base + schema, user, password);
                    var maria = mariaFactory.createEntityManager()) {
                seed(maria, "02247521 ", 111);
                seed(maria, "02247521  ", 222);
                seed(maria, "02247521", 71771);
                seed(maria, "02247522 ", 333);

                assertThat(TablesDao.getDrugByDIN(maria, "02247521")).singleElement().satisfies(drug ->
                        assertThat(drug).containsEntry("product", "DPD BRAND 71771"));
                assertThat(TablesDao.getDrugByDIN(maria, "02247522")).isEmpty();
                assertThat(TablesDao.findDrugIdFromDIN(maria, "02247522")).isEqualTo(333);
                assertThatThrownBy(() -> TablesDao.findDrugIdFromDIN(maria, "02247521"))
                        .isInstanceOf(NonUniqueResultException.class);
            } finally {
                statement.execute("DROP DATABASE " + schema);
            }
        }
    }

    private int searchRow(int code, int category, String name) {
        CdDrugSearch row = new CdDrugSearch();
        row.setDrugCode(Integer.toString(code));
        row.setCategory(category);
        row.setName(name);
        em.persist(row);
        return row.getId();
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
