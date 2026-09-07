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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;

/**
 * Process-wide record of the most recent database update attempt.
 *
 * <p>Before this existed the only signal an EMR client had was
 * {@code getLastUpdateTime()} answering the literal string {@code "updating"}
 * while {@link io.github.carlos_emr.drugref2026.Drugref#UPDATE_DB} was set. When the
 * worker thread died with an uncaught exception that flag was never cleared, so
 * the client saw "updating" forever and had no way to learn that the update had
 * in fact failed, or why. This holder is what {@code getUpdateStatus()} reports.
 *
 * <p>All state lives in one instance for the life of the JVM; it is not
 * persisted. A JVM restart returns it to {@link State#IDLE}, which is also the
 * honest answer after a restart (no update is running).
 */
public final class UpdateStatus {

    /** Lifecycle of a single update attempt. */
    public enum State { IDLE, RUNNING, SUCCEEDED, FAILED }

    private static final UpdateStatus INSTANCE = new UpdateStatus();

    private State state = State.IDLE;
    private Date startedAt;
    private Date finishedAt;
    private String step = "";
    private String message = "";

    private UpdateStatus() {
    }

    /** @return the single process-wide status holder */
    public static UpdateStatus get() {
        return INSTANCE;
    }

    /** Marks a new attempt as running and clears the outcome of the previous one. */
    public synchronized void begin() {
        state = State.RUNNING;
        startedAt = new Date();
        finishedAt = null;
        step = "starting";
        message = "";
    }

    /** Records the pipeline step currently executing, for progress reporting. */
    public synchronized void step(String currentStep) {
        step = currentStep == null ? "" : currentStep;
    }

    /** Marks the running attempt as finished successfully. */
    public synchronized void succeed(String summary) {
        state = State.SUCCEEDED;
        finishedAt = new Date();
        step = "done";
        message = summary == null ? "" : summary;
    }

    /** Marks the running attempt as failed; {@code reason} is what the operator sees. */
    public synchronized void fail(String reason) {
        state = State.FAILED;
        finishedAt = new Date();
        message = reason == null ? "" : reason;
    }

    /** @return {@code true} while an attempt is in progress */
    public synchronized boolean isRunning() {
        return state == State.RUNNING;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized String getStep() {
        return step;
    }

    public synchronized String getMessage() {
        return message;
    }

    /**
     * Snapshot for XML-RPC transport. Every value is a {@link String} so the
     * struct round-trips through any XML-RPC client; absent timestamps are
     * empty strings rather than nulls, which XML-RPC cannot carry.
     *
     * <p>Keys: {@code state} (IDLE/RUNNING/SUCCEEDED/FAILED), {@code step},
     * {@code message}, {@code startedAt}, {@code finishedAt} (both
     * {@code yyyy-MM-dd HH:mm:ss} in the server's zone, matching
     * {@code getLastUpdateTime()}).
     */
    public synchronized Hashtable<String, Object> toStruct() {
        Hashtable<String, Object> struct = new Hashtable<>();
        struct.put("state", state.name());
        struct.put("step", step);
        struct.put("message", message);
        struct.put("startedAt", format(startedAt));
        struct.put("finishedAt", format(finishedAt));
        return struct;
    }

    private static String format(Date date) {
        if (date == null) {
            return "";
        }
        // Locale.ENGLISH: this is an API value with a documented format, not display
        // text. A server defaulting to e.g. a Thai or Hindi locale would otherwise emit
        // localized digits or a non-Gregorian year and break every client parsing it.
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(date);
    }
}
