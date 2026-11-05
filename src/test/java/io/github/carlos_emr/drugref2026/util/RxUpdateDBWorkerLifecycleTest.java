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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class RxUpdateDBWorkerLifecycleTest {
    @Test
    void shouldWaitForWorkerRecovery_beforeShutdownCanClosePools() throws Exception {
        var lifecycle = new RxUpdateDBWorker.WorkerLifecycle();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finishRecovery = new CountDownLatch(1);
        CountDownLatch shutdownStarted = new CountDownLatch(1);
        CountDownLatch poolsCanClose = new CountDownLatch(1);
        AtomicBoolean workerInterrupted = new AtomicBoolean();
        AtomicBoolean shutdownInterruptRestored = new AtomicBoolean();
        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                finishRecovery.await();
            } catch (InterruptedException unexpected) {
                workerInterrupted.set(true);
            }
        }, "synthetic-drugref-update");
        Thread shutdown = new Thread(() -> {
            Thread.currentThread().interrupt();
            shutdownStarted.countDown();
            lifecycle.stopAndAwait();
            shutdownInterruptRestored.set(Thread.currentThread().isInterrupted());
            poolsCanClose.countDown();
        }, "synthetic-drugref-shutdown");
        try {
            lifecycle.start(worker);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            shutdown.start();
            assertThat(shutdownStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(poolsCanClose.await(100, TimeUnit.MILLISECONDS)).isFalse();
            assertThatThrownBy(() -> lifecycle.start(new Thread()))
                    .isInstanceOf(IllegalStateException.class);
            finishRecovery.countDown();
            assertThat(poolsCanClose.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(workerInterrupted.get()).isFalse();
            assertThat(shutdownInterruptRestored.get()).isTrue();
            assertThatThrownBy(() -> lifecycle.start(new Thread()))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            finishRecovery.countDown();
            worker.join(2000);
            shutdown.join(2000);
        }
    }

    @Test
    void shouldAllowNextUpdate_afterPreviousWorkerFinishes() throws Exception {
        var lifecycle = new RxUpdateDBWorker.WorkerLifecycle();
        Thread first = new Thread(() -> { });
        Thread second = new Thread(() -> { });
        lifecycle.start(first);
        first.join(2000);
        lifecycle.start(second);
        lifecycle.stopAndAwait();
        assertThat(second.getState()).isEqualTo(Thread.State.TERMINATED);
    }

    /** A completed update releases its slot immediately while shutdown still awaits its thread tail. */
    @Test
    void shouldAllowRetryAfterCleanup_andStillJoinThePreviousThread() throws Exception {
        var lifecycle = new RxUpdateDBWorker.WorkerLifecycle();
        CountDownLatch cleanupFinished = new CountDownLatch(1);
        CountDownLatch allowThreadExit = new CountDownLatch(1);
        CountDownLatch shutdownFinished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread first = new Thread(() -> {
            lifecycle.finished(Thread.currentThread());
            cleanupFinished.countDown();
            try {
                allowThreadExit.await();
            } catch (InterruptedException unexpected) {
                interrupted.set(true);
            }
        }, "synthetic-drugref-completed-update");
        Thread second = new Thread(() -> { }, "synthetic-drugref-retry");
        Thread shutdown = new Thread(() -> {
            lifecycle.stopAndAwait();
            shutdownFinished.countDown();
        }, "synthetic-drugref-shutdown");
        try {
            lifecycle.start(first);
            assertThat(cleanupFinished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(first.isAlive()).isTrue();
            lifecycle.start(second);
            second.join(2000);
            assertThat(second.getState()).isEqualTo(Thread.State.TERMINATED);
            shutdown.start();
            assertThat(shutdownFinished.await(100, TimeUnit.MILLISECONDS)).isFalse();
            allowThreadExit.countDown();
            assertThat(shutdownFinished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(interrupted.get()).isFalse();
        } finally {
            allowThreadExit.countDown();
            first.join(2000);
            second.join(2000);
            shutdown.join(2000);
        }
    }
}
