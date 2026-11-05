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

import java.sql.DriverManager;
import java.sql.SQLException;

import org.mariadb.jdbc.client.util.SchedulerProvider;
import org.springframework.context.ConfigurableApplicationContext;

/** Owns shutdown without loading SpringUtils, which would create a context on an unused deployment. */
final class DrugrefShutdownResources {
    private static final DrugrefShutdownResources WEBAPP = new DrugrefShutdownResources();
    private ConfigurableApplicationContext context;
    private boolean stopping;

    /** Register the eagerly created context so servlet shutdown can find it without creating one. */
    static <T extends ConfigurableApplicationContext> T register(T created) {
        WEBAPP.track(created);
        return created;
    }

    /** Release this web application's resources, preserving container-owned JDBC resources. */
    static void closeWebapp() {
        WEBAPP.close(DrugrefShutdownResources.class.getClassLoader());
    }

    /** Reject duplicate or late contexts and immediately close the rejected instance. */
    synchronized void track(ConfigurableApplicationContext created) {
        if (stopping || context != null) {
            created.close();
            throw new IllegalStateException("DrugRef application context is already registered or shutting down");
        }
        context = created;
    }

    /** Close the Spring pool before its driver scheduler and registrations, including on failure. */
    void close(ClassLoader owner) {
        ConfigurableApplicationContext registered;
        synchronized (this) {
            if (stopping) return;
            stopping = true;
            registered = context;
            context = null;
        }
        try {
            // Spring destroys the task executor, Hibernate factory and Hikari pool before driver threads.
            if (registered != null) registered.close();
        } finally {
            // A shared container-level driver belongs to other applications as well.
            try {
                if (owns(owner, SchedulerProvider.class.getClassLoader())) {
                    SchedulerProvider.close();
                }
            } finally {
                DriverManager.drivers().filter(driver -> owns(owner, driver.getClass().getClassLoader()))
                        .forEach(driver -> {
                            try {
                                DriverManager.deregisterDriver(driver);
                            } catch (SQLException failure) {
                                MiscUtils.getLogger().error("Unable to unregister a DrugRef JDBC driver", failure);
                            }
                        });
            }
        }
    }

    /** A resource belongs to this deployment only when its loader is the owner or a child. */
    private static boolean owns(ClassLoader owner, ClassLoader resource) {
        if (owner == null) return false;
        for (ClassLoader current = resource; current != null; current = current.getParent()) {
            if (current == owner) return true;
        }
        return false;
    }
}
