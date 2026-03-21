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

There are two ways to trigger a data update:

### 1. XML-RPC: `updateDB()`
Call the `updateDB()` method via XML-RPC. Returns `"running"` if started, `"updating"` if already in progress. The import runs in a background thread (`RxUpdateDBWorker`).

### 2. Update.jsp
Navigate to `/drugref2/Update.jsp` in a browser. This triggers the import directly and displays results (row counts, timing) in the browser.

## Import Pipeline

When triggered, `RxUpdateDBWorker.run()` executes these steps sequentially:

### Step 1: DPD Import (`DPDImport.doItDifferent()`)

1. **Download** ZIP files from Health Canada using `getZipStream(url)`
2. **Drop** all existing DPD tables if they exist:
   - cd_drug_product, cd_companies, cd_active_ingredients, cd_drug_status, cd_form, cd_inactive_products, cd_packaging, cd_pharmaceutical_std, cd_route, cd_schedule, cd_therapeutic_class, cd_veterinary_species, interactions
3. **Create** fresh tables with schema from `getDPDTables()`
4. **Parse** CSV files from each ZIP using `RecordParser.getDPDObject()`:
   - Handles ISO-8859-1 to UTF-8 encoding conversion
   - Parses dates in `dd-MMM-yy` format (e.g., `03-DEC-2018`)
   - Persists each record as a JPA entity
5. **Import interactions** from `interactions-holbrook.txt` into the `interactions` table
6. **Build search index** via `ConfigureSearchData.importSearchData()` (see below)
7. **Create database indexes** on key columns for query performance
8. Returns total execution time in milliseconds

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

## Concurrency

- The `Drugref.UPDATE_DB` static boolean flag prevents concurrent updates
- `updateDB()` checks this flag before launching `RxUpdateDBWorker`
- `getLastUpdateTime()` returns `"updating"` while the flag is set
- The flag is cleared at the end of `RxUpdateDBWorker.run()`

## Key Files

| File | Purpose |
|------|---------|
| `ca/dpd/fetch/DPDImport.java` | Main import orchestrator |
| `ca/dpd/fetch/RecordParser.java` | CSV parser |
| `ca/dpd/fetch/ConfigureSearchData.java` | Search index builder |
| `ca/dpd/fetch/TempNewGenericImport.java` | Generic drug entry generator |
| `ca/dpd/history/HistoryUtil.java` | Update history recording |
| `util/RxUpdateDBWorker.java` | Background thread coordinator |
| `src/main/resources/interactions-holbrook.txt` | Bundled interaction data |
