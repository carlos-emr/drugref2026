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
package io.github.carlos_emr.drugref2026.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import java.util.Properties;

import io.github.carlos_emr.drugref2026.ca.dpd.fetch.DPDImport;

class DrugrefDataSourceTest {

    @Test
    void shouldAcceptExistingDriverAndUrl_withoutLoadingMysqlConnector() throws Exception {
        try (DrugrefDataSource source = new DrugrefDataSource()) {
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setJdbcUrl("jdbc:mysql://localhost/drugref2");
            assertThat(source.getDriverClassName()).isEqualTo(MariaDbCompatibilityDriver.class.getName());
            var settings = MariaDbJdbcCompatibility.settings(source.getJdbcUrl(), new Properties());
            assertThat(settings.url()).isEqualTo("jdbc:mariadb://localhost/drugref2");
            assertThat(settings.properties()).containsEntry("connectionTimeZone", "UTC");
        }
    }

    @Test
    void shouldKeepQueryOptionsAndExplicitTimezone_withoutAddingSecondQueryMarker() {
        String configured = "jdbc:mysql://localhost/drugref2?connectTimeout=1400&serverTimezone=America/Toronto";
        String resolved = DrugrefDataSource.connectionUrl(configured);
        assertThat(resolved).doesNotContain("?serverTimezone=UTC").doesNotContain("connectionTimeZone=UTC");
        assertThat(resolved).contains("connectTimeout=1400");
        assertThat(resolved.chars().filter(c -> c == '?').count()).isEqualTo(1);
    }

    @Test
    void shouldAppendDefaultTimezoneToExistingOptions() throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(
                DrugrefDataSource.connectionUrl("jdbc:mariadb://localhost/drugref2?connectTimeout=1400"), new Properties());
        assertThat(settings.properties()).containsEntry("connectTimeout", "1400").containsEntry("connectionTimeZone", "UTC");
    }

    @Test
    void shouldHonorMariaDbLegacyTimezone_regardlessOfOptionCase() throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(
                DrugrefDataSource.connectionUrl("jdbc:mariadb://localhost/drugref2?TiMeZoNe=America%2FToronto"), new Properties());
        var parsed = org.mariadb.jdbc.Configuration.parse(settings.url(), settings.properties());
        assertThat(parsed.connectionTimeZone()).isEqualTo("America/Toronto");
        assertThat(parsed.forceConnectionTimeZoneToSession()).isTrue();
    }

    @Test
    void shouldLeaveOtherDriversUnchanged() {
        try (DrugrefDataSource source = new DrugrefDataSource()) {
            source.setDriverClassName("org.h2.Driver");
            source.setJdbcUrl("jdbc:h2:mem:source_test");
            assertThat(source.getDriverClassName()).isEqualTo("org.h2.Driver");
            assertThat(source.getJdbcUrl()).isEqualTo("jdbc:h2:mem:source_test");
        }
    }

    @Test
    void shouldKeepMysqlDialectForMariaDbImportIndexes() {
        DrugrefProperties properties = DrugrefProperties.getInstance();
        String prior = properties.getProperty("db_url");
        try {
            properties.setProperty("db_url", "jdbc:mariadb://localhost/drugref2");
            assertThat(properties.isMysql()).isTrue();
            assertThat(new DPDImport().addIndexToSearchTable())
                    .contains("create index  cd_drug_search_name_idx on cd_drug_search(name(70));");
        } finally {
            if (prior == null) {
                properties.remove("db_url");
            } else {
                properties.setProperty("db_url", prior);
            }
        }
    }

    @Test
    void shouldAbortImport_whenDatabaseMetadataCannotBeRead() {
        DrugrefProperties properties = DrugrefProperties.getInstance();
        Properties previous = new Properties();
        previous.putAll(properties);
        try {
            properties.setProperty("db_driver", "org.h2.Driver");
            properties.setProperty("db_url", "jdbc:h2:mem:nonexistent_drugref_database;IFEXISTS=TRUE");
            properties.setProperty("db_user", "sa");
            properties.setProperty("db_password", "");
            assertThatThrownBy(() -> new DPDImport().getDPDTablesDrop())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Unable to inspect DrugRef database tables")
                    .hasCauseInstanceOf(java.sql.SQLException.class);
        } finally {
            properties.clear();
            properties.putAll(previous);
        }
    }
}
