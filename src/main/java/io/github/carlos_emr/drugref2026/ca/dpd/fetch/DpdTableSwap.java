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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.drugref2026.util.DrugrefProperties;
import io.github.carlos_emr.drugref2026.util.MiscUtils;

/**
 * Keeps the previous drug dataset alive as a set of {@code *_prev} tables for the
 * duration of an update, so that a failed update can be undone.
 *
 * <p>The import rebuilds every DPD table from scratch and, being DDL on
 * MySQL/MariaDB, none of it is transactional. Instead of dropping the live tables
 * the worker renames them aside with {@link #backupLiveTables()}; the importer then
 * creates fresh tables under the live names. On success the backup set is
 * {@link #discardBackupTables() discarded}; on any failure it is
 * {@link #restoreBackupTables() put back}.
 *
 * <h2>Why a state marker</h2>
 * <p>Neither the rename loop nor the drop loop is atomic, so a JVM exit or a SQL
 * error can leave a partial {@code *_prev} set — and the two partial states need
 * opposite repairs. A set left behind by an interrupted <em>backup</em> is the only
 * copy of the data and must be restored; a set left behind by an interrupted
 * <em>discard</em> is a stale copy of data the import already replaced, and
 * restoring it would splice an old drug dataset into a new one. The two are
 * indistinguishable from the table names alone, so this class records which
 * operation is in flight in a one-row {@link #STATE_TABLE} table, written before
 * the first rename and cleared after the last drop.
 * {@link #recoverInterruptedSwap()} reads it and applies the matching repair;
 * {@link #restoreIfInterrupted()} runs that at webapp start, before Hibernate
 * validates the schema, and {@link #backupLiveTables()} runs it again before
 * starting a new swap so an unresolved set is never dropped as stale.
 *
 * <p>Plain JDBC on purpose: this must work before the Spring context exists, and
 * it must not share a connection with an open JPA transaction whose metadata lock
 * would block the rename. Table names are compile-time constants, never input.
 *
 * <p><b>MySQL/MariaDB only.</b> CARLOS deploys DrugRef against MariaDB, and that is the
 * only backend this swap is written and tested for. PostgreSQL would need more than a
 * table rename: {@code ALTER TABLE ... RENAME TO} leaves the table's {@code serial}
 * sequence under the old name, so the importer's {@code CREATE TABLE ... id serial}
 * collides with the surviving sequence. If DrugRef is ever run on PostgreSQL for real,
 * fix this class first.
 *
 * <p>Lookups are degraded while an update runs, exactly as before: the live tables
 * are being rebuilt. What changes is that a failure no longer leaves them empty.
 */
public final class DpdTableSwap {

    /** Suffix of the backup copies. */
    public static final String BACKUP_SUFFIX = "_prev";

    /** One-row table recording which half of the swap is in flight. */
    static final String STATE_TABLE = "_carlos_drugref_swap";
    /** The previous dataset has been (or is being) moved aside; the import has not been accepted. */
    static final String STATE_BACKUP = "BACKUP";
    /** The import was accepted; the previous dataset is being dropped. */
    static final String STATE_DISCARD = "DISCARD";

    /**
     * Marker column holding the comma-separated tables {@link #backupLiveTables()} actually
     * renamed, written in one statement AFTER the rename loop completes. {@code NULL} therefore
     * means "the loop did not finish", and an empty string means "it finished and moved
     * nothing" — the two are deliberately distinct. Absent entirely on a marker written by a
     * build before this column existed.
     */
    static final String MOVED_COLUMN = "moved";

    /**
     * Every table the import rebuilds. {@code history}, {@code utility} and any
     * site-specific tables are untouched by an update and so are not swapped.
     */
    static final String[] SWAPPED_TABLES = {
        "cd_drug_product", "cd_companies", "cd_active_ingredients", "cd_drug_status", "cd_form",
        "cd_inactive_products", "cd_packaging", "cd_pharmaceutical_std", "cd_route", "cd_schedule",
        "cd_therapeutic_class", "cd_veterinary_species", "interactions",
        "cd_drug_search", "link_generic_brand"
    };

    private static final Logger logger = MiscUtils.getLogger();

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final String driverClass;

    /** Uses the database configured in {@link DrugrefProperties}. */
    public DpdTableSwap() {
        DrugrefProperties dp = DrugrefProperties.getInstance();
        // No query string is appended here for the same reason spring_config.xml
        // appends exactly one: db_url is documented as carrying no parameters of
        // its own. This connection needs no session settings, so it takes the URL
        // as configured rather than adding a second '?'.
        this.jdbcUrl = dp.getDbUrl();
        this.user = dp.getDbUser();
        this.password = dp.getDbPassword();
        this.driverClass = dp.getProperty("db_driver");
    }

    /** Explicit connection settings, for tests. */
    DpdTableSwap(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        // Tests run in a plain JVM, where the JDBC ServiceLoader has already
        // registered the driver by the time anything calls connect().
        this.driverClass = null;
    }

    /**
     * Renames every present live table to its {@code *_prev} name, so that the
     * importer can create fresh tables under the live names.
     *
     * <p>Any unresolved swap from an earlier attempt is settled first: without
     * that, the stale-backup cleanup below would drop a {@code *_prev} set that is
     * still the only copy of the data.
     */
    public void backupLiveTables() throws SQLException {
        SwapRecovery recovery = recoverInterruptedSwap();
        // Checking the post-condition rather than trusting the recovery's own word for it:
        // whatever it did, a new swap may only start from a clean slate. The case that makes
        // this necessary is a DISCARD whose cleanup failed. recoverInterruptedSwap() reports
        // that as committed (correctly -- the new data is live), but clearState() is the last
        // statement in discardBackupTables(), so the marker is still DISCARD and stale *_prev
        // tables remain. Proceeding would call openState() and relabel that stale set as a
        // BACKUP set; if the rename loop then failed on the same table the drop failed on, the
        // restore would put those OLD tables back over the NEW live data -- the exact splice
        // the marker exists to prevent, arrived at through the marker itself.
        if (readState() != null || !listBackupTables().isEmpty()) {
            throw new SQLException("DrugRef: refusing to start a database update while the"
                    + " previous one is unresolved (" + recovery.description() + "). The live"
                    + " drug tables are intact and in use; clear the leftover " + BACKUP_SUFFIX
                    + " tables and the " + STATE_TABLE + " marker by hand, then retry.");
        }
        try (Connection con = connect(); Statement st = con.createStatement()) {
            // The marker goes in BEFORE the first rename: a rename loop that fails
            // halfway must still be recognisable as an interrupted backup, or the
            // half-moved dataset is left with no record of what happened to it.
            openState(con, st);
            List<String> moved = new ArrayList<>();
            for (String table : SWAPPED_TABLES) {
                String backup = table + BACKUP_SUFFIX;
                if (tableExists(con, backup)) {
                    dropTable(st, backup);
                }
                if (tableExists(con, table)) {
                    renameTable(st, table, backup);
                    moved.add(table);
                }
            }
            // Recorded only now, in one statement, so its presence is proof the loop
            // completed. That is what lets restoreBackupTables() tell an orphan the import
            // created (safe to empty) from one this loop never reached (holding the original
            // rows). A crash anywhere above leaves it NULL and restore stays conservative.
            st.execute("UPDATE " + STATE_TABLE + " SET " + MOVED_COLUMN + " = '"
                    + String.join(",", moved) + "'");
        }
        logger.info("DrugRef update: previous dataset kept as " + BACKUP_SUFFIX + " tables");
    }

    /**
     * Puts the backup set back under the live names, dropping whatever partial
     * tables the failed import left behind, and clears the state marker.
     *
     * <p>A live table that has no {@code *_prev} counterpart is one of two things: a table
     * the importer created that had no previous version (its rows belong to the abandoned
     * run), or a table {@link #backupLiveTables()} never reached because the rename loop
     * failed partway (it still holds the <em>original</em> rows). Emptying it is right in the
     * first case and destroys data in the second, so the decision is made on evidence, never
     * on a guess: the marker's {@link #MOVED_COLUMN} is written only once the rename loop has
     * completed. When it is present, the backup provably finished and any live table it does
     * not list did not exist then — the import created it, and it is emptied (not dropped, so
     * Hibernate's startup validation still finds it). When it is {@code NULL}, or the marker
     * predates the column, the loop's fate is unknown and the table is left as it is, with a
     * warning naming both possibilities for the operator.
     *
     * @return the number of tables restored
     */
    public int restoreBackupTables() throws SQLException {
        int restored = 0;
        List<String> orphans = new ArrayList<>();
        List<String> emptied = new ArrayList<>();
        List<String> moved;
        try (Connection con = connect(); Statement st = con.createStatement()) {
            moved = readMovedSet(con);
            for (String table : SWAPPED_TABLES) {
                String backup = table + BACKUP_SUFFIX;
                if (!tableExists(con, backup)) {
                    if (tableExists(con, table)) {
                        if (moved != null && !moved.contains(table)) {
                            // The backup provably completed and did not move this table,
                            // so it did not exist then: the import created it, and its rows
                            // belong to the abandoned run. Emptied, not dropped -- Hibernate
                            // validates the schema against it at startup.
                            st.execute("DELETE FROM " + table);
                            emptied.add(table);
                        } else {
                            orphans.add(table);
                        }
                    }
                    continue;
                }
                if (tableExists(con, table)) {
                    dropTable(st, table);
                }
                renameTable(st, backup, table);
                restored++;
            }
            clearState(con, st);
        }
        logger.info("DrugRef update: restored " + restored + " table(s) from the " + BACKUP_SUFFIX + " set");
        if (!emptied.isEmpty()) {
            logger.info("DrugRef update: emptied " + emptied + ", which the abandoned import had created");
        }
        if (!orphans.isEmpty()) {
            if (liveTablesWereNeverDisturbed(restored, moved)) {
                logger.info("DrugRef update: nothing had been moved aside, so the live drug tables"
                        + " were never disturbed and are the previous dataset unchanged");
            } else {
                logger.warn("DrugRef update: " + orphans + " had no " + BACKUP_SUFFIX + " counterpart"
                        + " and were left as they are. Either the import created them (they now hold"
                        + " rows from the abandoned run) or the backup never reached them (they hold"
                        + " the original rows). Check them before relying on drug search.");
            }
        }
        return restored;
    }

    /**
     * Whether a restore that produced orphans can honestly tell the operator that the live
     * drug tables are the previous dataset, untouched.
     *
     * <p>Both conditions are required, and this is a seam rather than an inline expression
     * because getting it wrong is silent: the wrong answer is a reassuring log line over data
     * that is not what it claims, which nobody notices until a prescriber does.
     *
     * <ul>
     *   <li>{@code restored == 0} — no rename was undone.
     *   <li>{@code moved == null} — and none is recorded either, so
     *       {@link #backupLiveTables()} failed on its first table and never disturbed the live
     *       set. Warning here would tell operators to distrust a dataset that is provably fine.
     * </ul>
     *
     * <p>{@code restored == 0} alone is <em>not</em> enough, which is the trap. A non-null
     * {@code moved} set says the backup provably renamed these tables aside; if their
     * {@code *_prev} copies are then gone — dropped by a partial discard, or by hand — nothing
     * is restored and every moved table looks like an orphan, yet what is live is the abandoned
     * import's data, not the original. That is the one case where "unchanged" would be the
     * exact opposite of the truth, so it falls through to the warning along with the
     * genuinely ambiguous partial restore ({@code restored > 0}).
     */
    static boolean liveTablesWereNeverDisturbed(int restored, List<String> moved) {
        return restored == 0 && moved == null;
    }

    /**
     * Drops the backup set and clears the state marker.
     *
     * <p>{@link #markDiscarding()} must already have run: the marker is what makes this
     * step resumable, and moving it is the act that commits the new dataset. Calling this
     * without it would leave a window where a crash restores the dataset the import
     * replaced.
     */
    public void discardBackupTables() throws SQLException {
        try (Connection con = connect(); Statement st = con.createStatement()) {
            for (String table : SWAPPED_TABLES) {
                String backup = table + BACKUP_SUFFIX;
                if (tableExists(con, backup)) {
                    dropTable(st, backup);
                }
            }
            clearState(con, st);
        }
    }

    /**
     * What {@link #recoverInterruptedSwap()} found and did.
     *
     * <p>{@code committed} is the field that matters, and it exists because the caller cannot
     * infer it. A worker that lands in its catch block after {@link #markDiscarding()} has
     * committed on the server but thrown on the way back is looking at a <em>successful</em>
     * update: the marker says {@code DISCARD}, the new dataset is live, and nothing was rolled
     * back. Reporting that run as {@code FAILED} tells the operator the previous drug data was
     * kept, which is the one statement the commit ordering exists to prevent being false.
     *
     * @param committed   the marker had already accepted the new dataset, so the recovery
     *                    finished the cleanup and the live tables hold the NEW data
     * @param description what was done, for the log and the status message
     */
    public record SwapRecovery(boolean committed, String description) {
    }

    /**
     * Settles an unfinished swap, if there is one.
     *
     * <p>{@link #STATE_BACKUP} (or a {@code *_prev} set with no marker at all, which
     * is what a build predating the marker leaves) means the import never completed:
     * the previous dataset is restored. {@link #STATE_DISCARD} means the import was
     * accepted and only the cleanup was interrupted: the discard is finished, because
     * restoring here would mix the old dataset into the new one. A discard that fails is
     * still reported as committed — the marker already made the new dataset live, and a
     * leftover {@code *_prev} set is cleared on the next start.
     *
     * @return what was found and done, including whether the new dataset had been committed
     */
    public SwapRecovery recoverInterruptedSwap() throws SQLException {
        String state = readState();
        List<String> leftover = listBackupTables();
        if (state == null && leftover.isEmpty()) {
            return new SwapRecovery(false, "nothing to recover");
        }
        if (STATE_DISCARD.equals(state)) {
            // Best-effort, and the try/catch is load-bearing rather than defensive. Past the
            // marker the new dataset IS the live one, so nothing here can un-commit it: the
            // worst a failed DROP leaves is a stale *_prev set the next start clears. Letting
            // the exception out instead would escape before committed=true is ever returned,
            // and the caller would report a committed update as FAILED -- the very thing this
            // return value was added to prevent -- while telling the operator to reload the
            // drug reference seed over data that is correct and live.
            try {
                discardBackupTables();
                return new SwapRecovery(true,
                        "finished discarding the previous dataset (" + leftover.size() + " table(s))");
            } catch (SQLException | RuntimeException e) {
                logger.warn("DrugRef: the new dataset is committed but the previous one could not be"
                        + " dropped; the " + BACKUP_SUFFIX + " tables will be cleared on the next"
                        + " start", e);
                return new SwapRecovery(true,
                        "the new dataset is committed and live, but the previous one could not be"
                        + " dropped (" + e + "); the " + BACKUP_SUFFIX + " tables will be cleared"
                        + " on the next start");
            }
        }
        int restored = restoreBackupTables();
        return new SwapRecovery(false, "restored the previous dataset (" + restored + " table(s))");
    }

    /**
     * Thrown when the swap state has been read successfully and is <em>undecidable</em> — as
     * opposed to not having been readable at all.
     *
     * <p>The distinction is the whole point of the type. {@link #restoreIfInterrupted()} must
     * swallow "I could not reach the database", because Hibernate's own schema validation
     * reports that a moment later with a better message and a startup repair should not be what
     * takes the context down. It must NOT swallow "I looked, and the only record of which half
     * of a swap the process died in is gone": that is the one state where continuing means
     * serving drug data that may be half a rebuild. Both used to arrive as a plain
     * {@link SQLException} through the same catch, so the documented refusal never fired and
     * the service came up regardless — found by running the repair matrix against MariaDB, not
     * by reading the code.
     */
    public static class UndecidableSwapState extends SQLException {
        private static final long serialVersionUID = 1L;

        UndecidableSwapState(String message) {
            super(message);
        }
    }

    /** @return the backup tables currently present, empty when no update was interrupted */
    public List<String> listBackupTables() throws SQLException {
        List<String> present = new ArrayList<>();
        try (Connection con = connect()) {
            for (String table : SWAPPED_TABLES) {
                if (tableExists(con, table + BACKUP_SUFFIX)) {
                    present.add(table + BACKUP_SUFFIX);
                }
            }
        }
        return present;
    }

    /**
     * Startup repair: settle any swap that a previous run left unfinished, before
     * Hibernate validates the schema against the live tables.
     *
     * <p>Not every failure here is treated the same way, and the distinction is deliberate.
     * <b>Failing to look</b> — an unreachable or unreadable database — is logged and swallowed:
     * Hibernate's own schema validation fails against the same database moments later with a
     * better message, and a startup repair should not be what takes the context down.
     * <b>Looking and not being able to resolve what it found</b> throws
     * {@link IllegalStateException} and prevents the context from deploying: that covers both an
     * undecidable state marker ({@link UndecidableSwapState}) and a repair that was needed and
     * failed. In both of those the live drug tables may be half a rebuild, and a DrugRef that
     * quietly answers from those is worse for a prescriber than one that is plainly unavailable
     * — CARLOS renders an unreachable DrugRef as a banner rather than a broken lookup.
     *
     * @throws IllegalStateException if an interrupted update was found and could not be resolved
     */
    public static void restoreIfInterrupted() {
        restoreIfInterrupted(new DpdTableSwap());
    }

    /** Package-private seam so the repair decision can be tested against a real database. */
    static void restoreIfInterrupted(DpdTableSwap swap) {
        String state;
        List<String> leftover;
        try {
            state = swap.readState();
            leftover = swap.listBackupTables();
        } catch (UndecidableSwapState e) {
            // Checked, and undecidable. Refuse to deploy for the same reason the failed-repair
            // branch below does: a DrugRef that quietly answers from a half-swapped drug table
            // is worse for a prescriber than one that is plainly unavailable.
            logger.error("DrugRef: the interrupted database update cannot be resolved; refusing to start", e);
            throw new IllegalStateException(
                    "DrugRef: a previous database update was interrupted and its state marker is"
                    + " unreadable, so it is not possible to tell whether it had been committed."
                    + " Inspect the " + BACKUP_SUFFIX + " tables by hand before starting the service.", e);
        } catch (SQLException | RuntimeException e) {
            // Cannot tell whether a repair is needed. Hibernate's own schema validation
            // fails against the same unreachable database moments later, so let the
            // context fail there with the clearer message rather than pretending the
            // dataset was checked.
            logger.error("DrugRef: could not check for an interrupted database update", e);
            return;
        }
        if (state == null && leftover.isEmpty()) {
            return;
        }
        logger.warn("DrugRef: a previous database update did not finish (state=" + state
                + ", leftover=" + leftover + "); repairing");
        try {
            logger.warn("DrugRef: " + swap.recoverInterruptedSwap().description());
        } catch (SQLException | RuntimeException e) {
            // A repair was needed and could not be done, so the live tables are the
            // partial ones the backup set was meant to replace. Refuse to deploy: a
            // DrugRef that silently answers from a half-built drug table is worse for a
            // prescriber than one that is plainly unavailable, and CARLOS already renders
            // an unreachable DrugRef as a banner rather than a broken lookup.
            logger.error("DrugRef: could not repair the interrupted database update; refusing to start", e);
            throw new IllegalStateException(
                    "DrugRef: a previous database update was interrupted and could not be repaired."
                    + " The live drug tables may be incomplete. Restore the drugref2 database or"
                    + " reload the drug reference dataset before starting the service.", e);
        }
    }

    // --- state marker ------------------------------------------------------

    /**
     * @return the recorded state, or {@code null} when the marker table does not exist —
     *         either no swap is in flight, or the {@code *_prev} set was left by a build
     *         from before the marker existed, which {@link #recoverInterruptedSwap()}
     *         treats as {@link #STATE_BACKUP}
     * @throws SQLException if the marker table exists, holds no row, AND a {@code *_prev}
     *         set is present. Then the marker was the only record of which half of a swap
     *         the process died in, and both repairs destroy data when applied to the wrong
     *         half, so guessing is worse than stopping.
     */
    String readState() throws SQLException {
        try (Connection con = connect()) {
            if (!tableExists(con, STATE_TABLE)) {
                return null;
            }
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT state FROM " + STATE_TABLE)) {
                if (!rs.next()) {
                    return resolveEmptyMarker(con, st);
                }
                return rs.getString(1);
            }
        }
    }

    /**
     * @return the tables the last {@link #backupLiveTables()} renamed, or {@code null} when
     *         that cannot be known — the loop did not complete, there is no marker, or the
     *         marker was written by a build before {@link #MOVED_COLUMN} existed. An empty
     *         list is a completed backup that moved nothing, and is not {@code null}.
     */
    List<String> readMovedSet(Connection con) throws SQLException {
        if (!tableExists(con, STATE_TABLE)) {
            return null;
        }
        // Column presence is checked through metadata rather than by catching the failed
        // SELECT: a marker from an older build lacks the column, and that must read as
        // "unknown" without swallowing any other SQLException on the way.
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet cols = meta.getColumns(con.getCatalog(), null, STATE_TABLE, MOVED_COLUMN)) {
            if (!cols.next()) {
                return null;
            }
        }
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + MOVED_COLUMN + " FROM " + STATE_TABLE)) {
            if (!rs.next()) {
                return null;
            }
            String value = rs.getString(1);
            if (value == null) {
                return null;
            }
            List<String> moved = new ArrayList<>();
            for (String name : value.split(",")) {
                if (!name.isEmpty()) {
                    moved.add(name);
                }
            }
            return moved;
        }
    }

    /**
     * Decides what an empty marker table means, by looking at whether any dataset was
     * actually moved aside.
     *
     * <p>DDL is not transactional on MariaDB, so the marker cannot be created and
     * populated as one atomic act: {@code CREATE TABLE} commits on its own and the
     * {@code INSERT} that follows is a separate statement. A process killed between the
     * two leaves the table present and empty. {@link #openState} runs before the first
     * rename and {@link #markDiscarding()}'s create branch runs only when no swap is in
     * flight, so in both cases there is nothing moved aside to be ambiguous about — the
     * marker is meaningless and is cleared here.
     *
     * <p>An earlier revision threw unconditionally, which made that harmless crash
     * permanent: {@link #backupLiveTables()} settles any unfinished swap first, so it read
     * the marker and failed, and every subsequent update failed the same way until someone
     * dropped the table by hand.
     *
     * <p>With a {@code *_prev} set present the marker was load-bearing and something
     * outside this class truncated it. That stays an error.
     */
    private String resolveEmptyMarker(Connection con, Statement st) throws SQLException {
        for (String table : SWAPPED_TABLES) {
            if (tableExists(con, table + BACKUP_SUFFIX)) {
                throw new UndecidableSwapState("the DrugRef update marker table " + STATE_TABLE
                        + " exists but is empty, so it is not possible to tell whether the"
                        + " previous update had been committed. Inspect the " + BACKUP_SUFFIX
                        + " tables by hand: restoring them reverts a completed update, dropping"
                        + " them discards the only copy of the previous dataset.");
            }
        }
        logger.warn("DrugRef: the update marker table " + STATE_TABLE + " was left empty by an"
                + " interrupted start, and no " + BACKUP_SUFFIX + " tables exist, so no dataset"
                + " was moved aside; clearing it");
        st.execute("DROP TABLE " + STATE_TABLE);
        return null;
    }

    /**
     * Opens the marker at {@link #STATE_BACKUP}. Called before the first rename, when no
     * table has moved yet. A crash between the {@code CREATE} and the {@code INSERT}
     * leaves the marker table empty — DDL commits on its own here, so the two cannot be
     * made atomic — and {@link #resolveEmptyMarker} clears it on the next pass, having
     * confirmed no {@code *_prev} set exists to be ambiguous about.
     */
    private void openState(Connection con, Statement st) throws SQLException {
        if (tableExists(con, STATE_TABLE)) {
            st.execute("DROP TABLE " + STATE_TABLE);
        }
        st.execute("CREATE TABLE " + STATE_TABLE + " (state varchar(16) NOT NULL, "
                + MOVED_COLUMN + " varchar(1024) NULL)");
        st.execute("INSERT INTO " + STATE_TABLE + " (state) VALUES ('" + STATE_BACKUP + "')");
    }

    /**
     * Moves the marker to {@link #STATE_DISCARD} in ONE statement.
     *
     * <p>This is the durable commit point of an update, so it must never be observable as
     * "no state". An earlier revision wrote every transition as {@code DELETE} followed by
     * {@code INSERT} on an autocommit connection: a crash between the two left the marker
     * table present but empty, which the recovery pass read as "no marker" and — with
     * {@code *_prev} tables still around — treated as an interrupted backup, restoring the
     * old dataset over a new one that had already been accepted.
     */
    public void markDiscarding() throws SQLException {
        try (Connection con = connect(); Statement st = con.createStatement()) {
            if (!tableExists(con, STATE_TABLE)) {
                // No swap is in flight (an update over an empty schema, say). Record the
                // commit directly rather than leaving no marker at all.
                st.execute("CREATE TABLE " + STATE_TABLE + " (state varchar(16) NOT NULL, "
                        + MOVED_COLUMN + " varchar(1024) NULL)");
                st.execute("INSERT INTO " + STATE_TABLE + " (state, " + MOVED_COLUMN + ") VALUES ('"
                        + STATE_DISCARD + "', '')");
                return;
            }
            st.execute("UPDATE " + STATE_TABLE + " SET state = '" + STATE_DISCARD + "'");
        }
    }

    private void clearState(Connection con, Statement st) throws SQLException {
        if (tableExists(con, STATE_TABLE)) {
            st.execute("DROP TABLE " + STATE_TABLE);
        }
    }

    // --- DDL helpers -------------------------------------------------------

    private void dropTable(Statement st, String table) throws SQLException {
        st.execute("DROP TABLE " + table);
    }

    private void renameTable(Statement st, String from, String to) throws SQLException {
        st.execute("ALTER TABLE " + from + " RENAME TO " + to);
    }

    /**
     * Opens a connection, registering the configured JDBC driver first.
     *
     * <p>The {@code Class.forName} is load-bearing, not belt-and-braces. This class is
     * the first thing in the webapp to open a JDBC connection: {@link #restoreIfInterrupted()}
     * runs from the {@code ServletContextListener}, before Spring builds the pool, and at
     * that point {@link DriverManager} has not yet discovered the driver sitting in
     * {@code WEB-INF/lib}. Without this the startup repair failed on every boot with
     * "No suitable driver found", was swallowed by the caller's catch, and silently did
     * nothing — which is only visible in a real deployment, since a plain JVM (the unit
     * tests) registers the driver through the ServiceLoader long before this runs.
     */
    private Connection connect() throws SQLException {
        if (driverClass != null && !driverClass.isEmpty()) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException e) {
                throw new SQLException("JDBC driver " + driverClass + " is not on the classpath", e);
            }
        }
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    static boolean tableExists(Connection con, String table) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getTables(con.getCatalog(), null, table, new String[] {"TABLE"})) {
            return rs.next();
        }
    }
}
