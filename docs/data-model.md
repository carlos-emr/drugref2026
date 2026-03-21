# Data Model

All data comes from Health Canada's Drug Product Database (DPD) extracts, supplemented by application-generated search indexes and bundled interaction data.

JPA entity classes are in `src/main/java/io/github/carlos_emr/drugref2026/ca/dpd/`.

## Table Overview

### DPD Tables (from Health Canada)

These tables are **dropped and recreated** on every data import. They mirror the CSV files in the DPD extract ZIPs.

#### cd_drug_product
Core drug product records. Central table that all detail tables reference via `drug_code`.

| Column | Description |
|--------|-------------|
| id | Auto-increment primary key |
| drug_code | Health Canada's unique numeric drug code |
| drug_identification_number | DIN (8-digit identifier) |
| brand_name | Brand/trade name |
| descriptor | Additional name descriptor |
| pediatric_flag | Pediatric use indicator |
| accession_number | Regulatory accession number |
| number_of_ais | Number of active ingredients |
| last_update_date | Last Health Canada update |
| ai_group_no | Active Ingredient group number (links generic equivalents) |
| company_code | Manufacturer code |
| class | Drug class |
| product_categorization | Product category |

#### cd_active_ingredients
Active pharmaceutical ingredients for each drug product.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| active_ingredient_code | Ingredient identifier |
| ingredient | Ingredient name (e.g., `ACETAMINOPHEN`) |
| strength | Numeric strength value |
| strength_unit | Unit (MG, ML, etc.) |
| strength_type | Type of strength measurement |
| dosage_value | Dosage amount |
| dosageUnit | Dosage unit |

#### cd_companies
Manufacturer and distributor information.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| company_code | Company identifier |
| company_name | Company name |
| company_type | Manufacturer or Distributor |

#### cd_drug_status
Regulatory status history for each product.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| currentStatusFlag | `Y` for current status |
| status | MARKETED, CANCELLED POST MARKET, DORMANT, APPROVED |
| historyDate | Date of status change |

#### cd_form
Pharmaceutical dosage forms.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| pharmCdFormCode | Numeric form code |
| pharmaceuticalCdForm | Form name (TABLET, CAPSULE, CREAM, etc.) |

#### cd_inactive_products
Discontinued or cancelled drug products.

| Column | Description |
|--------|-------------|
| drug_code | Drug code |
| drugIdentificationNumber | DIN |
| brandName | Brand name |
| historyDate | Date of discontinuation |

#### cd_packaging
Package configurations.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| upc | Universal Product Code |
| packageSizeUnit | ML, G, EA |
| packageType | Package type |
| packageSize | Numeric size |

#### cd_pharmaceutical_std
Quality standards (USP, BP, PhEur, etc.).

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| pharmaceuticalStd | Standard name |

#### cd_route
Routes of administration.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| routeOfAdministrationCode | Route code |
| routeOfAdministration | ORAL, TOPICAL, INTRAVENOUS, etc. |

#### cd_schedule
Regulatory scheduling.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| schedule | OTC, Prescription, Schedule I-IV, Targeted |

#### cd_therapeutic_class
ATC (Anatomical Therapeutic Chemical) classification.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| tcAtcNumber | ATC code (e.g., `N02BE01`) |
| tcAtc | English ATC description |
| tcAhfs | AHFS classification (deprecated July 2022) |
| tcAtcf | French ATC description |

#### cd_veterinary_species
Species approvals for veterinary products.

| Column | Description |
|--------|-------------|
| drug_code | FK to cd_drug_product |
| vetSpecies | Species (CATTLE, SWINE, DOGS, etc.) |
| vetSubSpecies | Sub-species |

---

### Application Tables

#### cd_drug_search
Pre-built search index for fast type-ahead lookups. Not part of the original DPD data -- built during import by `ConfigureSearchData`.

| Column | Description |
|--------|-------------|
| id | Auto-increment primary key |
| drugCode | Meaning varies by category (drug_code for brands, aiCode+formCode for generics) |
| category | Search category code (8, 11, 12, 13, 14, 18, 19) |
| name | Searchable drug name |

**Categories:**
- **8** -- ATC code + description
- **11** -- Generic single ingredient
- **12** -- Generic multi-ingredient (composite)
- **13** -- Brand name
- **14** -- Individual active ingredient
- **18** -- New generic single-ingredient with form and strength
- **19** -- New generic multi-ingredient with form and strength

#### link_generic_brand
Maps generic drugs to their brand-name equivalents. Built by `ConfigureSearchData.importGenerics()`.

| Column | Description |
|--------|-------------|
| pkId | Auto-increment primary key |
| id | Search table ID (generic entry) |
| drugCode | Brand product drug code (as String) |

#### history
Database update audit trail.

| Column | Description |
|--------|-------------|
| id | Auto-increment primary key |
| dateTime | Timestamp of the update |
| action | Description (e.g., `"update db"`) |

#### interactions
Drug-drug interaction records from the bundled Holbrook dataset.

| Column | Description |
|--------|-------------|
| affectingatc | ATC code of the affecting drug (7 chars) |
| affectedatc | ATC code of the affected drug |
| effect | Effect description |
| significance | Severity rating |
| evidence | Evidence quality |
| comment | Clinical commentary |
| affectingdrug | Name of affecting drug |
| affecteddrug | Name of affected drug |

Unique constraint on `(affectingatc, affectedatc, effect)`.

---

## Relationships

All DPD detail tables link to `cd_drug_product` through the `drug_code` field (one-to-many):

```
cd_drug_product (drug_code)
  |-- cd_active_ingredients
  |-- cd_companies
  |-- cd_drug_status
  |-- cd_form
  |-- cd_packaging
  |-- cd_pharmaceutical_std
  |-- cd_route
  |-- cd_schedule
  |-- cd_therapeutic_class
  +-- cd_veterinary_species
```

The `cd_drug_search` and `link_generic_brand` tables form a separate search layer:

```
cd_drug_search (category 11/12 generics)
  +-- link_generic_brand --> cd_drug_product (brand products)
```

## Database Indexes

Created during import on:
- `drug_code` on all detail tables
- `ai_group_no` on `cd_drug_product`
- `drug_identification_number` on `cd_inactive_products`
- `id`, `drug_code`, `category`, `name` on `cd_drug_search`
