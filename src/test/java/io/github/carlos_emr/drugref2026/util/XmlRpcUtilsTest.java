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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;
import java.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("default-time-zone")
class XmlRpcUtilsTest {
    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Vancouver", "Pacific/Kiritimati"})
    void sqlDatePreservesDatabaseCalendarDate(String zone) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            java.sql.Date date = java.sql.Date.valueOf("2018-07-24");
            String xml = XmlRpcUtils.buildResponse(date);
            assertThat(xml).contains("<dateTime.iso8601>20180724T00:00:00</dateTime.iso8601>");
            assertThat(XmlRpcUtils.parseResponse(xml)).isEqualTo(new Date(date.getTime()));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void ordinaryDateAndTimestampKeepLocalTimeAndSecondPrecision() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Vancouver"));
            Instant instant = Instant.parse("2026-01-01T02:03:04Z");
            for (Date date : new Date[]{Date.from(instant), Timestamp.from(instant.plusMillis(567))}) {
                String xml = XmlRpcUtils.buildResponse(date);
                assertThat(xml).contains("<dateTime.iso8601>20251231T18:03:04</dateTime.iso8601>");
                assertThat(XmlRpcUtils.parseResponse(xml)).isEqualTo(Date.from(instant));
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void sqlTimeDoesNotCallUnsupportedToInstant() {
        assertThat(XmlRpcUtils.buildResponse(java.sql.Time.valueOf("12:34:56")))
                .contains("T12:34:56</dateTime.iso8601>");
    }

    @Test
    void inactiveDateInsideNestedArrayAndStructRoundTrips() throws Exception {
        Vector<Object> dates = new Vector<>();
        java.sql.Date date = java.sql.Date.valueOf("2018-07-24");
        dates.add(date);
        dates.add(null);
        Hashtable<String, Object> result = new Hashtable<>();
        result.put("inactive", dates);
        Object parsed = XmlRpcUtils.parseResponse(XmlRpcUtils.buildResponse(result));
        assertThat(parsed).isInstanceOf(Hashtable.class);
        Vector<?> actual = (Vector<?>) ((Hashtable<?, ?>) parsed).get("inactive");
        assertThat(actual.get(0)).isEqualTo(new Date(date.getTime()));
        assertThat(actual.get(1)).isEqualTo("");
    }
}
