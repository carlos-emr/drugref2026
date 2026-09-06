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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        swap.markDiscarding();
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
            st.execute("UPDATE " + DpdTableSwap.STATE_TABLE + " SET state = '"
                    + DpdTableSwap.STATE_DISCARD + "'");
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

        swap.markDiscarding();
        assertThat(swap.readState()).isEqualTo(DpdTableSwap.STATE_DISCARD);

        swap.discardBackupTables();
        assertThat(swap.readState()).isNull();
    }


    @Test
    void shouldKeepTheNewDataset_whenTheProcessDiesRightAfterTheCommitMarker() throws SQLException {
        // The worker moves the marker to DISCARD and only then writes the history row and
        // drops the backups. This pins the window in between: a crash there must keep the
        // NEW data, because the marker — not the history row — is what commits an update.
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(200))");
            st.execute("INSERT INTO cd_drug_product VALUES (1, 'NEW-AMOXICILLIN')");
            st.execute("CREATE TABLE cd_drug_search (id int primary key, name varchar(200))");
            st.execute("INSERT INTO cd_drug_search VALUES (1, 'NEW-AMOXICILLIN 500MG')");
        }
        swap.markDiscarding();

        // ...process dies here; next start:
        String action = swap.recoverInterruptedSwap();

        assertThat(action).contains("discarding");
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("NEW-AMOXICILLIN");
        assertThat(swap.listBackupTables()).isEmpty();
        assertThat(swap.readState()).isNull();
    }

    @Test
    void shouldHoldExactlyOneMarkerRow_afterMovingToDiscard() throws SQLException {
        // Renamed from shouldNeverLeaveAnEmptyMarker_whenMovingToDiscard, which promised more
        // than it delivers. "Never leaves an empty marker" is a statement about the window
        // *inside* markDiscarding(), and this test only looks at the state after it returns —
        // which a DELETE + INSERT implementation satisfies identically. The crash-window
        // property comes from the transition being a single UPDATE, which is structural and
        // reviewable in the source, not observable from JDBC without instrumenting the driver.
        //
        // Why it matters, and why the single UPDATE is there: an earlier revision wrote every
        // transition as DELETE + INSERT on an autocommit connection, so a crash between them
        // left the marker present but EMPTY — which the recovery pass read as "no marker" and,
        // with *_prev tables still around, resolved as an interrupted backup, restoring stale
        // data over a committed dataset. What this test does pin is the post-condition that
        // makes that resolvable at all: exactly one row, holding DISCARD.
        swap.backupLiveTables();

        swap.markDiscarding();

        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM " + DpdTableSwap.STATE_TABLE)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("exactly one marker row at all times").isEqualTo(1);
        }
        assertThat(swap.readState()).isEqualTo(DpdTableSwap.STATE_DISCARD);
    }

    @Test
    void shouldRecordTheCommit_whenNoBackupSetExists() throws SQLException {
        // An update over an empty DPD schema has nothing to move aside, so no marker was
        // opened. markDiscarding() must still record the commit rather than no-op.
        swap.markDiscarding();

        assertThat(swap.readState()).isEqualTo(DpdTableSwap.STATE_DISCARD);
        assertThat(swap.recoverInterruptedSwap()).contains("discarding");
        assertThat(swap.readState()).isNull();
    }

    @Test
    void shouldRefuseToGuess_whenTheMarkerTableIsEmpty() throws SQLException {
        // Unreachable from this class (the row is written with the CREATE, and the only
        // transition is a single UPDATE), so an empty marker means something outside
        // truncated it. Restoring would revert a committed update and discarding would
        // destroy the previous dataset, so neither may be chosen silently.
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("DELETE FROM " + DpdTableSwap.STATE_TABLE);
        }

        assertThatThrownBy(() -> swap.recoverInterruptedSwap())
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("exists but is empty");
    }

    @Test
    void shouldClearTheMarker_whenItIsEmptyAndNothingWasMovedAside() throws SQLException {
        // The crash openState() can actually leave: DDL commits on its own, so a process
        // killed between the CREATE and the INSERT leaves the marker table present and
        // empty -- but before any rename, so no dataset is ambiguous. This used to be
        // permanent: backupLiveTables() settles an unfinished swap first, so it read the
        // marker, threw, and every later update threw the same way.
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE " + DpdTableSwap.STATE_TABLE + " (state varchar(16) NOT NULL)");
        }

        assertThat(swap.readState()).isNull();
        assertThat(exists(DpdTableSwap.STATE_TABLE)).isFalse();
    }

    @Test
    void shouldStillRunTheNextUpdate_afterAnEmptyMarkerWasLeftBehind() throws SQLException {
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE " + DpdTableSwap.STATE_TABLE + " (state varchar(16) NOT NULL)");
        }

        swap.backupLiveTables();

        assertThat(exists("cd_drug_product_prev")).isTrue();
        assertThat(swap.readState()).isEqualTo(DpdTableSwap.STATE_BACKUP);
        // Two, not three: `history` is deliberately outside the swap set, because it is
        // what getLastUpdateTime() reads and must survive a rolled-back update.
        assertThat(swap.restoreBackupTables()).isEqualTo(2);
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("OLD-AMOXICILLIN");
    }

    @Test
    void shouldRefuseToStart_whenTheMarkerIsUndecidable() throws SQLException {
        // The guard the class documents ("refusing to start") did not fire. readState() threw a
        // plain SQLException for the undecidable marker and restoreIfInterrupted() caught it in
        // the same block as "could not reach the database", logged, and returned — so the
        // context deployed and served whatever was in the live tables. Found by running the
        // repair matrix against MariaDB; every unit test passed throughout.
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("DELETE FROM " + DpdTableSwap.STATE_TABLE);
        }

        assertThatThrownBy(() -> DpdTableSwap.restoreIfInterrupted(swap))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unreadable")
                .hasCauseInstanceOf(DpdTableSwap.UndecidableSwapState.class);
    }

    @Test
    void shouldNotRefuseToStart_whenTheDatabaseIsSimplyUnreachable() throws SQLException {
        // The other half of the distinction, and why this needs a type rather than a message
        // check: an unreachable database must NOT take the context down here. Hibernate's own
        // schema validation reports it a moment later with a better message, and a startup
        // repair that cannot run is not evidence the data is bad.
        DpdTableSwap unreachable = new DpdTableSwap("jdbc:h2:mem:nosuchdb;IFEXISTS=TRUE", "sa", "");

        assertThatCode(() -> DpdTableSwap.restoreIfInterrupted(unreachable))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRepairAndReturn_whenTheSwapIsResolvable() throws SQLException {
        // And the ordinary path still repairs rather than refusing.
        swap.backupLiveTables();
        // backupLiveTables() renames the live table away, so this stands in for the half-built
        // one an abandoned import leaves behind under the live name.
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(200))");
            st.execute("INSERT INTO cd_drug_product VALUES (99, 'HALF-BUILT')");
        }

        DpdTableSwap.restoreIfInterrupted(swap);

        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("OLD-AMOXICILLIN");
        assertThat(exists(DpdTableSwap.STATE_TABLE)).isFalse();
    }

    @Test
    void shouldEmptyAnImportCreatedTable_whenTheBackupProvablyCompleted() throws SQLException {
        // The fixture has no cd_companies, so backupLiveTables() has nothing to move for it
        // and records that fact. The abandoned import then creates it. On restore that table
        // has no *_prev, and the recorded set proves the backup finished without it -- so it
        // did not exist before, its rows belong to the abandoned run, and it is emptied. Not
        // dropped: Hibernate validates the schema against it at startup.
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE cd_companies (id int primary key, name varchar(200))");
            st.execute("INSERT INTO cd_companies VALUES (1, 'FROM-THE-ABANDONED-RUN')");
        }

        assertThat(swap.restoreBackupTables()).isEqualTo(2);

        assertThat(exists("cd_companies")).as("kept for schema validation").isTrue();
        assertThat(scalarCount("cd_companies")).as("rows from the abandoned run").isZero();
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("OLD-AMOXICILLIN");
    }

    @Test
    void shouldLeaveAnOrphanAlone_whenTheRenameLoopNeverCompleted() throws SQLException {
        // A NULL moved column is what a crash partway through the rename loop leaves, and
        // then an orphan may be a table the loop never reached, still holding the ORIGINAL
        // rows. Emptying it here would destroy real data, so nothing is decided.
        swap.backupLiveTables();
        try (Statement st = con.createStatement()) {
            st.execute("UPDATE " + DpdTableSwap.STATE_TABLE + " SET " + DpdTableSwap.MOVED_COLUMN + " = NULL");
            st.execute("CREATE TABLE cd_companies (id int primary key, name varchar(200))");
            st.execute("INSERT INTO cd_companies VALUES (1, 'ORIGINAL-NEVER-MOVED')");
        }

        swap.restoreBackupTables();

        assertThat(scalar("SELECT name FROM cd_companies")).isEqualTo("ORIGINAL-NEVER-MOVED");
    }

    @Test
    void shouldTreatAMarkerFromAnOlderBuild_asUnknown() throws SQLException {
        // A build before the column existed can leave a BACKUP marker mid-swap. Its loop's
        // fate is unknowable, so restore must take the conservative path, and must do so by
        // reading the metadata -- not by catching the failed SELECT and swallowing whatever
        // else might be wrong with the database.
        try (Statement st = con.createStatement()) {
            st.execute("ALTER TABLE cd_drug_product RENAME TO cd_drug_product_prev");
            st.execute("CREATE TABLE " + DpdTableSwap.STATE_TABLE + " (state varchar(16) NOT NULL)");
            st.execute("INSERT INTO " + DpdTableSwap.STATE_TABLE + " VALUES ('BACKUP')");
            st.execute("CREATE TABLE cd_companies (id int primary key, name varchar(200))");
            st.execute("INSERT INTO cd_companies VALUES (1, 'ORIGINAL-OLD-BUILD')");
        }

        assertThat(swap.readMovedSet(con)).isNull();
        assertThat(swap.restoreBackupTables()).isEqualTo(1);

        assertThat(scalar("SELECT name FROM cd_companies")).isEqualTo("ORIGINAL-OLD-BUILD");
        assertThat(scalar("SELECT brand_name FROM cd_drug_product")).isEqualTo("OLD-AMOXICILLIN");
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

    private int scalarCount(String table) throws SQLException {
        return Integer.parseInt(scalar("SELECT count(*) FROM " + table));
    }

    private String scalar(String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
