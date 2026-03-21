# Architecture

## Request Flow

Every client interaction follows this path:

```
1. EMR client sends HTTP POST to /drugref2/DrugrefService
   Content-Type: text/xml
   Body: XML-RPC methodCall document

2. DrugrefService.doPost() receives the request
   Sets response content type to text/xml

3. SimpleXmlRpcServer.execute() processes the XML-RPC envelope:
   a. XmlRpcUtils.parseRequest() deserializes XML into method name + parameters
   b. invokeMethod() uses Java reflection to find and call the matching
      public method on the Drugref handler instance
   c. Type coercion converts XML-RPC types to Java types:
      - int/i4 -> Integer/int
      - string -> String
      - boolean -> Boolean/boolean
      - array -> Vector
      - struct -> Hashtable
   d. XmlRpcUtils.buildResponse() serializes the return value back to XML-RPC

4. Drugref.methodName() executes:
   - Most methods delegate to TablesDao for JPA/JPQL queries
   - Interaction checks delegate to InteractionsChecker (Medi-Span)
   - The Holbrook plugin handles ATC-based interaction queries

5. Response flows back as XML-RPC methodResponse
```

## Component Diagram

```
+------------------------------------------------------------------+
|                         Tomcat 11                                 |
|                                                                   |
|  +--------------------+     +-------------------------------+     |
|  | web.xml            |     | JSP Pages                     |     |
|  | - StartUp listener |     | - index.jsp (search UI)       |     |
|  | - DrugrefService   |     | - Update.jsp (trigger import) |     |
|  +--------+-----------+     | - test4.jsp (search test)     |     |
|           |                 +-------------------------------+     |
|           v                                                       |
|  +--------------------+                                           |
|  | DrugrefService     |  (Jakarta HttpServlet)                    |
|  +--------+-----------+                                           |
|           |                                                       |
|           v                                                       |
|  +--------------------+                                           |
|  | SimpleXmlRpcServer |  (reflection-based method dispatch)       |
|  +--------+-----------+                                           |
|           |                                                       |
|           v                                                       |
|  +--------------------+     +---------------------------+         |
|  | Drugref            +---->| InteractionsChecker       |         |
|  | (XML-RPC handler)  |     | (Medi-Span, licensed)     |         |
|  +--------+-----------+     +---------------------------+         |
|           |                                                       |
|           v                                                       |
|  +--------------------+     +---------------------------+         |
|  | TablesDao          +---->| Holbrook plugin           |         |
|  | (Spring @Repository)|    | (ATC interaction queries) |         |
|  +--------+-----------+     +---------------------------+         |
|           |                                                       |
|           v                                                       |
|  +--------------------+                                           |
|  | JPA / Hibernate    |  (EntityManager, JPQL queries)            |
|  +--------+-----------+                                           |
|           |                                                       |
|           v                                                       |
|  +--------------------+                                           |
|  | HikariCP           |  (connection pool, 32 max / 2 min idle)   |
|  +--------+-----------+                                           |
+-----------|---------------------------------------------------+   |
            v                                                       |
   +--------------------+                                           |
   | MySQL / MariaDB    |                                           |
   | or PostgreSQL      |                                           |
   +--------------------+                                           |
```

## Startup Sequence

1. **Tomcat deploys the WAR** and triggers the `StartUp` servlet context listener.

2. **StartUp.contextInitialized()**:
   - Constructs the properties file path from `DRUGREF_PROPERTIES_PATH` (web.xml) + context name.
     Default: `${user.home}/drugref2.properties`
   - Loads external properties via `DrugrefProperties.loader()`, falling back to `../../` relative path.
   - Calls `InteractionsCheckerFactory.start()` to begin loading Medi-Span data if a licence key is configured.

3. **Spring context initializes** (triggered by `SpringUtils` static initializer):
   - Loads `spring_config.xml`
   - Creates HikariCP DataSource
   - Creates JPA EntityManagerFactory (scans `io.github.carlos_emr.drugref2026.ca.dpd` for entities)
   - Hibernate validates the schema (`hbm2ddl.auto=validate`)
   - Creates `TablesDao` bean and registers the Holbrook plugin

4. **First request** to `/DrugrefService` triggers `DrugrefService.init()`:
   - Creates `SimpleXmlRpcServer` with a new `Drugref` handler instance

## Plugin System

The plugin architecture allows extensible drug attribute lookups:

```
Plugin<T> (interface)
  |
  +-- PluginImpl<T> (generic base)
        |
        +-- DrugrefPlugin (concrete)
              |
              +-- wraps Holbrook instance
```

- **Plugin interface** defines: `setName()`, `setVersion()`, `setProvides()`, `setPlugin()`, `register()`
- **DrugrefPlugin** wraps the Holbrook interaction checker and exposes its capabilities
- **DrugrefApi** provides a `get(attribute, key)` method with caching and function dispatch
- **TablesDao** registers plugins at construction time and routes `fetch()` calls through them

### Holbrook Plugin

The Holbrook plugin queries the `interactions` database table for ATC-based drug interactions:

```sql
SELECT hi FROM Interactions hi
WHERE hi.affectingatc = :affecting AND hi.affectedatc = :affected
```

It performs pairwise comparisons across all drugs in a list and returns interaction records with significance, effect, evidence, and comments.

## Threading Model

| Thread | Purpose | Source |
|--------|---------|--------|
| Tomcat worker threads | Handle HTTP requests to `/DrugrefService` and JSP pages | Tomcat thread pool |
| `RxUpdateDBWorker` | Background DPD import (launched by `Drugref.updateDB()`) | `new Thread().start()` |
| `drugref-task-*` | Medi-Span data loading and status polling | Spring `ThreadPoolTaskExecutor` (2-4 threads) |
| `InteractionsCheckerFactory` scheduler | Periodic Medi-Span status checks | `ScheduledExecutorService` |

**Concurrency controls:**
- `Drugref.UPDATE_DB` static boolean prevents concurrent database updates
- Several `Drugref` methods are `synchronized` (e.g., `get_drug_by_DIN`, `get_drug_pkey_from_DIN`)
- `RxUpdateDBWorker.run()` is synchronized on `this`

## Security Features

- **XXE Prevention**: `DocumentBuilderFactory` disables DOCTYPE declarations and external entities in `XmlRpcUtils`
- **Parameterized Queries**: JPA named parameters prevent SQL injection
- **Connection Pooling**: HikariCP manages database connections with timeouts
- **License Compliance**: Medi-Span interaction data has a hard 60-day expiry cutoff

## Design Patterns

| Pattern | Usage |
|---------|-------|
| Singleton | `DrugrefProperties`, `SpringUtils`, `InteractionsCheckerFactory` |
| Factory | `SpringUtils.getBean()`, `InteractionsCheckerFactory.getInteractionChecker()` |
| DAO/Repository | `TablesDao` with Spring `@Repository` |
| Plugin | `Plugin` interface with Holbrook implementation |
| Reflection-based Dispatch | `SimpleXmlRpcServer` invokes handler methods by name |
| Background Worker | `RxUpdateDBWorker` extends `Thread` |
