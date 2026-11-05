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

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mariadb.jdbc.client.util.SchedulerProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.support.GenericApplicationContext;

@ResourceLock("java.sql.DriverManager")
class DrugrefShutdownResourcesTest {
    private List<Driver> originalDrivers;

    @BeforeEach
    void preserveTestJvmDrivers() throws Exception {
        Class.forName("org.h2.Driver");
        Class.forName("org.mariadb.jdbc.Driver");
        originalDrivers = DriverManager.drivers().toList();
    }

    @AfterEach
    void restoreTestJvmDrivers() throws Exception {
        List<Driver> remaining = DriverManager.drivers().toList();
        for (Driver driver : originalDrivers) {
            if (!remaining.contains(driver)) DriverManager.registerDriver(driver);
        }
    }

    @Test
    void shouldCloseApplicationPool_beforeCancellingDriverTasks() throws Exception {
        var scheduler = SchedulerProvider.getTimeoutScheduler();
        scheduler.schedule(() -> { }, 1, TimeUnit.DAYS);
        DrugrefDataSource pool = new DrugrefDataSource();
        pool.setDriverClassName("org.h2.Driver");
        pool.setJdbcUrl("jdbc:h2:mem:drugref_shutdown");
        pool.setUsername("sa");
        pool.setPassword("");
        pool.setMinimumIdle(0);
        pool.setMaximumPoolSize(1);
        try (var ignored = pool.getConnection()) {
            assertThat(pool.isRunning()).isTrue();
        }
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("pool", DrugrefDataSource.class, () -> pool,
                definition -> definition.setDestroyMethodName("close"));
        AtomicBoolean driverOpenAtBeanDestruction = new AtomicBoolean();
        context.registerBean("driverDependent", DisposableBean.class,
                () -> () -> driverOpenAtBeanDestruction.set(!scheduler.isShutdown()),
                definition -> definition.setDependsOn("pool"));
        context.refresh();
        DrugrefShutdownResources resources = new DrugrefShutdownResources();
        resources.track(context);
        resources.close(getClass().getClassLoader());
        assertThat(driverOpenAtBeanDestruction.get()).isTrue();
        assertThat(context.isActive()).isFalse();
        assertThat(pool.isClosed()).isTrue();
        assertThat(scheduler.isShutdown()).isTrue();
        assertThat(scheduler.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        assertThat(DriverManager.drivers().toList())
                .noneMatch(driver -> driver.getClass().getClassLoader() == getClass().getClassLoader());
        resources.close(getClass().getClassLoader());
    }

    @Test
    void shouldLeaveContainerOwnedSchedulerRunning() {
        var scheduler = SchedulerProvider.getTimeoutScheduler();
        try {
            ClassLoader childWebapp = new ClassLoader(getClass().getClassLoader()) { };
            new DrugrefShutdownResources().close(childWebapp);
            assertThat(scheduler.isShutdown()).isFalse();
            assertThat(DriverManager.drivers().toList()).containsAll(originalDrivers);
        } finally {
            SchedulerProvider.close();
        }
    }

    @Test
    void shouldCloseDriverTasks_whenNoSpringContextWasRegistered() {
        var scheduler = SchedulerProvider.getTimeoutScheduler();
        new DrugrefShutdownResources().close(getClass().getClassLoader());
        assertThat(scheduler.isShutdown()).isTrue();
    }
}
