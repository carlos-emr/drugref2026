# Drug Interaction Checking

Drugref2026 provides two independent drug interaction checking systems:

1. **Holbrook** -- ATC-based interactions from a bundled dataset, queried via the database
2. **Medi-Span** -- DIN-based interactions from a licensed, remotely-hosted dataset, parsed in memory

## Holbrook (ATC-Based Interactions)

### Overview

The Holbrook system uses the `interactions` database table, populated from the bundled `interactions-holbrook.txt` file during data import. It performs pairwise ATC code comparisons.

### Data Source

`src/main/resources/interactions-holbrook.txt` -- CSV format:

```
"id","affectingATC","affectedATC","severity","potency","precedence","comment","affectingDrug","affectedDrug"
```

Includes interactions from the Liverpool Paxlovid interaction tables and RxNorm data.

### How It Works

The Holbrook plugin (`plugin/Holbrook.java`) extends `DrugrefApi` and registers an `interactions_byATC` capability. When invoked:

1. Takes a list of drugs (with ATC codes)
2. Performs pairwise comparison of all ATC codes
3. Queries the `interactions` table:
   ```sql
   SELECT hi FROM Interactions hi
   WHERE hi.affectingatc = :affecting AND hi.affectedatc = :affected
   ```
4. Returns interaction records with: affecting/affected ATCs, drug names, significance, effect, evidence, comment

### Integration

The Holbrook plugin is registered by `TablesDao` at construction time via `DrugrefPlugin`. It is accessible through the `fetch("interactions_byATC", drugList)` XML-RPC method.

---

## Medi-Span (DIN-Based Interactions)

### Overview

The Medi-Span system uses a licensed drug interaction database distributed as a fixed-width text file. The data is downloaded from a remote server, parsed into in-memory data structures, and queried at runtime.

### Lifecycle

```
Application Startup
  |
  v
InteractionsCheckerFactory.start()
  |-- Check for licence_key in properties
  |-- If no key: skip (checker remains inactive)
  |
  v
Background load task:
  |-- Download data from: {interaction_base_url}/file?currentVersion=0&licenceKey={key}
  |-- Parse fixed-width text into InteractionsChecker
  |-- Send audit: {interaction_base_url}/audit?audit={checksum}&licenceKey={key}
  |-- Get disclaimer: {interaction_base_url}/disclaimer?licenceKey={key}
  |
  v
Periodic status polling (every scheduled_timer ms, default 300000 = 5 min):
  |-- Check: {interaction_base_url}/status?release={version}&licenceKey={key}
  |-- Response: "NEW_VERSION_AVAILABLE" -> reload
  |             numeric (ms) -> schedule next check
  |             "ERROR..." -> stop polling
```

### Data Format

The Medi-Span data file uses fixed-width text records. Each line starts with a 4-character record type:

| Record Type | Description |
|-------------|-------------|
| `D011` | Drug formulation -- substance codes + therapeutic classes (primary) |
| `D012` | Drug formulation extension -- additional therapeutic classes |
| `I021` | Substance names (identifies Food, Ethanol) |
| `I031` | DIN-to-substance mapping |
| `M011` | Interaction master record -- two substances + severity/onset/documentation |
| `M013` | Interacting drug detail records |
| `M019` | Interaction text segments (warning, effect, mechanism, management, discussion, references) |
| `V011` | Copyright notice |
| `V012` | Publication metadata (date, quarter, release, edition, expiry) |
| `V019` | Additional metadata |
| `ERRO` | Error messages |

### In-Memory Data Structures

`InteractionsChecker` maintains three primary maps:

```
dinmap:              DIN string -> DinRecord (substance codes: kdc1, kdc2, kdc3)
drugFormulationMap:  KDC string -> DrugFormulation (therapeutic class list)
interactionMap:      "cnum1:cnum2" -> InteractionRecord (full interaction detail)
```

### Lookup Algorithm

When `interaction_by_regional_identifier(listOfDins, minSignificates)` is called:

**Phase 1: Pairwise drug-drug checks (O(n^2))**

For each pair of DINs:
1. Look up DIN in `dinmap` to get `DinRecord` (substance codes)
2. Look up substance code in `drugFormulationMap` to get therapeutic class list
3. Cross-product all class codes from both drugs
4. Check each `"class1:class2"` pair in `interactionMap`
5. Collect all matching `InteractionRecord` objects

```
DIN "02241985"
  --> DinRecord { kdc1: "12345" }
    --> DrugFormulation { classList: ["N02BE0100", "N02BE0101"] }

DIN "00123456"
  --> DinRecord { kdc1: "67890" }
    --> DrugFormulation { classList: ["B01AC0600"] }

Check: "N02BE0100:B01AC0600" in interactionMap -> InteractionRecord (if exists)
Check: "N02BE0101:B01AC0600" in interactionMap -> ...
Check: "B01AC0600:N02BE0100" in interactionMap -> ... (reverse direction)
```

**Phase 2: Food and ethanol checks**

For each individual DIN:
1. Look up substance codes and therapeutic classes (same as above)
2. Check each class against known Food and Ethanol substance codes
3. Collect any food/alcohol interaction records

**Phase 3: Format results**

Each `InteractionRecord` is converted to a Hashtable with:
- `name`: Warning text (WAR)
- `body`: HTML combining Effect, Mechanism, Management, Discussion, References, Copyright, Issue, Disclaimer
- `significance`: Severity level
- `author`: "Medi-Span"

### DIN Fallback

If a DIN is not found in `dinmap`, the checker calls `findClassByDinOrLikeDin(din)` which queries the database for similar DINs using `TablesDao.findLikeDins()`.

### InteractionRecord Fields

| Field | Source Record | Description |
|-------|-------------|-------------|
| `cnum1`, `cnum2` | M011 | Substance codes (5 chars each, map key) |
| `severity` | M011 | Numeric severity |
| `onset` | M011 | Onset timing |
| `doc` | M011 | Documentation level |
| `WAR` | M019 (WAR) | Warning text |
| `EFF` | M019 (EFF) | Effect description |
| `MEC` | M019 (MEC) | Mechanism of interaction |
| `MAN` | M019 (MAN) | Management/recommendation |
| `DIS` | M019 (DIS) | Clinical discussion |
| `REF` | M019 (REF) | Literature references |

Text fields may contain `@A@` and `@B@` placeholders that are replaced with the actual drug names.

### License Expiry Handling

The Medi-Span data has a built-in expiration date (`V012` record):

| Days Past Expiry | Behavior |
|-----------------|----------|
| 0 (not expired) | Normal operation |
| 1-50 | Normal operation |
| 50-60 | Warning returned, checks still run |
| >60 | Hard cutoff -- checks blocked, only error returned |

### Configuration

| Property | Description | Default |
|----------|-------------|---------|
| `licence_key` | Medi-Span license key | (none -- checker disabled) |
| `interaction_base_url` | Base URL for data/status endpoints | `https://download.oscar-emr.com/ws/rs/accounts` |
| `scheduled_timer` | Status polling interval (ms) | `300000` (5 minutes) |

### Key Files

| File | Purpose |
|------|---------|
| `dinInteractionCheck/InteractionsChecker.java` | Data parser and query engine |
| `dinInteractionCheck/InteractionsCheckerFactory.java` | Lifecycle management, download, polling |
| `dinInteractionCheck/InteractionRecord.java` | Interaction data container |
| `dinInteractionCheck/DinRecord.java` | DIN-to-substance mapping |
| `dinInteractionCheck/DrugFormulation.java` | Substance formulation with class list |
| `dinInteractionCheck/InteractingDrugRecord.java` | M013 detail records |
| `dinInteractionCheck/InteractionTextRecord.java` | M019 text segments |
| `dinInteractionCheck/LineCursor.java` | Fixed-width field parser utility |
| `plugin/Holbrook.java` | ATC-based interaction queries |
| `plugin/DrugrefPlugin.java` | Plugin registration wrapper |
