# Drugref2026 Overview

Drugref2026 is a pharmaceutical drug reference web service for Canada. It provides an XML-RPC API for searching, looking up, and checking interactions between drugs listed in Health Canada's Drug Product Database (DPD).

The application is designed to be integrated into Electronic Medical Record (EMR) systems such as OSCAR/CARLOS, where it powers drug search, prescribing decision support, and drug-drug interaction checking.

## What It Does

- **Drug Search** -- Type-ahead search across brand names, generics, ATC codes, and active ingredients
- **Drug Lookup** -- Retrieve full drug details by DIN (Drug Identification Number) or internal ID
- **Drug Interaction Checking** -- Two systems: Holbrook (ATC-based, bundled) and Medi-Span (DIN-based, licensed)
- **Allergy Warnings** -- Check a drug against a patient's known allergies
- **ATC Classification** -- Look up Anatomical Therapeutic Chemical codes and names
- **Data Updates** -- Pull the latest drug data from Health Canada on demand

## Architecture at a Glance

```
EMR Client (OSCAR/CARLOS)
    |
    | HTTP POST (XML-RPC)
    v
DrugrefService (Jakarta Servlet)
    |
    v
SimpleXmlRpcServer (reflection-based dispatch)
    |
    v
Drugref (handler -- 20+ public XML-RPC methods)
    |
    +---> TablesDao (Spring @Repository, JPA/JPQL queries)
    |         |
    |         v
    |     MySQL/MariaDB or PostgreSQL
    |
    +---> InteractionsChecker (Medi-Span, licensed)
    |
    +---> Holbrook plugin (ATC-based interactions from bundled data)
```

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java (JDK) | 21 |
| Application Server | Apache Tomcat | 11.0 |
| Servlet API | Jakarta EE | 6.1 |
| Dependency Injection | Spring Framework | 7.0.6 |
| ORM | Hibernate ORM | 7.0.0.Final |
| Connection Pool | HikariCP | 6.2.1 |
| Database | MySQL/MariaDB or PostgreSQL | 9.1.0 / 42.7.4 (drivers) |
| Logging | Log4j 2 | 2.24.3 |
| Build | Maven | 3.x |
| Testing | JUnit 5, AssertJ | 5.11.4, 3.26.3 |
| RPC Protocol | Custom XML-RPC 1.0 | Built-in |
| License | AGPL-3.0 | -- |

## Package Structure

All source code lives under `src/main/java/io/github/carlos_emr/drugref2026/`:

```
drugref2026/
  Drugref.java                   -- Main XML-RPC handler (all public API methods)
  DrugrefService.java            -- HTTP servlet (XML-RPC endpoint)

  ca/dpd/                        -- JPA entity classes (database tables)
    TablesDao.java               -- Spring DAO for all drug queries
    CdDrugProduct.java           -- Drug product entity
    CdActiveIngredients.java     -- Active ingredients entity
    CdDrugSearch.java            -- Search index entity
    (+ 11 more entity classes)

  ca/dpd/fetch/                  -- Data import pipeline
    DPDImport.java               -- Downloads and imports DPD data from Health Canada
    RecordParser.java            -- CSV parser for DPD extract files
    ConfigureSearchData.java     -- Builds the search index tables
    TempNewGenericImport.java    -- Generates generic drug search entries

  ca/dpd/history/
    HistoryUtil.java             -- Records update timestamps

  dinInteractionCheck/           -- Medi-Span drug interaction checking
    InteractionsChecker.java     -- Parses and queries Medi-Span data
    InteractionsCheckerFactory.java -- Lifecycle management and polling

  plugin/                        -- Plugin system for extensible drug lookups
    Plugin.java                  -- Plugin interface
    Holbrook.java                -- ATC-based interaction queries

  util/                          -- Infrastructure utilities
    SimpleXmlRpcServer.java      -- Custom XML-RPC server implementation
    SimpleXmlRpcClient.java      -- XML-RPC client for testing
    XmlRpcUtils.java             -- XML serialization/deserialization
    DrugrefProperties.java       -- Singleton configuration holder
    SpringUtils.java             -- Spring ApplicationContext holder
    RxUpdateDBWorker.java        -- Background data update thread
    StartUp.java                 -- Servlet context listener (bootstrap)
```

## Related Documentation

- [Architecture](architecture.md) -- Component design, request flow, threading model
- [API Reference](api-reference.md) -- All XML-RPC methods with parameters and examples
- [Data Model](data-model.md) -- Database schema and entity descriptions
- [Data Import](data-import.md) -- How the DPD import pipeline works
- [Drug Interactions](drug-interactions.md) -- Holbrook and Medi-Span interaction systems
- [Configuration](configuration.md) -- Properties, Spring config, database setup
- [Development Guide](development.md) -- DevContainer, building, testing, debugging
