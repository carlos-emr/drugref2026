# Configuration

## Properties Files

### Loading Order

1. **Bundled defaults** (`src/main/resources/drugref.properties`) -- loaded at class init time via `DrugrefProperties` static initializer
2. **External override** (`${user.home}/drugref2.properties`) -- loaded at deployment by `StartUp` listener, overlays the defaults

The external file path is constructed from:
- `DRUGREF_PROPERTIES_PATH` context parameter in `web.xml` (default: `${user.home}/`)
- The servlet context name (default: `drugref2`)
- Result: `${user.home}/drugref2.properties`

If the primary path is not found, `StartUp` falls back to `../../${user.home}/drugref2.properties`.

In the DevContainer, the Dockerfile copies a properties file to `/root/drugref2.properties` and sets `CATALINA_OPTS` with `-Ddrugref_override_properties=/root/drugref2.properties`.

### Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `db_url` | JDBC connection URL | `jdbc:mariadb://127.0.0.1:3306/drugref` |
| `db_user` | Database username | `drugref` |
| `db_password` | Database password | *(empty)* |
| `db_driver` | JDBC driver class | `org.mariadb.jdbc.Driver` |
| `sort_down_mfg_tagged_generics` | Sort manufacturer-tagged generics lower in search | `false` |
| `interaction_base_url` | Medi-Span data server URL | `https://download.oscar-emr.com/ws/rs/accounts` |
| `scheduled_timer` | Medi-Span status poll interval (ms) | `300000` (5 minutes) |
| `licence_key` | Medi-Span license key | (none) |
| `DPD_BASE_URL` | Health Canada DPD download URL; the three archive names are appended to it. Point it at a local mirror to run an update offline. | `https://www.canada.ca/content/dam/hc-sc/documents/services/drug-product-database` |

> **The bundled `db_user` / `db_password` defaults are non-functional placeholders —
> supplying real credentials in the external overlay is REQUIRED.** They name an
> unprivileged account with an empty password, so a deployment without an overlay
> fails to connect rather than falling back to a working database login. That is
> deliberate: the bundled file ships inside the WAR and is published in this source
> tree, so any real credential in it would be a published credential. Earlier
> releases defaulted to the database superuser `root` with a known password, which
> meant a missing or unreadable overlay silently connected as `root`.
>
> Grant the account only what DrugRef needs on its own schema, and set `db_url`,
> `db_user` and `db_password` in `${user.home}/drugref2.properties` (see **Loading
> Order** above). In the CARLOS Debian deployment the `carlos-emr-drugref` package
> renders this file for you.

### Database Support

The application supports two database backends, selected by the JDBC URL:

| Database | Driver | URL Pattern |
|----------|--------|-------------|
| MySQL / MariaDB | `org.mariadb.jdbc.Driver` | `jdbc:mariadb://host:port/dbname` |
| PostgreSQL | `org.postgresql.Driver` | `jdbc:postgresql://host:port/dbname` |

MariaDB Connector/J 3.5.11 supplies the MySQL/MariaDB driver. Existing
`com.mysql.cj.jdbc.Driver` / `com.mysql.jdbc.Driver` properties and `jdbc:mysql:`
URLs are translated internally; operators do not need to rewrite existing files.
The pool, import helper and startup table-swap recovery use the same compatibility
adapter. `serverTimezone` remains accepted, and UTC is the default only when no
timezone is configured. Query options in `db_url` are supported without appending
a second `?`.

The adapter preserves legacy TLS policy: the default and `sslMode=PREFERRED`
encrypt when the server supports TLS without verifying its certificate, and
fall back to plaintext only when TLS is unavailable. `sslMode=trust` requires
TLS without certificate verification. Explicit `sslMode=verify-ca` and
`sslMode=verify-full` retain certificate and hostname verification respectively;
the legacy `VERIFY_CA` and `VERIFY_IDENTITY` spellings are also accepted.
Native `sslMode=allow` retains its certificate-verifying behavior.

Empty strings read through numeric JDBC getters continue to produce zero by
default, matching the former driver. `emptyStringsConvertToZero=false` retains
the explicit opt-out, and string reads still return the original empty string.

`DrugrefProperties.isMysql()` recognizes both MySQL and MariaDB URLs so imported
search indexes continue to use the MySQL/MariaDB prefix-index syntax.
`isPostgres()` retains its existing meaning.

---

## Spring Configuration (`spring_config.xml`)

Located at `src/main/resources/spring_config.xml`.

### DataSource (HikariCP)

```xml
<bean id="dataSource" class="io.github.carlos_emr.drugref2026.util.DrugrefDataSource" destroy-method="close">
    <property name="driverClassName" value="${db_driver}" />
    <property name="jdbcUrl" value="${db_url}" />
    <property name="username" value="${db_user}" />
    <property name="password" value="${db_password}" />
    <property name="maximumPoolSize" value="32" />
    <property name="minimumIdle" value="2" />
    <property name="connectionTimeout" value="10000" />
    <property name="autoCommit" value="false" />
</bean>
```

Property placeholders (`${db_url}`, etc.) are resolved by `SpringPropertyConfigurer`, which injects the `DrugrefProperties` singleton.

### JPA / Hibernate

```xml
<bean id="entityManagerFactory"
      class="org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean">
    <property name="packagesToScan"
              value="io.github.carlos_emr.drugref2026.ca.dpd" />
    <property name="jpaProperties">
        <props>
            <prop key="hibernate.format_sql">true</prop>
            <prop key="hibernate.hbm2ddl.auto">validate</prop>
        </props>
    </property>
</bean>
```

- **Entity scan**: `io.github.carlos_emr.drugref2026.ca.dpd`
- **Schema validation**: `hibernate.hbm2ddl.auto=validate` -- Hibernate validates that JPA entities match the database schema at startup. Tables are created/managed by `DPDImport`, not by Hibernate DDL.
- **SQL logging**: `showSql=true` and `hibernate.format_sql=true`

### Transaction Management

```xml
<bean id="txManager"
      class="org.springframework.orm.jpa.JpaTransactionManager"
      autowire="byName" />
<tx:annotation-driven transaction-manager="txManager" />
```

Spring's `@Transactional` annotation is enabled for declarative transaction management.

### Task Executor

```xml
<bean id="taskScheduler"
      class="org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor">
    <property name="corePoolSize" value="2" />
    <property name="maxPoolSize" value="4" />
    <property name="queueCapacity" value="10" />
    <property name="threadNamePrefix" value="drugref-task-" />
</bean>
```

Used by `InteractionsCheckerFactory` for background Medi-Span data loading and status checks.

### Component Scan

```xml
<context:component-scan base-package="io.github.carlos_emr.drugref2026" />
```

Discovers `@Repository`, `@Service`, and `@Component` annotated classes (primarily `TablesDao`).

---

## Web Configuration (`web.xml`)

Located at `src/main/webapp/WEB-INF/web.xml`. Jakarta EE 6.1.

### Servlet

```xml
<servlet>
    <servlet-name>DrugrefService</servlet-name>
    <servlet-class>io.github.carlos_emr.drugref2026.DrugrefService</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>DrugrefService</servlet-name>
    <url-pattern>/DrugrefService</url-pattern>
</servlet-mapping>
```

XML-RPC endpoint at `/drugref2/DrugrefService`.

### Startup Listener

```xml
<listener>
    <listener-class>io.github.carlos_emr.drugref2026.util.StartUp</listener-class>
</listener>
```

Initializes properties and Medi-Span interaction checker on deployment.

During undeploy, DrugRef stops interaction polling and rejects new database
updates, then waits for the current update to finish its commit or recovery before
closing Spring and Hikari. This can delay undeploy while a DPD download or rebuild
is active; it prevents the old deployment from changing shared tables after a new
deployment starts recovery.

### Context Parameters

```xml
<context-param>
    <param-name>DRUGREF_PROPERTIES_PATH</param-name>
    <param-value>${user.home}/</param-value>
</context-param>

<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath:spring_config.xml</param-value>
</context-param>
```

### Session

- Session timeout: 30 minutes
- No welcome file: DrugRef is a machine-to-machine XML-RPC service and serves no
  browser pages. A GET of the context root returns 404.

---

## Logging (`log4j.xml`)

Located at `src/main/resources/log4j.xml`. Uses Log4j 2 with XML configuration. Log levels and appenders are configured here.

---

## Key Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `drugref.properties` | `src/main/resources/` | Bundled default properties |
| `drugref2.properties` | `${user.home}/` (external) | Environment-specific overrides |
| `spring_config.xml` | `src/main/resources/` | Spring beans, DataSource, JPA, transactions |
| `web.xml` | `src/main/webapp/WEB-INF/` | Servlet mapping, listeners, context params |
| `log4j.xml` | `src/main/resources/` | Logging configuration |
| `persistence.xml` | `src/main/resources/META-INF/` | JPA persistence unit definition |

## Connector regression tests

`mvn -B -ntp verify` runs the unit suite and packages the WAR. CI also runs
`MariaDbConnectorTest` against physical MariaDB 11.8.8. For a local disposable
server, provide `DRUGREF_TEST_MARIADB_URL` (for example,
`jdbc:mariadb://127.0.0.1:3306/`), `DRUGREF_TEST_MARIADB_USER`, and
`DRUGREF_TEST_MARIADB_PASSWORD` to the same Maven command. The user must be able
to create and drop databases: the tests create a uniquely named
`drugref_connector_*` schema and remove that schema afterward. Without the URL,
only these physical-server tests are skipped.
