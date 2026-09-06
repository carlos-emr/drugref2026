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
    void shouldKeepTheRealPreviousDataset_whenRetryingAnAbandonedAttempt() throws SQLException {
        // First attempt parks the previous dataset and then dies mid-import, leaving
        // partially imported live tables behind.
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(200))");
            st.execute("INSERT INTO cd_drug_product VALUES (1, 'HALF-IMPORTED')");
        }

        // The retry must settle that first: an earlier revision dropped the *_prev set
        // as stale and promoted the half-imported tables in its place, which threw away
        // the only remaining copy of the real dataset.
        swap.backupLiveTables();

        assertThat(scalar("SELECT brand_name FROM cd_drug_product_prev")).isEqualTo("OLD-AMOXICILLIN");
        assertThat(scalar("SELECT name FROM cd_drug_search_prev")).isEqualTo("OLD-AMOXICILLIN 500MG");
        assertThat(exists("cd_drug_product")).isFalse();
        assertThat(swap.readState()).isEqualTo(DpdTableSwap.STATE_BACKUP);
    }

    @Test
    void shouldRestorePreviousRows_whenBackupWasInterruptedPartway() throws SQLException {
        // backupLiveTables() renames one table at a time. Simulate a failure after the
        // first rename: half the dataset is parked, the state marker says BACKUP.
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE " + DpdTableSwap.STATE_TABLE + " (state varchar(16) NOT NULL)");
            st.execute("INSERT INTO " + DpdTableSwap.STATE_TABLE + " VALUES ('"
                    + DpdTableSwap.STATE_BACKUP + "')");
            st.execute("ALTER TABLE cd_drug_product RENAME TO cd_drug_product_prev");
        }

        String action = swap.recoverInterruptedSwap();

        assertThat(action).contains("restored");
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("OLD-AMOXICILLIN");
        assertThat(scalar("SELECT name FROM cd_drug_search")).isEqualTo("OLD-AMOXICILLIN 500MG");
        assertThat(swap.readState()).isNull();
    }

    @Test
    void shouldFinishTheDiscard_whenCleanupWasInterruptedPartway() throws SQLException {
        // The import was accepted and discardBackupTables() died partway: some *_prev
        // are already gone, the rest are stale copies of data the import replaced.
        // Restoring them would splice an old dataset into a new one.
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(200))");
            st.execute("INSERT INTO cd_drug_product VALUES (1, 'NEW-AMOXICILLIN')");
            st.execute("CREATE TABLE cd_drug_search (id int primary key, name varchar(200))");
            st.execute("INSERT INTO cd_drug_search VALUES (1, 'NEW-AMOXICILLIN 500MG')");
            st.execute("DELETE FROM " + DpdTableSwap.STATE_TABLE);
            st.execute("INSERT INTO " + DpdTableSwap.STATE_TABLE + " VALUES ('"
                    + DpdTableSwap.STATE_DISCARD + "')");
            st.execute("DROP TABLE cd_drug_search_prev");
        }

        String action = swap.recoverInterruptedSwap();

        assertThat(action).contains("discarding");
        assertThat(swap.listBackupTables()).isEmpty();
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("NEW-AMOXICILLIN");
        assertThat(scalar("SELECT name FROM cd_drug_search")).isEqualTo("NEW-AMOXICILLIN 500MG");
        assertThat(swap.readState()).isNull();
    }

    @Test
    void shouldSettleAnUnresolvedSwap_beforeStartingANewOne() throws SQLException {
        // A *_prev set left by an interrupted backup is the only copy of the data.
        // Starting a fresh swap must restore it first, never drop it as stale.
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE " + DpdTableSwap.STATE_TABLE + " (state varchar(16) NOT NULL)");
            st.execute("INSERT INTO " + DpdTableSwap.STATE_TABLE + " VALUES ('"
                    + DpdTableSwap.STATE_BACKUP + "')");
            st.execute("DROP TABLE cd_drug_product");
            st.execute("CREATE TABLE cd_drug_product_prev (id int primary key, brand_name varchar(200))");
            st.execute("INSERT INTO cd_drug_product_prev VALUES (1, 'ONLY-COPY')");
        }

        swap.backupLiveTables();

        // The only copy survived: recovered, then parked again by the new swap.
        assertThat(scalar("SELECT brand_name FROM cd_drug_product_prev")).isEqualTo("ONLY-COPY");
        assertThat(swap.readState()).isEqualTo(DpdTableSwap.STATE_BACKUP);
    }

    @Test
    void shouldRecordAndClearState_aroundASuccessfulSwap() throws SQLException {
        assertThat(swap.readState()).isNull();

        swap.backupLiveTables();
        assertThat(swap.readState()).isEqualTo(DpdTableSwap.STATE_BACKUP);

        swap.discardBackupTables();
        assertThat(swap.readState()).isNull();
    }

    @Test
    void shouldDoNothing_whenNoBackupPresent() throws SQLException {
        assertThat(swap.restoreBackupTables()).isZero();
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("OLD-AMOXICILLIN");
        assertThat(swap.recoverInterruptedSwap()).isEqualTo("nothing to recover");
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
