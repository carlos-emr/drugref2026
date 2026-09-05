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

import java.util.Hashtable;

import org.junit.jupiter.api.Test;

/**
 * Pins the shape of the struct {@code getUpdateStatus()} returns: every value a
 * String (XML-RPC cannot carry null), and the lifecycle transitions an EMR client
 * relies on to tell "still running" from "died".
 */
class UpdateStatusTest {

    @Test
    void shouldReportFailureWithReason_afterFail() {
        UpdateStatus status = UpdateStatus.get();
        status.begin();
        status.step("downloading");
        assertThat(status.isRunning()).isTrue();

        status.fail("failed during 'downloading': IOException: HTTP 404");

        assertThat(status.isRunning()).isFalse();
        Hashtable<String, Object> struct = status.toStruct();
        assertThat(struct.get("state")).isEqualTo("FAILED");
        assertThat(struct.get("step")).isEqualTo("downloading");
        assertThat(struct.get("message")).isEqualTo("failed during 'downloading': IOException: HTTP 404");
        assertThat((String) struct.get("startedAt")).isNotEmpty();
        assertThat((String) struct.get("finishedAt")).isNotEmpty();
    }

    @Test
    void shouldClearPreviousOutcome_whenNewAttemptBegins() {
        UpdateStatus status = UpdateStatus.get();
        status.begin();
        status.fail("boom");

        status.begin();

        Hashtable<String, Object> struct = status.toStruct();
        assertThat(struct.get("state")).isEqualTo("RUNNING");
        assertThat(struct.get("message")).isEqualTo("");
        assertThat(struct.get("finishedAt")).isEqualTo("");
        status.succeed("done");
        assertThat(status.toStruct().get("state")).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldNeverCarryNulls_inStruct() {
        UpdateStatus status = UpdateStatus.get();
        status.begin();
        status.step(null);
        status.succeed(null);
        for (Object value : status.toStruct().values()) {
            assertThat(value).isInstanceOf(String.class);
        }
    }
}
