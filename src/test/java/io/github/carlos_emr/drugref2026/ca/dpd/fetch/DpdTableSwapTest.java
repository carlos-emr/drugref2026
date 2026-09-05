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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The backup/restore contract that makes a failed update harmless: the previous
 * rows must be exactly recoverable after the live tables were rebuilt and abandoned.
 * Runs on H2 with case-sensitive lower-case identifiers to match MariaDB's names.
 */
class DpdTableSwapTest {

    private static final String URL = "jdbc:h2:mem:swaptest;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE";

    private DpdTableSwap swap;
    private Connection con;

    @BeforeEach
    void setUp() throws SQLException {
        con = DriverManager.getConnection(URL, "sa", "");
        try (Statement st = con.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(200))");
            st.execute("INSERT INTO cd_drug_product VALUES (1, 'OLD-AMOXICILLIN')");
            st.execute("CREATE TABLE cd_drug_search (id int primary key, name varchar(200))");
            st.execute("INSERT INTO cd_drug_search VALUES (1, 'OLD-AMOXICILLIN 500MG')");
            st.execute("CREATE TABLE history (id int primary key, action varchar(20))");
            st.execute("INSERT INTO history VALUES (1, 'update db')");
        }
        swap = new DpdTableSwap(URL, "sa", "");
    }

    @AfterEach
    void tearDown() throws SQLException {
        con.close();
    }

    @Test
    void shouldRestorePreviousRows_afterFailedRebuild() throws SQLException {
        swap.backupLiveTables();
        assertThat(exists("cd_drug_product")).isFalse();
        assertThat(exists("cd_drug_product_prev")).isTrue();
        assertThat(swap.listBackupTables()).containsExactly("cd_drug_product_prev", "cd_drug_search_prev");
        // the importer's fresh, partially filled tables
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(200))");
            st.execute("CREATE TABLE cd_drug_search (id int primary key, name varchar(200))");
            st.execute("CREATE TABLE cd_form (id int primary key)");
        }

        int restored = swap.restoreBackupTables();

        assertThat(restored).isEqualTo(2);
        assertThat(swap.listBackupTables()).isEmpty();
        assertThat(exists("cd_drug_product_prev")).isFalse();
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("OLD-AMOXICILLIN");
        assertThat(scalar("SELECT name FROM cd_drug_search")).isEqualTo("OLD-AMOXICILLIN 500MG");
        // untouched by the swap
        assertThat(scalar("SELECT action FROM history")).isEqualTo("update db");
        // a new table that had no previous version is simply left in place
        assertThat(exists("cd_form")).isTrue();
    }

    @Test
    void shouldDiscardBackup_afterSuccessfulRebuild() throws SQLException {
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(200))");
            st.execute("INSERT INTO cd_drug_product VALUES (1, 'NEW-AMOXICILLIN')");
        }

        swap.discardBackupTables();

        assertThat(swap.listBackupTables()).isEmpty();
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("NEW-AMOXICILLIN");
    }

    @Test
    void shouldReplaceStaleBackup_whenBackingUpAgain() throws SQLException {
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(200))");
            st.execute("INSERT INTO cd_drug_product VALUES (1, 'SECOND')");
        }

        // a second attempt while a stale *_prev set is still around
        swap.backupLiveTables();

        assertThat(scalar("SELECT brand_name FROM cd_drug_product_prev")).isEqualTo("SECOND");
        assertThat(exists("cd_drug_product")).isFalse();
    }

    @Test
    void shouldDoNothing_whenNoBackupPresent() throws SQLException {
        assertThat(swap.restoreBackupTables()).isZero();
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("OLD-AMOXICILLIN");
    }

    private boolean exists(String table) throws SQLException {
        return DpdTableSwap.tableExists(con, table);
    }

    private String scalar(String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
