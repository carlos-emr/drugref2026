# Data Import Pipeline

Drugref2026 periodically imports drug data from Health Canada's Drug Product Database (DPD). The import uses a **full-replace strategy** -- all tables are dropped and recreated from scratch on every update.

## Data Sources

### Health Canada DPD
The primary data source. ZIP files containing CSV extracts are downloaded from:

```
https://www.canada.ca/content/dam/hc-sc/documents/services/drug-product-database
```

This URL is configurable via the `DPD_BASE_URL` property.

**Files downloaded:**
| File | Contents |
|------|----------|
| `allfiles.zip` | Active drug products (all DPD tables) |
| `Allfiles_ia-Oct10.zip` | Legacy inactive products (2018 format) |
| `inactive.zip` | Current inactive products table |

Each ZIP contains CSV files for each DPD table (drug.txt, comp.txt, form.txt, route.txt, etc.).

### Holbrook Interactions
Bundled as a classpath resource at `src/main/resources/interactions-holbrook.txt`. This is a CSV file containing ATC-based drug interaction records with fields: ID, affecting ATC, affected ATC, effect, significance, evidence, comment, affecting drug name, affected drug name.

Includes interactions from the Liverpool Paxlovid interaction tables and RxNorm data.

## Triggering an Update

### XML-RPC: `updateDB()`
Call the `updateDB()` method via XML-RPC. Returns `"running"` if started, `"updating"` if already in progress. The import runs in a background thread (`RxUpdateDBWorker`).

This is the only supported trigger. CARLOS uses it (`RxDrugRef.updateDB()`, reached
from **Administration → Update DrugRef Database**). The former `Update.jsp` browser
page has been removed: it ran the same import inline on an unauthenticated GET, with
no CSRF protection and no in-progress guard.

### XML-RPC: `getUpdateStatus()`
Returns a struct describing the most recent attempt in this JVM: `state`
(`IDLE`, `RUNNING`, `SUCCEEDED`, `FAILED`), `step`, `message`, `startedAt`,
`finishedAt` and `lastUpdate`. Poll it after `updateDB()`: `getLastUpdateTime()`
alone cannot distinguish an update that is still running from one that failed,
and before this method existed a failed update was reported as `"updating"`
indefinitely. See [api-reference.md](api-reference.md#database-management-methods).

### Network requirements
The worker fetches the archives itself with the JVM's HTTP client, so the
DrugRef JVM needs outbound HTTPS to `www.canada.ca` (or to whatever
`DPD_BASE_URL` names). Behind a proxy, set the standard JVM properties
(`-Dhttps.proxyHost=... -Dhttps.proxyPort=...`); behind a TLS-intercepting
proxy, add its CA to the JVM trust store. A server that cannot reach the site
now fails the update cleanly during the download step, with the reason in
`getUpdateStatus()`, and keeps its current dataset.

## Import Pipeline

When triggered, `RxUpdateDBWorker.run()` executes these steps sequentially. A
failure at any step propagates to the worker, which restores the previous
dataset (see **Failure handling** below) and records the reason.

### Step 0: Download (`DpdDownloader.download()`)

All three archives are fetched to temp files **before anything in the database
is touched**, with connect and read timeouts, a `User-Agent`, an HTTP status
check, a `Content-Length` check, and a test that each file opens as a non-empty
ZIP. A 404, a proxy block page served with status 200, or a truncated transfer
aborts the update here, at no cost.

The earlier pipeline dropped every table first and opened each URL with a bare
`URL.openStream()` whose failure was swallowed; a server that could not reach
Health Canada was left with an empty drug database.

### Step 0b: Table swap (`DpdTableSwap.backupLiveTables()`)

Every table the import rebuilds (the thirteen `cd_*`/`interactions` tables plus
`cd_drug_search` and `link_generic_brand`) is renamed to `<table>_prev`. The
importer then creates fresh tables under the live names. `history` and
`utility` are not swapped. Drug lookups are degraded while the import runs, as
they always were; `getLastUpdateTime()` answers `"updating"` for the duration.

### Step 1: DPD Import (`DPDImport.importDpd()`)

1. **Create** fresh tables with schema from `getDPDTables()` (any leftover live
   table is dropped first)
2. **Parse** CSV files from each ZIP using `RecordParser.getDPDObject()`:
   - Handles ISO-8859-1 to UTF-8 encoding conversion
   - Parses dates in `dd-MMM-yy` format (e.g., `03-DEC-2018`)
   - Persists each record as a JPA entity, flushing and clearing the persistence
     context every 500 rows so the whole extract is never resident at once
     (DrugRef shares its JVM with CARLOS in the Debian deployment)
   - A parse failure aborts the update instead of being logged and skipped
3. **Import interactions** from `interactions-holbrook.txt` into the `interactions` table
4. **Build search index** via `ConfigureSearchData.importSearchData()` (see below)
5. **Create database indexes** on key columns for query performance (an index
   that cannot be created is logged and skipped; it affects speed, not data)
6. Returns total execution time in milliseconds

### Step 2: Generic Drug Import (`TempNewGenericImport.run()`)

Generates synthetic search entries for generic drugs (categories 18 and 19):

1. Queries AI (Active Ingredient) group numbers from `cd_drug_product`
2. For each group, combines ingredient names with strength and form information
3. Creates human-readable names like `"ACETAMINOPHEN 500MG TABLET"`
4. Multi-ingredient products become category 19: `"ACETAMINOPHEN 300MG / CODEINE 30MG TABLET"`
5. Inserts into `cd_drug_search` with `drugCode = "aiCode+formCode"`

### Step 3: ISMP Flagging (`DPDImport.setISMPmeds()`)

Flags ISMP (Institute for Safe Medication Practices) high-alert medications in the database. These drugs require special safeguards to reduce the risk of medication errors.

### Step 4: History Recording (`HistoryUtil.addUpdateHistory()`)

Inserts a row into the `history` table with the current timestamp and action `"update db"`.

### Step 5: Search Name Enhancement

Two post-processing steps improve search result quality:

- **`addDescriptorToSearchName()`** -- Appends pharmaceutical form descriptors to drug names
- **`addStrengthToBrandName()`** -- Appends strength values to brand name entries

### Step 6: Statistics

Stores update metadata in `Drugref.DB_INFO`:
- Table row counts
- Import timing (data import minutes, generic import minutes)
- Lists of modified search entries (added descriptors, added strengths)

## Search Index Building (`ConfigureSearchData`)

This runs as part of Step 1 and builds the `cd_drug_search` table:

1. **Category 8 (ATC):** Inserts ATC code + description pairs
2. **Category 13 (Brand Names):** Inserts brand names from `cd_drug_product`, excluding common manufacturer prefixes (APO-, NOVO-, MYLAN-, etc.)
3. **Category 14 (Ingredients):** Inserts individual active ingredient names
4. **Categories 11/12 (Generics):** Creates generic single and multi-ingredient entries
5. **`link_generic_brand` mappings:** Links generic entries to their brand-name equivalents by AI group number

Categories 18 and 19 (new generics with form/strength) are populated separately in Step 2.

## CSV Parsing (`RecordParser`)

The `RecordParser` class handles CSV parsing from DPD ZIP files:

- Uses Ostermiller CSV utilities for parsing
- Supports hexadecimal conversion for certain fields
- Handles date parsing in `dd-MMM-yy` format
- Monitors memory usage during bulk import
- Entities parsed: CdDrugProduct, CdActiveIngredients, CdCompanies, CdDrugStatus, CdForm, CdInactiveProducts, CdPackaging, CdPharmaceuticalStd, CdRoute, CdSchedule, CdTherapeuticClass, CdVeterinarySpecies

## Failure handling

The worker wraps the whole pipeline. On any exception it:

1. logs the failure with its stack trace,
2. drops the partially built live tables and renames every `<table>_prev` back
   (`DpdTableSwap.restoreBackupTables()`), so the dataset is exactly what it was,
3. records `FAILED`, the step, and the root-cause message in `UpdateStatus`, and
4. clears `Drugref.UPDATE_DB` in a `finally` block, so a failed update can be
   retried without restarting DrugRef.

On success the `_prev` tables are dropped as the last step. If the JVM exits
mid-update (an out-of-memory exit, a service restart) the `_prev` set is still
there at the next start; `StartUp` calls `DpdTableSwap.restoreIfInterrupted()`
before Spring and Hibernate come up, which restores the previous dataset so the
schema validation and the first lookups see complete tables.

## Concurrency

- The `Drugref.UPDATE_DB` static boolean flag prevents concurrent updates
- `updateDB()` checks and sets the flag under a lock before launching `RxUpdateDBWorker`
- `getLastUpdateTime()` returns `"updating"` while the flag is set
- The flag is cleared in the worker's `finally` block, whatever the outcome

## Key Files

| File | Purpose |
|------|---------|
| `ca/dpd/fetch/DpdDownloader.java` | Fetches and validates the three archives before any table is touched |
| `ca/dpd/fetch/DpdTableSwap.java` | Renames the live tables aside for the import and restores them on failure |
| `ca/dpd/fetch/DPDImport.java` | Main import orchestrator |
| `ca/dpd/fetch/RecordParser.java` | CSV parser |
| `ca/dpd/fetch/ConfigureSearchData.java` | Search index builder |
| `ca/dpd/fetch/TempNewGenericImport.java` | Generic drug entry generator |
| `ca/dpd/history/HistoryUtil.java` | Update history recording |
| `util/RxUpdateDBWorker.java` | Background thread coordinator: download, swap, import, rollback |
| `util/UpdateStatus.java` | Outcome of the last attempt, reported by `getUpdateStatus()` |
| `src/main/resources/interactions-holbrook.txt` | Bundled interaction data |
