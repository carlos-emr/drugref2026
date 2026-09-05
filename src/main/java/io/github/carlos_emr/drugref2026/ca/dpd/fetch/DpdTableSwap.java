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
 * {@link #restoreBackupTables() put back}. {@link #restoreIfInterrupted()} runs at
 * webapp start so that an update cut short by a JVM exit (an OOM under
 * {@code -XX:+ExitOnOutOfMemoryError}, a service restart) is also undone, before
 * Hibernate validates the schema.
 *
 * <p>Plain JDBC on purpose: this must work before the Spring context exists, and
 * it must not share a connection with an open JPA transaction whose metadata lock
 * would block the rename. Table names are compile-time constants, never input.
 *
 * <p>Lookups are degraded while an update runs, exactly as before: the live tables
 * are being rebuilt. What changes is that a failure no longer leaves them empty.
 */
public final class DpdTableSwap {

    /** Suffix of the backup copies. */
    public static final String BACKUP_SUFFIX = "_prev";

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

    /** Uses the database configured in {@link DrugrefProperties}. */
    public DpdTableSwap() {
        DrugrefProperties dp = DrugrefProperties.getInstance();
        this.jdbcUrl = dp.getDbUrl() + "?serverTimezone=UTC";
        this.user = dp.getDbUser();
        this.password = dp.getDbPassword();
    }

    /** Explicit connection settings, for tests. */
    DpdTableSwap(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    /**
     * Renames every present live table to its {@code *_prev} name, first removing
     * any stale backup from an earlier attempt. After this call the live names are
     * free for the importer to create.
     */
    public void backupLiveTables() throws SQLException {
        try (Connection con = connect(); Statement st = con.createStatement()) {
            for (String table : SWAPPED_TABLES) {
                String backup = table + BACKUP_SUFFIX;
                if (tableExists(con, backup)) {
                    st.execute("DROP TABLE " + backup);
                }
                if (tableExists(con, table)) {
                    st.execute("ALTER TABLE " + table + " RENAME TO " + backup);
                }
            }
        }
        logger.info("DrugRef update: previous dataset kept as " + BACKUP_SUFFIX + " tables");
    }

    /**
     * Puts the backup set back under the live names, dropping whatever partial
     * tables the failed import left behind. Tables that have no backup (an
     * update on an empty database) are dropped so the schema is not half new.
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
                    st.execute("DROP TABLE " + table);
                }
                st.execute("ALTER TABLE " + backup + " RENAME TO " + table);
                restored++;
            }
        }
        logger.info("DrugRef update: restored " + restored + " table(s) from the " + BACKUP_SUFFIX + " set");
        return restored;
    }

    /** Drops the backup set after a successful import. */
    public void discardBackupTables() throws SQLException {
        try (Connection con = connect(); Statement st = con.createStatement()) {
            for (String table : SWAPPED_TABLES) {
                String backup = table + BACKUP_SUFFIX;
                if (tableExists(con, backup)) {
                    st.execute("DROP TABLE " + backup);
                }
            }
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
     * Startup repair: if a backup set exists, the last update did not finish (the
     * worker discards it as its final step), so the live tables are partial.
     * Restore the previous dataset. Errors are logged, not thrown: an unreachable
     * database at startup is reported by Hibernate's own validation a moment later.
     */
    public static void restoreIfInterrupted() {
        try {
            DpdTableSwap swap = new DpdTableSwap();
            List<String> leftover = swap.listBackupTables();
            if (leftover.isEmpty()) {
                return;
            }
            logger.warn("DrugRef: a previous database update did not finish (found " + leftover
                    + "); restoring the previous dataset");
            swap.restoreBackupTables();
        } catch (SQLException | RuntimeException e) {
            logger.error("DrugRef: could not check for an interrupted database update", e);
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, user, password);
    }

    static boolean tableExists(Connection con, String table) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        try (ResultSet rs = meta.getTables(con.getCatalog(), null, table, new String[] {"TABLE"})) {
            return rs.next();
        }
    }
}
