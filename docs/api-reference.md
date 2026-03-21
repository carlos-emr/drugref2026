# XML-RPC API Reference

## Protocol

- **Transport**: HTTP POST
- **Content-Type**: `text/xml`
- **Endpoint**: `/drugref2/DrugrefService`
- **Specification**: XML-RPC 1.0

All public methods on the `Drugref` class are callable via XML-RPC. Parameters and return values are serialized using standard XML-RPC types: `int`, `boolean`, `string`, `double`, `dateTime.iso8601`, `base64`, `array` (Java `Vector`), and `struct` (Java `Hashtable`).

## Request/Response Format

**Request:**
```xml
<?xml version="1.0"?>
<methodCall>
  <methodName>get_drug_by_DIN</methodName>
  <params>
    <param><value><string>02241985</string></value></param>
    <param><value><boolean>1</boolean></value></param>
  </params>
</methodCall>
```

**Response:**
```xml
<?xml version="1.0"?>
<methodResponse>
  <params>
    <param>
      <value>
        <array><data>
          <value><struct>
            <member><name>din</name><value><string>02241985</string></value></member>
            <member><name>name</name><value><string>ASPIRIN</string></value></member>
          </struct></value>
        </data></array>
      </value>
    </param>
  </params>
</methodResponse>
```

---

## Drug Search Methods

### `list_search_element(String searchStr)`
Basic drug name search.

### `list_search_element2(String searchStr)`
Extended search that resolves available formulation types using the AI (Active Ingredient) code.

### `list_search_element3(String searchStr)`
Enhanced search (algorithm v4) with **left-anchored** matching. This is the primary search method used by EMR clients. Results are capped at 60 rows. Two-phase search:
1. Direct prefix match on brand names (cat 13) and new generics (cat 18, 19)
2. Multi-keyword AND match for remaining slots

### `list_search_element3_right(String searchStr)`
Same as `list_search_element3` but with **right-anchored** (suffix) matching.

### `list_search_element_route(String str, String route)`
Search filtered by route of administration (e.g., `"ORAL"`, `"TOPICAL"`, `"IV"`).

### `list_search_element_select_categories(String str, Vector cat)`
Search filtered by specific drug categories. Left-anchored.

### `list_search_element_select_categories_right(String str, Vector cat)`
Same as above but with right-anchored matching.

**All search methods return:** `Vector<Hashtable>` with keys:
- `name` -- drug name
- `category` -- search category code (see below)
- `id` -- search table primary key

### Search Category Codes

| Code | Meaning | Examples |
|------|---------|---------|
| 8 | ATC classification | `N02BE01 - ACETAMINOPHEN` |
| 11 | Generic single ingredient | `ACETAMINOPHEN` |
| 12 | Generic multi-ingredient | `ACETAMINOPHEN / CODEINE` |
| 13 | Brand name | `TYLENOL` |
| 14 | Individual active ingredient | `ACETAMINOPHEN` |
| 18 | New generic single + form | `ACETAMINOPHEN 500MG TABLET` |
| 19 | New generic multi + form | `ACETAMINOPHEN 300MG / CODEINE 30MG TABLET` |

---

## Drug Lookup Methods

### `get_drug(String pKey, boolean html)`
Retrieve full drug details by internal primary key (as String).

### `get_drug(int pKey, boolean html)`
Convenience overload accepting an integer primary key.

### `get_drug_by_DIN(String DIN, boolean bvalue)`
Retrieve full drug details by DIN (Drug Identification Number). Resolves DIN to internal key, then fetches. Returns `null` if DIN not found.

### `get_drug_2(String pkey, boolean html)`
Retrieve drug details with category-aware handling:
- **Category 13** (brand): fetched directly
- **Category 18/19** (generic composite): drug code is `"aiCode+formCode"`, resolved to a representative example

**Lookup methods return:** `Vector<Hashtable>` with drug record fields including DIN, brand name, active ingredients, ATC codes, route, form, etc.

---

## Identifier Resolution Methods

### `get_drug_pkey_from_DIN(String DIN)`
Resolve a DIN to the internal database primary key. Returns `0` if not found.

### `get_drug_pkey_from_drug_id(String drugId)`
Resolve a numeric drug ID (as String) to the internal primary key. Returns `0` for non-numeric or missing values.

### `get_drug_id_from_DIN(String DIN)`
Resolve a DIN to the DPD drug code. Returns `0` if not found.

---

## Drug Property Methods

### `get_form(String pKey)`
Get the pharmaceutical form (e.g., TABLET, CAPSULE, CREAM) for a drug.

### `get_generic_name(String drugID)`
Get the generic (non-proprietary) name for a drug.

### `get_inactive_date(String str)`
Get the date a drug product was withdrawn or discontinued.

---

## Brand/Generic Methods

### `list_brands_from_element(String drugID)`
List all brand-name products associated with a given active ingredient.

---

## Therapeutic Classification Methods

### `get_atc_name(String atc)`
Get the therapeutic class name for an ATC code (e.g., `"N02BE01"` returns acetaminophen/paracetamol info).

### `get_atcs_by_din(String din)`
Get all ATC codes associated with a DIN. Returns `Vector<String>`.

### `list_drug_class(Vector Dclass)`
Get all drugs belonging to the specified therapeutic class codes.

---

## Allergy Warning Methods

### `get_allergy_warnings(String atcCode, Vector allergies)`
Check a drug (by ATC code) against a patient's known allergies. Returns matching warnings.

### `get_allergy_classes(Vector allergies)`
Get the drug classes associated with the given allergy identifiers.

---

## Drug Interaction Methods

### `interaction_by_regional_identifier(Vector listOfDins, int minSignificates)`
Check for drug-drug interactions among a list of DINs (the patient's current medications).

**Process:**
1. Pairwise interaction check between all DINs (O(n^2))
2. Food and ethanol interaction check for each individual DIN
3. Results converted to Hashtable format for XML-RPC transport

**Returns:** `Vector<Hashtable>` where each entry contains:

| Key | Type | Description |
|-----|------|-------------|
| `id` | String | Interaction identifier |
| `updated_at` | Date | Data publication date |
| `name` | String | Warning text summary |
| `body` | String | HTML-formatted detail (effect, mechanism, management, discussion, references, copyright, disclaimer) |
| `significance` | String | Severity level (1=minor, 2=moderate, 3=major) |
| `evidence` | String | Evidence quality |
| `reference` | String | Reference citation |
| `author` | String | `"Medi-Span"` |
| `trusted` | Boolean | Always `true` |

**License expiry behavior:**
- Active license: normal operation
- 50-60 days expired: warning prepended, checks still run
- >60 days expired: checks blocked, only expiry error returned

---

## Database Management Methods

### `updateDB()`
Trigger a full database update from Health Canada's DPD data. Launches `RxUpdateDBWorker` in a background thread.

**Returns:** `"running"` if a new update was started, `"updating"` if one is already in progress.

### `getLastUpdateTime()`
Get the timestamp of the most recent successful database update.

**Returns:** Date/time string, `"updating"` if an update is in progress, or `null` if no history exists.

---

## Service Metadata Methods

### `identify()`
Returns a human-readable identification string for the service.

### `version()`
Returns the service version string.

### `list_available_services()`
Returns a `Vector` of all available XML-RPC method names.

### `list_capabilities()`
Returns a `Hashtable` of the service's capabilities.

### `fetch(String attribute, Vector key)`
Generic attribute fetch that delegates to the plugin system. Used for extensible lookups.
