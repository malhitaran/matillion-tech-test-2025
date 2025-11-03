# Column Profiling Feature

## What problem does it solve?
Provides quick data profiling for uploaded CSVs: inferred data types and basic numeric summaries per column to aid validation and exploration.

## How it works
After ingesting a CSV via `POST /api/analysis/ingestCsv`, you can request profiles for that analysis ID. The service parses the persisted CSV and, for each column:
- Infers type: STRING, INTEGER, DECIMAL, or BOOLEAN
- Reports null and unique non-null counts
- For numeric columns: min, max, and mean (null for non-numeric)

## API Usage
- GET `/api/analysis/{id}/profile`
  - 200 OK with JSON array of profiles
  - 404 if the analysis ID does not exist

### Example response item
```json
{
  "columnName": "number",
  "inferredType": "INTEGER",
  "nullCount": 0,
  "uniqueCount": 10,
  "min": 1.0,
  "max": 99.0,
  "mean": 41.9
}
```

## Notes
- Unique counts exclude empty values.
- Numeric detection supports integers and decimals with optional sign.
- Boolean detection treats `true`/`false` (case-insensitive) as booleans.

