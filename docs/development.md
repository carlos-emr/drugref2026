# Development Guide

## DevContainer Setup

The project includes a full DevContainer configuration for VS Code / GitHub Codespaces with Tomcat 11 and MariaDB.

### Prerequisites

- Docker and Docker Compose
- VS Code with the Dev Containers extension (or GitHub Codespaces)

### Getting Started

1. Open the project in VS Code
2. When prompted, click "Reopen in Container" (or run `Dev Containers: Reopen in Container` from the command palette)
3. The container will build and run `postCreateCommand`:
   ```
   mvn -B dependency:go-offline dependency:sources dependency:resolve && make install
   ```
4. The application will be available at `http://localhost:8080/drugref2/`

### Container Architecture

```
docker-compose.yml
  |
  +-- drugref service (Tomcat 11 + JDK 21)
  |     Container: drugref2026-tomcat-dev
  |     Ports: 8080 (HTTP), 8000 (JPDA debug)
  |     Image: tomcat:11.0-jdk21-temurin + Maven, MariaDB client, Node.js, gh, Claude CLI
  |
  +-- db service (MariaDB 10.11)
        Container: drugref2026-mariadb-dev
        Port: 3306
        Database: drugref2
        Memory limit: 2G, CPU limit: 2
```

Environment variables are loaded from `.devcontainer/development/config/local.env`.

### Port Mappings

| Port | Service |
|------|---------|
| 8080 | Tomcat HTTP |
| 8000 | JPDA remote debugging |
| 3306 | MariaDB |

### VS Code Extensions (auto-installed)

- Git Blame
- Java Extension Pack
- Red Hat Fabric8 Analytics
- Claude Code

---

## Build Scripts

The DevContainer includes custom scripts in `.devcontainer/development/scripts/`:

### `make` -- Build and Deploy

```sh
make install                # Build and deploy to Tomcat (incremental by default)
make install --run-tests    # Build, deploy, then run tests
make install --force-clean  # Force a full clean build
make install --no-clean     # Skip clean even if changes suggest it's needed
make clean                  # Clean project and remove deployed webapp
make help                   # Show usage
```

The `make install` command:
1. Stops Tomcat if running
2. Determines if a clean build is needed by inspecting git state:
   - Deleted/renamed Java files -> clean needed (stale .class files)
   - Changed pom.xml -> clean needed (dependency changes)
   - Otherwise -> incremental build (faster)
3. Runs `mvn package war:exploded` (with `-DskipTests` unless `--run-tests`)
4. Copies the exploded WAR to `/usr/local/tomcat/webapps/drugref2`
5. Starts Tomcat with JPDA debugging

### `server` -- Tomcat Control

```sh
server start    # Start Tomcat with JPDA debugging on port 8000
server stop     # Stop Tomcat
server restart  # Stop then start
server log      # Tail catalina.out
```

---

## Building Manually

If not using the DevContainer scripts:

```sh
# Full build (skip tests)
mvn clean package -DskipTests

# Build with tests
mvn clean package

# Run tests only
mvn test

# Build exploded WAR for faster deployment
mvn package war:exploded -DskipTests
```

The WAR file is produced at `target/drugref2.war`. Deploy to Tomcat's `webapps/` directory.

---

## Debugging

### Remote Debugging (JPDA)

The DevContainer starts Tomcat with JPDA debugging enabled on port 8000. Connect your IDE's remote debugger to `localhost:8000`.

The Dockerfile sets:
```
CATALINA_OPTS="--add-opens java.base/java.net=ALL-UNNAMED"
JPDA_ADDRESS=8000
JPDA_TRANSPORT=dt_socket
```

### SQL Logging

Hibernate SQL logging is enabled by default in `spring_config.xml`:
- `showSql=true` -- prints SQL to stdout
- `hibernate.format_sql=true` -- pretty-prints the SQL

### Application Logging

Log4j 2 is configured in `src/main/resources/log4j2.xml`. The application uses `MiscUtils.getLogger()` which returns a Log4j Logger named after the calling class.

---

## Testing

### Unit Tests

Tests use JUnit 5 and AssertJ. Located at `src/test/java/io/github/carlos_emr/drugref2026/`.

```sh
mvn test
```

Current test coverage includes:
- `SimpleXmlRpcClientTest` -- XML-RPC client/server correctness:
  - XXE (XML External Entity) attack prevention
  - Fault response parsing
  - Array and struct deserialization
  - HTTP error handling

### Manual Testing

**JSP test pages** (accessible in browser):

| Page | URL | Purpose |
|------|-----|---------|
| `index.jsp` | `/drugref2/` | Search UI with XML-RPC proxy |
| `test4.jsp` | `/drugref2/test4.jsp?name=aspirin` | Drug search returning JSON/text |
| `Update.jsp` | `/drugref2/Update.jsp` | Trigger data import, view results |
| `addDescriptorAndStrength.jsp` | `/drugref2/addDescriptorAndStrength.jsp` | Run post-import enhancements |

**XML-RPC testing** (using curl):
```sh
curl -X POST http://localhost:8080/drugref2/DrugrefService \
  -H "Content-Type: text/xml" \
  -d '<?xml version="1.0"?>
<methodCall>
  <methodName>identify</methodName>
  <params/>
</methodCall>'
```

---

## Project Structure

```
drugref2026/
  pom.xml                           -- Maven build configuration
  .devcontainer/
    devcontainer.json                -- VS Code DevContainer config
    docker-compose.yml               -- Tomcat + MariaDB services
    development/
      Dockerfile                     -- Dev container image (Tomcat 11 + JDK 21)
      config/
        local.env                    -- Environment variables
        drugref2.properties          -- Dev database properties
      scripts/
        make                         -- Build and deploy script
        server                       -- Tomcat start/stop/restart
        startup.sh                   -- Alternative startup script
  src/
    main/
      java/io/github/carlos_emr/drugref2026/
        ...                          -- Application source code
      resources/
        drugref.properties           -- Default configuration
        spring_config.xml            -- Spring beans configuration
        log4j.xml                    -- Logging configuration
        interactions-holbrook.txt    -- Bundled drug interaction data
        META-INF/persistence.xml     -- JPA persistence unit
      webapp/
        WEB-INF/web.xml              -- Servlet configuration
        index.jsp                    -- Search UI
        Update.jsp                   -- Data import trigger
        test4.jsp                    -- Search test page
    test/
      java/io/github/carlos_emr/drugref2026/
        ...                          -- Test source code
  docs/                              -- This documentation
```
