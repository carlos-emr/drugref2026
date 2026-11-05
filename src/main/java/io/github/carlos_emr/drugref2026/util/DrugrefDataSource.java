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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import com.zaxxer.hikari.HikariDataSource;

/** Applies the connector migration to existing operator properties before Hikari loads its driver. */
public final class DrugrefDataSource extends HikariDataSource {

    @Override
    public void setDriverClassName(String driverClassName) {
        super.setDriverClassName(MariaDbJdbcCompatibility.normalizeDriverClassName(driverClassName));
    }

    @Override
    public void setJdbcUrl(String jdbcUrl) {
        super.setJdbcUrl(connectionUrl(jdbcUrl));
    }

    /** Use the same adapter before Spring starts, without depending on DriverManager registration order. */
    public static Connection connect(String url, String user, String password, String driverClass) throws SQLException {
        if (MariaDbJdbcCompatibility.isMariaDbUrl(url)) {
            Properties credentials = new Properties();
            if (user != null) credentials.setProperty("user", user);
            if (password != null) credentials.setProperty("password", password);
            return new MariaDbCompatibilityDriver().connect(connectionUrl(url), credentials);
        }
        if (driverClass != null && !driverClass.isBlank()) {
            try {
                Class.forName(driverClass);
            } catch (ClassNotFoundException missing) {
                throw new SQLException("Configured JDBC driver is not on the classpath", missing);
            }
        }
        return DriverManager.getConnection(url, user, password);
    }

    /** Use UTC by default as before, without adding a second query marker or overriding an operator. */
    public static String connectionUrl(String configured) {
        String url = MariaDbJdbcCompatibility.normalizeUrl(configured);
        if (url == null || !url.startsWith("jdbc:mariadb:")) {
            return url;
        }
        int query = url.indexOf('?');
        if (query >= 0) {
            for (String option : url.substring(query + 1).split("&")) {
                String key = option.split("=", 2)[0];
                try {
                    key = URLDecoder.decode(key, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException malformed) {
                    throw new IllegalArgumentException("Invalid JDBC option encoding");
                }
                if (key.equalsIgnoreCase("connectionTimeZone") || key.equalsIgnoreCase("serverTimezone")
                        || key.equalsIgnoreCase("timezone")) {
                    return url;
                }
            }
        }
        return url + (query < 0 ? "?" : "&") + "connectionTimeZone=UTC";
    }
}
