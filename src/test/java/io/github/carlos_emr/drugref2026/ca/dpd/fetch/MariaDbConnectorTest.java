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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.drugref2026.util.DrugrefDataSource;
import io.github.carlos_emr.drugref2026.util.DrugrefProperties;

/** Physical server coverage; each run owns a new disposable schema and never touches an existing one. */
@EnabledIfEnvironmentVariable(named = "DRUGREF_TEST_MARIADB_URL", matches = ".+")
class MariaDbConnectorTest {
    private static final String SCHEMA = "drugref_connector_" + UUID.randomUUID().toString().replace("-", "");
    private static Connection administrator;
    private static String base;
    private static String user;
    private static String password;

    /** Allocate an isolated schema through the native connector before exercising the adapter. */
    @BeforeAll
    static void createSchema() throws Exception {
        base = System.getenv("DRUGREF_TEST_MARIADB_URL");
        assertThat(base).startsWith("jdbc:mariadb://").endsWith("/").doesNotContain("?");
        user = System.getenv("DRUGREF_TEST_MARIADB_USER");
        password = System.getenv("DRUGREF_TEST_MARIADB_PASSWORD");
        Properties credentials = new Properties();
        credentials.setProperty("user", user);
        credentials.setProperty("password", password);
        administrator = new org.mariadb.jdbc.Driver().connect(base, credentials);
        try (Statement statement = administrator.createStatement()) {
            statement.execute("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    /** Remove only this test run's schema, including when an assertion fails. */
    @AfterAll
    static void removeSchema() throws Exception {
        if (administrator != null) {
            try (Connection cleanup = administrator; Statement statement = cleanup.createStatement()) {
                statement.execute("DROP DATABASE " + SCHEMA);
            }
        }
    }

    /** Cover the pooled configuration, generated keys, aliases, timestamps and rollback together. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveDataMetadataAndTransactions_withNativeAndLegacyConfiguration(boolean legacy) throws Exception {
        String url = base + SCHEMA;
        if (legacy) url = url.replace("jdbc:mariadb:", "jdbc:mysql:");
        url += "?zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true&sslMode=DISABLED"
                + "&forceConnectionTimeZoneToSession=true";
        // Legacy properties override the URL; native MariaDB gives the URL precedence.
        url += "&user=" + URLEncoder.encode(legacy ? "wrong" : user, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(legacy ? "wrong" : password, StandardCharsets.UTF_8);
        try (DrugrefDataSource source = new DrugrefDataSource()) {
            source.setDriverClassName(legacy ? "com.mysql.cj.jdbc.Driver" : "org.mariadb.jdbc.Driver");
            source.setJdbcUrl(url);
            source.setUsername(legacy ? user : "wrong");
            source.setPassword(legacy ? password : "wrong"); // ggignore - deliberately invalid test credential
            source.setMaximumPoolSize(1);
            source.setMinimumIdle(0);
            try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS connector_probe");
                statement.execute("CREATE TABLE connector_probe (id int auto_increment primary key, "
                        + "label varchar(60), enabled tinyint(1), recorded date, happened timestamp, payload varbinary(20))");
                Instant instant = Instant.parse("2026-11-01T05:30:00Z");
                byte[] bytes = new byte[] {0, 1, -1, 32};
                try (var insert = connection.prepareStatement("INSERT INTO connector_probe "
                        + "(label,enabled,recorded,happened,payload) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, "Synthetic \uD83E\uDDEA");
                    insert.setBoolean(2, true);
                    insert.setDate(3, java.sql.Date.valueOf("2026-11-01"));
                    insert.setTimestamp(4, Timestamp.from(instant));
                    insert.setBytes(5, bytes);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                    try (var keys = insert.getGeneratedKeys()) {
                        assertThat(keys.next()).isTrue();
                        assertThat(keys.getLong(1)).isEqualTo(1L);
                    }
                }
                try (var rows = statement.executeQuery("SELECT label AS display_name, enabled, recorded, happened, payload FROM connector_probe")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getMetaData().getColumnName(1)).isEqualTo("display_name");
                    assertThat(rows.getString(1)).isEqualTo("Synthetic \uD83E\uDDEA");
                    assertThat(rows.getBoolean(2)).isTrue();
                    assertThat(rows.getDate(3).toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 1));
                    assertThat(rows.getTimestamp(4).toInstant()).isEqualTo(instant);
                    assertThat(rows.getBytes(5)).containsExactly(bytes);
                }
                connection.setAutoCommit(false);
                statement.executeUpdate("UPDATE connector_probe SET label='rolled back'");
                connection.rollback();
                connection.setAutoCommit(true);
                statement.execute("SET SESSION sql_mode=''");
                statement.executeUpdate("UPDATE connector_probe SET recorded='0000-00-00'");
                try (var rows = statement.executeQuery("SELECT label, recorded FROM connector_probe")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("Synthetic \uD83E\uDDEA");
                    assertThat(rows.getDate(2).toLocalDate()).isEqualTo(java.time.LocalDate.of(1, 1, 1));
                    assertThat(rows.wasNull()).isFalse();
                }
            }
        }
    }

    /** Exercise table-swap recovery through the raw connection path used before Spring starts. */
    @Test
    void shouldRestorePreviousDataset_throughPreSpringLegacyDriverConfiguration() throws Exception {
        DrugrefProperties properties = DrugrefProperties.getInstance();
        Properties previous = new Properties();
        previous.putAll(properties);
        try {
            properties.setProperty("db_url", (base + SCHEMA).replace("jdbc:mariadb:", "jdbc:mysql:"));
            properties.setProperty("db_driver", "com.mysql.cj.jdbc.Driver");
            properties.setProperty("db_user", user);
            properties.setProperty("db_password", password);
            try (Connection connection = DrugrefDataSource.connect(properties.getDbUrl(), user, password,
                    properties.getProperty("db_driver")); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(100))");
                statement.execute("INSERT INTO cd_drug_product VALUES (1, 'SYNTHETIC OLD DATA')");
                DpdTableSwap swap = new DpdTableSwap();
                swap.backupLiveTables();
                statement.execute("CREATE TABLE cd_drug_product (id int primary key, brand_name varchar(100))");
                statement.execute("INSERT INTO cd_drug_product VALUES (2, 'SYNTHETIC INCOMPLETE DATA')");
                assertThat(swap.restoreBackupTables()).isEqualTo(1);
                try (var rows = statement.executeQuery("SELECT brand_name FROM cd_drug_product")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("SYNTHETIC OLD DATA");
                    assertThat(rows.next()).isFalse();
                }
                assertThat(swap.listBackupTables()).isEmpty();
            }
        } finally {
            properties.clear();
            properties.putAll(previous);
        }
    }

    /** #5969: Connector/J's yes/no Boolean spellings failed every MariaDB connection before translation. */
    @Test
    void shouldConnectAndApplyConnectorJYesNo_forLegacyBooleanOptions() throws Exception {
        String url = (base + SCHEMA).replace("jdbc:mariadb:", "jdbc:mysql:") + "?allowPublicKeyRetrieval=yes"
                + "&useServerPrepStmts=YES&cachePrepStmts=No&tcpKeepAlive=Yes&allowMultiQueries=yes&useAffectedRows=no";
        try (Connection connection = DrugrefDataSource.connect(url, user, password, "com.mysql.cj.jdbc.Driver");
             Statement statement = connection.createStatement()) {
            var configuration = connection.unwrap(org.mariadb.jdbc.Connection.class).getContext().getConf();
            assertThat(configuration.allowPublicKeyRetrieval()).isTrue();
            assertThat(configuration.useServerPrepStmts()).isTrue();
            assertThat(configuration.cachePrepStmts()).isFalse();
            assertThat(configuration.tcpKeepAlive()).isTrue();
            assertThat(configuration.allowMultiQueries()).isTrue();
            assertThat(configuration.useAffectedRows()).isFalse();
            statement.execute("CREATE TABLE yes_no_probe (id int primary key, label varchar(10))");
            // allowMultiQueries=yes: one statement string carries two inserts.
            statement.execute("INSERT INTO yes_no_probe VALUES (1, 'same'); INSERT INTO yes_no_probe VALUES (2, 'same')");
            // useAffectedRows=no keeps Connector/J's found-rows count for a row that does not change.
            assertThat(statement.executeUpdate("UPDATE yes_no_probe SET label='same' WHERE id=1")).isEqualTo(1);
            statement.execute("DROP TABLE yes_no_probe");
        }
    }

    /** carlos-emr/carlos#5968: values read from the same rows through MySQL Connector/J 9.6.0. */
    @Test
    void shouldPreserveConnectorJValues_forFloatingAndBitColumns() throws Exception {
        String url = (base + SCHEMA).replace("jdbc:mariadb:", "jdbc:mysql:");
        try (Connection connection = DrugrefDataSource.connect(url, user, password, "com.mysql.cj.jdbc.Driver");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE number_probe (id int primary key, d double, f float, b1 bit(1), b8 bit(8), "
                    + "b64 bit(64), t1 tinyint(1))");
            statement.execute("INSERT INTO number_probe VALUES (1, 100, 1.1, b'1', 5, 3, -1), "
                    + "(2, 1.0E7, 16777217, b'0', 0, b'1" + "0".repeat(63) + "', 1), (3, NULL, NULL, NULL, NULL, NULL, NULL)");
            try (var rows = statement.executeQuery("SELECT * FROM number_probe ORDER BY id")) {
                assertThat(rows.getMetaData().getColumnClassName(rows.findColumn("b8"))).isEqualTo(Boolean.class.getName());
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("d")).isEqualTo("100.0");
                assertThat(rows.getBigDecimal("d")).hasToString("100.0");
                assertThat(rows.getString("f")).isEqualTo("1.1");
                assertThat(rows.getString("b1")).isEqualTo("1");
                assertThat(rows.getObject("b1")).isEqualTo(Boolean.TRUE);
                assertThat(rows.getString("b8")).isEqualTo("5");
                assertThat(rows.getDouble("b8")).isEqualTo(5.0d);
                assertThat(rows.getDouble("t1")).isEqualTo(-1.0d);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("d")).isEqualTo("1.0E7");
                assertThat(rows.getString("f")).isEqualTo("1.67772E7");
                assertThat(rows.getString("b1")).isEqualTo("0");
                assertThat(rows.getString("b64")).isEqualTo("-9223372036854775808");
                assertThat(rows.getBigDecimal("b64")).hasToString("9223372036854775808");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("d")).isNull();
                assertThat(rows.wasNull()).isTrue();
                assertThat(rows.getObject("b1")).isNull();
            }
            statement.execute("DROP TABLE number_probe");
        }
    }

    /** Keep the former driver's default rejection of zero dates explicit. */
    @Test
    void shouldRejectZeroDates_byDefaultRatherThanSilentlyReturningNull() throws Exception {
        try (Connection connection = DrugrefDataSource.connect(base + SCHEMA, user, password, "com.mysql.cj.jdbc.Driver");
             Statement statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT CAST('0000-00-00' AS DATE)")) {
            assertThat(rows.next()).isTrue();
            assertThatThrownBy(() -> rows.getDate(1)).isInstanceOf(SQLException.class);
        }
    }

    /** Preserve DATETIME object types while preventing partial zero dates from being coerced. */
    @Test
    void shouldPreserveDatetimeObjects_andRejectIncompleteDatesWithoutCoercion() throws Exception {
        try (Connection connection = DrugrefDataSource.connect(base + SCHEMA + "?zeroDateTimeBehavior=ROUND",
                user, password, "com.mysql.cj.jdbc.Driver"); Statement statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT CAST('2026-11-01 01:30:00' AS DATETIME), "
                    + "CAST('0000-00-00 00:00:00' AS DATETIME)")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isEqualTo(java.time.LocalDateTime.of(2026, 11, 1, 1, 30));
                assertThat(rows.getObject(2)).isEqualTo(java.time.LocalDateTime.of(1, 1, 1, 0, 0));
                assertThat(rows.getMetaData().getColumnClassName(1)).isEqualTo(java.time.LocalDateTime.class.getName());
            }
            for (String value : new String[] {"2020-00-01 12:34:56", "0000-00-00 12:34:56"}) {
                try (var query = connection.prepareStatement("SELECT CAST(? AS DATETIME)")) {
                    query.setString(1, value);
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThatThrownBy(() -> rows.getTimestamp(1)).isInstanceOf(SQLException.class);
                        assertThatThrownBy(() -> rows.getObject(1)).isInstanceOf(SQLException.class);
                    }
                }
            }
        }
    }

    /** Empty numeric settings retain the legacy default and explicit opt-out on both protocols. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveEmptyNumericSettings_withTextAndServerPreparedReads(boolean serverPrepared) throws Exception {
        for (boolean convert : new boolean[] {true, false}) {
            String url = base + SCHEMA + "?useServerPrepStmts=" + serverPrepared
                    + (convert ? "" : "&emptyStringsConvertToZero=false");
            try (Connection connection = DrugrefDataSource.connect(url, user, password, "com.mysql.cj.jdbc.Driver");
                 var query = connection.prepareStatement("SELECT CAST('' AS CHAR) AS blank, CAST(NULL AS CHAR) AS missing, 42 AS sentinel");
                 var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue();
                if (convert) {
                    assertThat(rows.getInt("blank")).isZero();
                    assertThat(rows.wasNull()).isFalse();
                    assertThat(rows.getBigDecimal("blank")).isEqualByComparingTo(java.math.BigDecimal.ZERO);
                    assertThat(rows.getObject("blank", Integer.class)).isEqualTo(0);
                    assertThat(rows.wasNull()).isFalse();
                } else {
                    assertThatThrownBy(() -> rows.getInt("blank")).isInstanceOf(SQLException.class);
                    assertThatThrownBy(() -> rows.getObject("blank", Integer.class)).isInstanceOf(SQLException.class);
                }
                assertThat(rows.getString("blank")).isEmpty();
                assertThat(rows.getObject("blank")).isEqualTo("");
                assertThat(rows.getInt("missing")).isZero();
                assertThat(rows.wasNull()).isTrue();
                assertThat(rows.getInt("sentinel")).isEqualTo(42);
            }
        }
    }

    /** Preserve the former driver's textual false values and its negative-number boolean rule. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveBooleanConversions_withTextAndServerPreparedReads(boolean serverPrepared) throws Exception {
        try (Connection connection = DrugrefDataSource.connect(base + SCHEMA + "?useServerPrepStmts=" + serverPrepared,
                user, password, "com.mysql.cj.jdbc.Driver");
             var query = connection.prepareStatement("SELECT 'false' AS f, 'no' AS n, -2 AS negative, -1 AS enabled, 'yes' AS y, NULL AS missing");
             var rows = query.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean("f")).isFalse();
            assertThat(rows.getObject("f", Boolean.class)).isFalse();
            assertThat(rows.getBoolean("n")).isFalse();
            assertThat(rows.getBoolean("negative")).isFalse();
            assertThat(rows.getBoolean("enabled")).isTrue();
            assertThat(rows.getBoolean("y")).isTrue();
            assertThat(rows.wasNull()).isFalse();
            assertThat(rows.getString("f")).isEqualTo("false");
            assertThat(rows.getBoolean("missing")).isFalse();
            assertThat(rows.wasNull()).isTrue();
        }
    }
}
