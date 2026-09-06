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
        recoverInterruptedSwap();
        try (Connection con = connect(); Statement st = con.createStatement()) {
            // The marker goes in BEFORE the first rename: a rename loop that fails
            // halfway must still be recognisable as an interrupted backup, or the
            // half-moved dataset is left with no record of what happened to it.
            openState(con, st);
            for (String table : SWAPPED_TABLES) {
                String backup = table + BACKUP_SUFFIX;
                if (tableExists(con, backup)) {
                    dropTable(st, backup);
                }
                if (tableExists(con, table)) {
                    renameTable(st, table, backup);
                }
            }
        }
        logger.info("DrugRef update: previous dataset kept as " + BACKUP_SUFFIX + " tables");
    }

    /**
     * Puts the backup set back under the live names, dropping whatever partial
     * tables the failed import left behind, and clears the state marker.
     *
     * <p>A live table that has no {@code *_prev} counterpart is left alone: it is
     * a table the importer created that had no previous version, and dropping it
     * could leave the schema missing a table Hibernate validates at startup.
     *
     * @return the number of tables restored
     */
    public int restoreBackupTables() throws SQLException {
        int restored = 0;
        try (Connection con = connect(); Statement st = con.createStatement()) {
            for (String table : SWAPPED_TABLES) {
                String backup = table + BACKUP_SUFFIX;
                if (!tableExists(con, backup)) {
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
        return restored;
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
     * Settles an unfinished swap, if there is one.
     *
     * <p>{@link #STATE_BACKUP} (or a {@code *_prev} set with no marker at all, which
     * is what a build predating the marker leaves) means the import never completed:
     * the previous dataset is restored. {@link #STATE_DISCARD} means the import was
     * accepted and only the cleanup was interrupted: the discard is finished, because
     * restoring here would mix the old dataset into the new one.
     *
     * @return a description of what was done, for the log
     */
    public String recoverInterruptedSwap() throws SQLException {
        String state = readState();
        List<String> leftover = listBackupTables();
        if (state == null && leftover.isEmpty()) {
            return "nothing to recover";
        }
        if (STATE_DISCARD.equals(state)) {
            discardBackupTables();
            return "finished discarding the previous dataset (" + leftover.size() + " table(s))";
        }
        int restored = restoreBackupTables();
        return "restored the previous dataset (" + restored + " table(s))";
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
     * Hibernate validates the schema against the live tables. Errors are logged,
     * not thrown: an unreachable database at startup is reported by Hibernate's own
     * validation a moment later, and this must not be what prevents the context
     * from deploying.
     */
    public static void restoreIfInterrupted() {
        DpdTableSwap swap = new DpdTableSwap();
        String state;
        List<String> leftover;
        try {
            state = swap.readState();
            leftover = swap.listBackupTables();
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
            logger.warn("DrugRef: " + swap.recoverInterruptedSwap());
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
     * @throws SQLException if the marker table exists but holds no row. That is a state
     *         this class cannot produce — {@link #openState} writes the row as it creates
     *         the table, and {@link #markDiscarding()} moves it with a single {@code UPDATE}
     *         — so it means something outside truncated it. Both repairs destroy data when
     *         applied to the wrong half of a swap, so guessing is worse than stopping.
     */
    String readState() throws SQLException {
        try (Connection con = connect()) {
            if (!tableExists(con, STATE_TABLE)) {
                return null;
            }
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT state FROM " + STATE_TABLE)) {
                if (!rs.next()) {
                    throw new SQLException("the DrugRef update marker table " + STATE_TABLE
                            + " exists but is empty, so it is not possible to tell whether the"
                            + " previous update had been committed. Inspect the " + BACKUP_SUFFIX
                            + " tables by hand: restoring them reverts a completed update, dropping"
                            + " them discards the only copy of the previous dataset.");
                }
                return rs.getString(1);
            }
        }
    }

    /**
     * Opens the marker at {@link #STATE_BACKUP}. Called before the first rename, when no
     * table has moved yet, so a crash anywhere inside it is harmless: the recovery pass
     * finds no {@code *_prev} tables and does nothing.
     */
    private void openState(Connection con, Statement st) throws SQLException {
        if (tableExists(con, STATE_TABLE)) {
            st.execute("DROP TABLE " + STATE_TABLE);
        }
        st.execute("CREATE TABLE " + STATE_TABLE + " (state varchar(16) NOT NULL)");
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
                st.execute("CREATE TABLE " + STATE_TABLE + " (state varchar(16) NOT NULL)");
                st.execute("INSERT INTO " + STATE_TABLE + " (state) VALUES ('" + STATE_DISCARD + "')");
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
