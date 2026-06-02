use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;
use thiserror::Error;

const SQL_SNAPSHOT_FORMAT: &str = "mortar-sql-snapshot-v1";
const MORTAR_METADATA_FORMAT: &str = "mortar-metadata-v1";
const MORTAR_SOURCE_MAP_FORMAT: &str = "mortar-source-map-v1";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct QueryInspection {
    pub sql: String,
    pub tables: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SqlSnapshotFile {
    pub format: String,
    pub snapshots: Vec<SqlSnapshot>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SqlSnapshot {
    pub name: String,
    pub sql: String,
    pub parameters: Vec<SqlSnapshotParameter>,
    pub metadata: SqlSnapshotMetadata,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SqlSnapshotParameter {
    pub position: u32,
    pub java_type: String,
    pub value: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SqlSnapshotMetadata {
    pub tables: Vec<String>,
    pub columns: Vec<String>,
    pub joins: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarMetadataFile {
    pub format: String,
    pub entities: Vec<MortarEntityMetadata>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarEntityMetadata {
    pub java_type: String,
    pub table: String,
    pub alias: String,
    pub columns: Vec<MortarColumnMetadata>,
    pub relations: Vec<MortarRelationMetadata>,
    #[serde(default)]
    pub queries: Vec<MortarQueryMetadata>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarColumnMetadata {
    pub property: String,
    pub column: String,
    pub java_type: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarRelationMetadata {
    pub property: String,
    pub local_column: String,
    pub target_table: String,
    pub target_alias: String,
    pub target_column: String,
    #[serde(default)]
    pub nullable: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarQueryMetadata {
    pub id: String,
    pub name: String,
    pub shape: String,
    pub generated_source: MortarQuerySourceMetadata,
    pub parameters: Vec<MortarQueryParameterMetadata>,
    pub row_type: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarQuerySourceMetadata {
    pub java_type: String,
    pub member: String,
    pub generated_type: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarQueryParameterMetadata {
    pub name: String,
    pub java_type: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarSourceMapFile {
    pub format: String,
    pub metadata: MortarSourceMapMetadataLink,
    pub queries: Vec<MortarSourceMapQuery>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarSourceMapMetadataLink {
    pub format: String,
    pub path: String,
    pub fingerprint: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarSourceMapQuery {
    pub id: String,
    pub entity_type: String,
    pub generated_entity_type: String,
    pub generated_read_namespace: String,
    pub generated_member: String,
    pub query_name: String,
    pub snapshot: String,
    pub row_type: String,
    pub parameters: Vec<MortarQueryParameterMetadata>,
    pub source_anchor: MortarSourceAnchor,
    pub freshness: MortarSourceMapFreshness,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarSourceAnchor {
    pub kind: String,
    pub java_type: String,
    pub member: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarSourceMapFreshness {
    pub fingerprint: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SourceMapFreshnessIssue {
    pub query_id: Option<String>,
    pub message: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DatabaseSchema {
    pub tables: Vec<DatabaseTable>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DatabaseTable {
    pub name: String,
    pub columns: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SchemaDrift {
    pub table: String,
    pub column: Option<String>,
    pub message: String,
}

#[derive(Debug, Error)]
pub enum MortarCompilerError {
    #[error("SQL cannot be blank")]
    BlankSql,
    #[error("Snapshot format must be mortar-sql-snapshot-v1")]
    InvalidSnapshotFormat,
    #[error("Snapshot name cannot be blank")]
    BlankSnapshotName,
    #[error("Snapshot SQL cannot be blank")]
    BlankSnapshotSql,
    #[error("Duplicate snapshot name: {0}")]
    DuplicateSnapshotName(String),
    #[error("Snapshot JSON is invalid: {0}")]
    InvalidSnapshotJson(#[from] serde_json::Error),
    #[error("Mortar metadata format must be mortar-metadata-v1")]
    InvalidMetadataFormat,
    #[error("Mortar metadata JSON is invalid: {0}")]
    InvalidMetadataJson(serde_json::Error),
    #[error("Mortar source map format must be mortar-source-map-v1")]
    InvalidSourceMapFormat,
    #[error("Mortar source map JSON is invalid: {0}")]
    InvalidSourceMapJson(serde_json::Error),
}

pub fn inspect_sql(sql: &str) -> Result<QueryInspection, MortarCompilerError> {
    if sql.trim().is_empty() {
        return Err(MortarCompilerError::BlankSql);
    }

    Ok(QueryInspection {
        sql: sql.trim().to_string(),
        tables: Vec::new(),
    })
}

pub fn postgres_explain_sql(sql: &str) -> Result<String, MortarCompilerError> {
    let inspection = inspect_sql(sql)?;
    Ok(format!("explain (format text) {}", inspection.sql))
}

pub fn redact_parameter_value(parameter_name: &str, value: &str) -> String {
    let normalized = parameter_name.to_lowercase();
    if [
        "password",
        "passwd",
        "secret",
        "token",
        "api_key",
        "authorization",
    ]
    .iter()
    .any(|term| normalized.contains(term))
    {
        "[redacted]".to_string()
    } else {
        value.to_string()
    }
}

pub fn redact_connection_string(connection: &str) -> String {
    let without_password = redact_userinfo_password(connection);
    redact_query_passwords(&without_password)
}

fn redact_userinfo_password(connection: &str) -> String {
    let Some(scheme_end) = connection.find("://") else {
        return connection.to_string();
    };
    let authority_start = scheme_end + 3;
    let authority_end = connection[authority_start..]
        .find('/')
        .map(|index| authority_start + index)
        .unwrap_or(connection.len());
    let authority = &connection[authority_start..authority_end];
    let Some(at_index) = authority.rfind('@') else {
        return connection.to_string();
    };
    let userinfo = &authority[..at_index];
    let Some(password_start) = userinfo.find(':') else {
        return connection.to_string();
    };

    format!(
        "{}***{}",
        &connection[..authority_start + password_start + 1],
        &connection[authority_start + at_index..]
    )
}

fn redact_query_passwords(connection: &str) -> String {
    let Some(query_start) = connection.find('?') else {
        return connection.to_string();
    };
    let prefix = &connection[..query_start + 1];
    let query = &connection[query_start + 1..];
    let redacted = query
        .split('&')
        .map(|part| {
            let Some((name, _value)) = part.split_once('=') else {
                return part.to_string();
            };
            let lower = name.to_lowercase();
            if lower.contains("password") || lower.contains("token") || lower.contains("secret") {
                format!("{name}=***")
            } else {
                part.to_string()
            }
        })
        .collect::<Vec<_>>()
        .join("&");
    format!("{prefix}{redacted}")
}

pub fn parse_sql_snapshot_file(content: &str) -> Result<SqlSnapshotFile, MortarCompilerError> {
    let snapshot_file: SqlSnapshotFile = serde_json::from_str(content)?;
    validate_sql_snapshot_file(&snapshot_file)?;
    Ok(snapshot_file)
}

pub fn render_sql_snapshot_file(
    snapshot_file: &SqlSnapshotFile,
) -> Result<String, MortarCompilerError> {
    validate_sql_snapshot_file(snapshot_file)?;

    let mut canonical = snapshot_file.clone();
    canonical
        .snapshots
        .sort_by(|left, right| left.name.cmp(&right.name));

    Ok(format!("{}\n", serde_json::to_string_pretty(&canonical)?))
}

pub fn sql_snapshot_format() -> &'static str {
    SQL_SNAPSHOT_FORMAT
}

pub fn empty_sql_snapshot_file() -> SqlSnapshotFile {
    SqlSnapshotFile {
        format: SQL_SNAPSHOT_FORMAT.to_string(),
        snapshots: Vec::new(),
    }
}

pub fn parse_mortar_metadata_file(
    content: &str,
) -> Result<MortarMetadataFile, MortarCompilerError> {
    let metadata: MortarMetadataFile =
        serde_json::from_str(content).map_err(MortarCompilerError::InvalidMetadataJson)?;
    if metadata.format != MORTAR_METADATA_FORMAT {
        return Err(MortarCompilerError::InvalidMetadataFormat);
    }
    Ok(metadata)
}

pub fn parse_mortar_source_map_file(
    content: &str,
) -> Result<MortarSourceMapFile, MortarCompilerError> {
    let source_map: MortarSourceMapFile =
        serde_json::from_str(content).map_err(MortarCompilerError::InvalidSourceMapJson)?;
    if source_map.format != MORTAR_SOURCE_MAP_FORMAT {
        return Err(MortarCompilerError::InvalidSourceMapFormat);
    }
    Ok(source_map)
}

pub fn detect_source_map_freshness(
    metadata: &MortarMetadataFile,
    source_map: &MortarSourceMapFile,
) -> Vec<SourceMapFreshnessIssue> {
    let expected = metadata_query_fingerprints(metadata);
    let mut issues = Vec::new();

    let expected_metadata_fingerprint = metadata_fingerprint(expected.values());
    if source_map.metadata.format != MORTAR_METADATA_FORMAT
        || source_map.metadata.path != "META-INF/mortar/entities.json"
        || source_map.metadata.fingerprint != expected_metadata_fingerprint
    {
        issues.push(SourceMapFreshnessIssue {
            query_id: None,
            message: "Source map metadata fingerprint is stale".to_string(),
        });
    }

    let actual = source_map
        .queries
        .iter()
        .map(|query| (query.id.as_str(), query.freshness.fingerprint.as_str()))
        .collect::<std::collections::BTreeMap<_, _>>();

    for (query_id, fingerprint) in &expected {
        match actual.get(query_id.as_str()) {
            Some(actual_fingerprint) if *actual_fingerprint == fingerprint => {}
            Some(_) => issues.push(SourceMapFreshnessIssue {
                query_id: Some(query_id.clone()),
                message: format!("Stale source map entry: {query_id}"),
            }),
            None => issues.push(SourceMapFreshnessIssue {
                query_id: Some(query_id.clone()),
                message: format!("Missing source map query: {query_id}"),
            }),
        }
    }

    for query in &source_map.queries {
        if !expected.contains_key(&query.id) {
            issues.push(SourceMapFreshnessIssue {
                query_id: Some(query.id.clone()),
                message: format!("Source map query is not present in metadata: {}", query.id),
            });
        }
    }

    issues
}

pub fn detect_schema_drift(
    metadata: &MortarMetadataFile,
    database_schema: &DatabaseSchema,
) -> Vec<SchemaDrift> {
    let mut drift = Vec::new();
    for entity in &metadata.entities {
        let Some(table) = database_schema
            .tables
            .iter()
            .find(|table| table.name == entity.table)
        else {
            drift.push(SchemaDrift {
                table: entity.table.clone(),
                column: None,
                message: format!("Missing database table: {}", entity.table),
            });
            continue;
        };

        for column in &entity.columns {
            if !table.columns.contains(&column.column) {
                drift.push(SchemaDrift {
                    table: entity.table.clone(),
                    column: Some(column.column.clone()),
                    message: format!(
                        "Missing database column: {}.{}",
                        entity.table, column.column
                    ),
                });
            }
        }

        for relation in &entity.relations {
            if !table.columns.contains(&relation.local_column) {
                drift.push(SchemaDrift {
                    table: entity.table.clone(),
                    column: Some(relation.local_column.clone()),
                    message: format!(
                        "Missing database column: {}.{}",
                        entity.table, relation.local_column
                    ),
                });
            }

            let Some(target_table) = database_schema
                .tables
                .iter()
                .find(|table| table.name == relation.target_table)
            else {
                drift.push(SchemaDrift {
                    table: relation.target_table.clone(),
                    column: None,
                    message: format!("Missing database table: {}", relation.target_table),
                });
                continue;
            };

            if !target_table.columns.contains(&relation.target_column) {
                drift.push(SchemaDrift {
                    table: relation.target_table.clone(),
                    column: Some(relation.target_column.clone()),
                    message: format!(
                        "Missing database column: {}.{}",
                        relation.target_table, relation.target_column
                    ),
                });
            }
        }
    }
    drift
}

fn validate_sql_snapshot_file(snapshot_file: &SqlSnapshotFile) -> Result<(), MortarCompilerError> {
    if snapshot_file.format != SQL_SNAPSHOT_FORMAT {
        return Err(MortarCompilerError::InvalidSnapshotFormat);
    }

    let mut names = BTreeSet::new();
    for snapshot in &snapshot_file.snapshots {
        if snapshot.name.trim().is_empty() {
            return Err(MortarCompilerError::BlankSnapshotName);
        }
        if snapshot.sql.trim().is_empty() {
            return Err(MortarCompilerError::BlankSnapshotSql);
        }
        if !names.insert(snapshot.name.as_str()) {
            return Err(MortarCompilerError::DuplicateSnapshotName(
                snapshot.name.clone(),
            ));
        }
    }

    Ok(())
}

fn metadata_query_fingerprints(
    metadata: &MortarMetadataFile,
) -> std::collections::BTreeMap<String, String> {
    let mut fingerprints = std::collections::BTreeMap::new();
    for entity in &metadata.entities {
        for query in &entity.queries {
            fingerprints.insert(query.id.clone(), query_fingerprint(entity, query));
        }
    }
    fingerprints
}

fn metadata_fingerprint<'a>(fingerprints: impl IntoIterator<Item = &'a String>) -> String {
    let mut input = String::new();
    let mut sorted = fingerprints.into_iter().collect::<Vec<_>>();
    sorted.sort();
    for fingerprint in sorted {
        input.push_str(fingerprint);
        input.push('\n');
    }
    sha256(&input)
}

fn query_fingerprint(entity: &MortarEntityMetadata, query: &MortarQueryMetadata) -> String {
    let mut input = String::new();
    input.push_str(&query.id);
    input.push('\n');
    input.push_str(&entity.java_type);
    input.push('\n');
    input.push_str(&entity.table);
    input.push('\n');
    input.push_str(&entity.alias);
    input.push('\n');
    input.push_str(&query.generated_source.java_type);
    input.push('\n');
    input.push_str(&query.generated_source.generated_type);
    input.push('\n');
    input.push_str(&query.generated_source.member);
    input.push('\n');
    input.push_str(&query.name);
    input.push('\n');
    input.push_str(&query.snapshot);
    input.push('\n');
    input.push_str(&query.row_type);
    input.push('\n');
    for column in &entity.columns {
        input.push_str("column:");
        input.push_str(&column.property);
        input.push('|');
        input.push_str(&column.column);
        input.push('|');
        input.push_str(&column.java_type);
        input.push('|');
        input.push_str(if query_parameter_is_id(query, column) {
            "true"
        } else {
            "false"
        });
        input.push('\n');
    }
    for relation in &entity.relations {
        input.push_str("relation:");
        input.push_str(&relation.property);
        input.push('|');
        input.push_str(&relation.local_column);
        input.push('|');
        input.push_str(&relation.target_table);
        input.push('|');
        input.push_str(&relation.target_alias);
        input.push('|');
        input.push_str(&relation.target_column);
        input.push('|');
        input.push_str(if relation.nullable { "true" } else { "false" });
        input.push('\n');
    }
    for parameter in &query.parameters {
        input.push_str("parameter:");
        input.push_str(&parameter.name);
        input.push('|');
        input.push_str(&parameter.java_type);
        input.push('\n');
    }
    sha256(&input)
}

fn query_parameter_is_id(query: &MortarQueryMetadata, column: &MortarColumnMetadata) -> bool {
    query.shape == "findById"
        && query
            .parameters
            .first()
            .is_some_and(|parameter| parameter.name == column.property)
}

fn sha256(input: &str) -> String {
    const K: [u32; 64] = [
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4,
        0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe,
        0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f,
        0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
        0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc,
        0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b,
        0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070, 0x19a4c116,
        0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7,
        0xc67178f2,
    ];
    let mut bytes = input.as_bytes().to_vec();
    let bit_len = (bytes.len() as u64) * 8;
    bytes.push(0x80);
    while bytes.len() % 64 != 56 {
        bytes.push(0);
    }
    bytes.extend_from_slice(&bit_len.to_be_bytes());

    let mut h = [
        0x6a09e667u32,
        0xbb67ae85,
        0x3c6ef372,
        0xa54ff53a,
        0x510e527f,
        0x9b05688c,
        0x1f83d9ab,
        0x5be0cd19,
    ];

    for chunk in bytes.chunks_exact(64) {
        let mut w = [0u32; 64];
        for index in 0..16 {
            w[index] = u32::from_be_bytes([
                chunk[index * 4],
                chunk[index * 4 + 1],
                chunk[index * 4 + 2],
                chunk[index * 4 + 3],
            ]);
        }
        for index in 16..64 {
            let s0 = w[index - 15].rotate_right(7)
                ^ w[index - 15].rotate_right(18)
                ^ (w[index - 15] >> 3);
            let s1 = w[index - 2].rotate_right(17)
                ^ w[index - 2].rotate_right(19)
                ^ (w[index - 2] >> 10);
            w[index] = w[index - 16]
                .wrapping_add(s0)
                .wrapping_add(w[index - 7])
                .wrapping_add(s1);
        }

        let mut a = h[0];
        let mut b = h[1];
        let mut c = h[2];
        let mut d = h[3];
        let mut e = h[4];
        let mut f = h[5];
        let mut g = h[6];
        let mut current_h = h[7];

        for index in 0..64 {
            let s1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let ch = (e & f) ^ ((!e) & g);
            let temp1 = current_h
                .wrapping_add(s1)
                .wrapping_add(ch)
                .wrapping_add(K[index])
                .wrapping_add(w[index]);
            let s0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let maj = (a & b) ^ (a & c) ^ (b & c);
            let temp2 = s0.wrapping_add(maj);

            current_h = g;
            g = f;
            f = e;
            e = d.wrapping_add(temp1);
            d = c;
            c = b;
            b = a;
            a = temp1.wrapping_add(temp2);
        }

        h[0] = h[0].wrapping_add(a);
        h[1] = h[1].wrapping_add(b);
        h[2] = h[2].wrapping_add(c);
        h[3] = h[3].wrapping_add(d);
        h[4] = h[4].wrapping_add(e);
        h[5] = h[5].wrapping_add(f);
        h[6] = h[6].wrapping_add(g);
        h[7] = h[7].wrapping_add(current_h);
    }

    let mut hex = String::from("sha256:");
    for value in h {
        hex.push_str(&format!("{value:08x}"));
    }
    hex
}

#[cfg(test)]
mod tests {
    use super::{
        DatabaseSchema, DatabaseTable, MortarCompilerError, MortarQueryParameterMetadata,
        MortarSourceAnchor, MortarSourceMapFile, MortarSourceMapFreshness,
        MortarSourceMapMetadataLink, MortarSourceMapQuery, SqlSnapshot, SqlSnapshotFile,
        SqlSnapshotMetadata, SqlSnapshotParameter, detect_schema_drift,
        detect_source_map_freshness, inspect_sql, metadata_fingerprint,
        metadata_query_fingerprints, parse_mortar_metadata_file, parse_mortar_source_map_file,
        parse_sql_snapshot_file, postgres_explain_sql, redact_connection_string,
        redact_parameter_value, render_sql_snapshot_file, sql_snapshot_format,
    };

    #[test]
    fn rejects_blank_sql() {
        let error = inspect_sql("   ").expect_err("blank SQL should fail");

        assert!(matches!(error, MortarCompilerError::BlankSql));
    }

    #[test]
    fn trims_sql_for_inspection() {
        let inspection = inspect_sql(" select 1 ").expect("SQL should be inspected");

        assert_eq!(inspection.sql, "select 1");
        assert!(inspection.tables.is_empty());
    }

    #[test]
    fn builds_postgres_explain_sql() {
        let explain_sql = postgres_explain_sql(" select 1 ").expect("SQL should build");

        assert_eq!(explain_sql, "explain (format text) select 1");
    }

    #[test]
    fn redacts_sensitive_parameter_values() {
        assert_eq!(
            redact_parameter_value("password", "secret-value"),
            "[redacted]"
        );
        assert_eq!(redact_parameter_value("clientName", "Ada"), "Ada");
    }

    #[test]
    fn redacts_connection_string_passwords() {
        assert_eq!(
            redact_connection_string("postgres://app:secret@localhost:5432/app?sslmode=require"),
            "postgres://app:***@localhost:5432/app?sslmode=require"
        );
        assert_eq!(
            redact_connection_string("postgres://localhost/app?password=secret&sslmode=require"),
            "postgres://localhost/app?password=***&sslmode=require"
        );
    }

    #[test]
    fn renders_canonical_sql_snapshot_file() {
        let snapshot_file = SqlSnapshotFile {
            format: sql_snapshot_format().to_string(),
            snapshots: vec![
                SqlSnapshot {
                    name: "ClientRepository.findByName".to_string(),
                    sql: "select c.id from clients c where c.name = ?".to_string(),
                    parameters: vec![SqlSnapshotParameter {
                        position: 1,
                        java_type: "java.lang.String".to_string(),
                        value: Some("Ana".to_string()),
                    }],
                    metadata: SqlSnapshotMetadata {
                        tables: vec!["clients".to_string()],
                        columns: vec!["clients.id".to_string(), "clients.name".to_string()],
                        joins: Vec::new(),
                    },
                },
                SqlSnapshot {
                    name: "ClientRepository.count".to_string(),
                    sql: "select count(*) from clients c".to_string(),
                    parameters: Vec::new(),
                    metadata: SqlSnapshotMetadata {
                        tables: vec!["clients".to_string()],
                        columns: Vec::new(),
                        joins: Vec::new(),
                    },
                },
            ],
        };

        let rendered = render_sql_snapshot_file(&snapshot_file).expect("snapshot should render");

        assert_eq!(
            rendered,
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.count",
      "sql": "select count(*) from clients c",
      "parameters": [],
      "metadata": {
        "tables": [
          "clients"
        ],
        "columns": [],
        "joins": []
      }
    },
    {
      "name": "ClientRepository.findByName",
      "sql": "select c.id from clients c where c.name = ?",
      "parameters": [
        {
          "position": 1,
          "java_type": "java.lang.String",
          "value": "Ana"
        }
      ],
      "metadata": {
        "tables": [
          "clients"
        ],
        "columns": [
          "clients.id",
          "clients.name"
        ],
        "joins": []
      }
    }
  ]
}
"#
        );
    }

    #[test]
    fn parses_sql_snapshot_file() {
        let parsed = parse_sql_snapshot_file(
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.findById",
      "sql": "select c.id from clients c where c.id = ?",
      "parameters": [
        {
          "position": 1,
          "java_type": "java.lang.Long",
          "value": "7"
        }
      ],
      "metadata": {
        "tables": [
          "clients"
        ],
        "columns": [
          "clients.id"
        ],
        "joins": []
      }
    }
  ]
}"#,
        )
        .expect("snapshot file should parse");

        assert_eq!(parsed.snapshots[0].name, "ClientRepository.findById");
        assert_eq!(
            parsed.snapshots[0].parameters[0].java_type,
            "java.lang.Long"
        );
    }

    #[test]
    fn rejects_invalid_sql_snapshot_format() {
        let error = parse_sql_snapshot_file(
            r#"{
  "format": "other",
  "snapshots": []
}"#,
        )
        .expect_err("invalid format should fail");

        assert!(matches!(error, MortarCompilerError::InvalidSnapshotFormat));
    }

    #[test]
    fn rejects_duplicate_sql_snapshot_names() {
        let snapshot_file = SqlSnapshotFile {
            format: sql_snapshot_format().to_string(),
            snapshots: vec![
                SqlSnapshot {
                    name: "ClientRepository.find".to_string(),
                    sql: "select 1".to_string(),
                    parameters: Vec::new(),
                    metadata: SqlSnapshotMetadata {
                        tables: Vec::new(),
                        columns: Vec::new(),
                        joins: Vec::new(),
                    },
                },
                SqlSnapshot {
                    name: "ClientRepository.find".to_string(),
                    sql: "select 2".to_string(),
                    parameters: Vec::new(),
                    metadata: SqlSnapshotMetadata {
                        tables: Vec::new(),
                        columns: Vec::new(),
                        joins: Vec::new(),
                    },
                },
            ],
        };

        let error =
            render_sql_snapshot_file(&snapshot_file).expect_err("duplicate names should fail");

        assert!(matches!(
            error,
            MortarCompilerError::DuplicateSnapshotName(name) if name == "ClientRepository.find"
        ));
    }

    #[test]
    fn parses_mortar_metadata_file() {
        let metadata = super::parse_mortar_metadata_file(
            r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "example.Client",
      "table": "clients",
      "alias": "c",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        }
      ],
      "relations": []
    }
  ]
}"#,
        )
        .expect("metadata should parse");

        assert_eq!(metadata.entities[0].java_type, "example.Client");
        assert_eq!(metadata.entities[0].columns[0].column, "id");
        assert!(metadata.entities[0].queries.is_empty());
    }

    #[test]
    fn parses_query_generated_source_metadata() {
        let metadata = super::parse_mortar_metadata_file(
            r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "example.Client",
      "table": "clients",
      "alias": "c",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        }
      ],
      "relations": [],
      "queries": [
        {
          "id": "example.Client.findById",
          "name": "findById",
          "shape": "findById",
          "generated_source": {
            "java_type": "example.QClient",
            "member": "findById",
            "generated_type": "example.QClient.FindByIdQuery"
          },
          "parameters": [
            {
              "name": "id",
              "java_type": "java.lang.Long"
            }
          ],
          "row_type": "example.QClient.FindByIdRow",
          "snapshot": "example.Client.findById"
        }
      ]
    }
  ]
}"#,
        )
        .expect("query metadata should parse");

        let query = &metadata.entities[0].queries[0];
        assert_eq!(query.id, "example.Client.findById");
        assert_eq!(query.generated_source.java_type, "example.QClient");
        assert_eq!(query.parameters[0].name, "id");
    }

    #[test]
    fn parses_mortar_source_map_file() {
        let source_map = parse_mortar_source_map_file(
            r#"{
  "format": "mortar-source-map-v1",
  "metadata": {
    "format": "mortar-metadata-v1",
    "path": "META-INF/mortar/entities.json",
    "fingerprint": "sha256:metadata"
  },
  "queries": [
    {
      "id": "example.Client.findById",
      "entity_type": "example.Client",
      "generated_entity_type": "example.QClient",
      "generated_read_namespace": "example.QClient.Read",
      "generated_member": "read.findById",
      "query_name": "findById",
      "snapshot": "example.Client.findById",
      "row_type": "example.QClient.FindByIdRow",
      "parameters": [
        {
          "name": "id",
          "java_type": "java.lang.Long"
        }
      ],
      "source_anchor": {
        "kind": "java-type",
        "java_type": "example.Client",
        "member": "findById"
      },
      "freshness": {
        "fingerprint": "sha256:query"
      }
    }
  ]
}"#,
        )
        .expect("source map should parse");

        assert_eq!(source_map.format, "mortar-source-map-v1");
        assert_eq!(source_map.metadata.path, "META-INF/mortar/entities.json");
        assert_eq!(source_map.queries[0].generated_member, "read.findById");
        assert_eq!(
            source_map.queries[0].source_anchor.java_type,
            "example.Client"
        );
        assert_eq!(source_map.queries[0].parameters[0].name, "id");
    }

    #[test]
    fn detects_stale_source_map_freshness() {
        let metadata = parse_mortar_metadata_file(
            r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "example.Client",
      "table": "clients",
      "alias": "c",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        }
      ],
      "relations": [],
      "queries": [
        {
          "id": "example.Client.findById",
          "name": "findById",
          "shape": "findById",
          "generated_source": {
            "java_type": "example.QClient",
            "member": "read.findById",
            "generated_type": "example.QClient.Read"
          },
          "parameters": [
            {
              "name": "id",
              "java_type": "java.lang.Long"
            }
          ],
          "row_type": "example.QClient.FindByIdRow",
          "snapshot": "example.Client.findById"
        }
      ]
    }
  ]
}"#,
        )
        .expect("metadata should parse");
        let source_map = parse_mortar_source_map_file(
            r#"{
  "format": "mortar-source-map-v1",
  "metadata": {
    "format": "mortar-metadata-v1",
    "path": "META-INF/mortar/entities.json",
    "fingerprint": "sha256:stale"
  },
  "queries": [
    {
      "id": "example.Client.findById",
      "entity_type": "example.Client",
      "generated_entity_type": "example.QClient",
      "generated_read_namespace": "example.QClient.Read",
      "generated_member": "read.findById",
      "query_name": "findById",
      "snapshot": "example.Client.findById",
      "row_type": "example.QClient.FindByIdRow",
      "parameters": [
        {
          "name": "id",
          "java_type": "java.lang.Long"
        }
      ],
      "source_anchor": {
        "kind": "java-type",
        "java_type": "example.Client",
        "member": "findById"
      },
      "freshness": {
        "fingerprint": "sha256:stale"
      }
    },
    {
      "id": "example.Client.findAll",
      "entity_type": "example.Client",
      "generated_entity_type": "example.QClient",
      "generated_read_namespace": "example.QClient.Read",
      "generated_member": "read.findAll",
      "query_name": "findAll",
      "snapshot": "example.Client.findAll",
      "row_type": "example.QClient.FindAllRow",
      "parameters": [],
      "source_anchor": {
        "kind": "java-type",
        "java_type": "example.Client",
        "member": "findAll"
      },
      "freshness": {
        "fingerprint": "sha256:extra"
      }
    }
  ]
}"#,
        )
        .expect("source map should parse");

        let issues = detect_source_map_freshness(&metadata, &source_map);
        let messages = issues
            .iter()
            .map(|issue| issue.message.as_str())
            .collect::<Vec<_>>();

        assert!(messages.contains(&"Source map metadata fingerprint is stale"));
        assert!(messages.contains(&"Stale source map entry: example.Client.findById"));
        assert!(
            messages
                .contains(&"Source map query is not present in metadata: example.Client.findAll")
        );
    }

    #[test]
    fn accepts_fresh_source_map_fingerprints() {
        let metadata = parse_mortar_metadata_file(
            r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "example.Client",
      "table": "clients",
      "alias": "c",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        }
      ],
      "relations": [],
      "queries": [
        {
          "id": "example.Client.findById",
          "name": "findById",
          "shape": "findById",
          "generated_source": {
            "java_type": "example.QClient",
            "member": "read.findById",
            "generated_type": "example.QClient.Read"
          },
          "parameters": [
            {
              "name": "id",
              "java_type": "java.lang.Long"
            }
          ],
          "row_type": "example.QClient.FindByIdRow",
          "snapshot": "example.Client.findById"
        }
      ]
    }
  ]
}"#,
        )
        .expect("metadata should parse");
        let fingerprints = metadata_query_fingerprints(&metadata);
        let query_fingerprint = fingerprints
            .get("example.Client.findById")
            .expect("query fingerprint should exist");
        let source_map = MortarSourceMapFile {
            format: "mortar-source-map-v1".to_string(),
            metadata: MortarSourceMapMetadataLink {
                format: "mortar-metadata-v1".to_string(),
                path: "META-INF/mortar/entities.json".to_string(),
                fingerprint: metadata_fingerprint(fingerprints.values()),
            },
            queries: vec![MortarSourceMapQuery {
                id: "example.Client.findById".to_string(),
                entity_type: "example.Client".to_string(),
                generated_entity_type: "example.QClient".to_string(),
                generated_read_namespace: "example.QClient.Read".to_string(),
                generated_member: "read.findById".to_string(),
                query_name: "findById".to_string(),
                snapshot: "example.Client.findById".to_string(),
                row_type: "example.QClient.FindByIdRow".to_string(),
                parameters: vec![MortarQueryParameterMetadata {
                    name: "id".to_string(),
                    java_type: "java.lang.Long".to_string(),
                }],
                source_anchor: MortarSourceAnchor {
                    kind: "java-type".to_string(),
                    java_type: "example.Client".to_string(),
                    member: "findById".to_string(),
                },
                freshness: MortarSourceMapFreshness {
                    fingerprint: query_fingerprint.clone(),
                },
            }],
        };

        assert!(detect_source_map_freshness(&metadata, &source_map).is_empty());
    }

    #[test]
    fn detects_schema_drift_for_missing_table_and_column() {
        let metadata = parse_mortar_metadata_file(
            r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "example.Client",
      "table": "clients",
      "alias": "c",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        },
        {
          "property": "name",
          "column": "name",
          "java_type": "java.lang.String"
        }
      ],
      "relations": []
    },
    {
      "java_type": "example.Route",
      "table": "routes",
      "alias": "r",
      "columns": [],
      "relations": []
    }
  ]
}"#,
        )
        .expect("metadata should parse");
        let schema = DatabaseSchema {
            tables: vec![DatabaseTable {
                name: "clients".to_string(),
                columns: vec!["id".to_string()],
            }],
        };

        let drift = detect_schema_drift(&metadata, &schema);

        assert_eq!(drift.len(), 2);
        assert_eq!(drift[0].message, "Missing database column: clients.name");
        assert_eq!(drift[1].message, "Missing database table: routes");
    }

    #[test]
    fn detects_r17_schema_drift_cases_from_ticket_fixture_metadata() {
        let metadata = parse_mortar_metadata_file(
            r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "dev.mortar.examples.querycorpus.postgres.TicketRecord",
      "table": "tickets",
      "alias": "t",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        },
        {
          "property": "summary",
          "column": "summary",
          "java_type": "java.lang.String"
        },
        {
          "property": "priority",
          "column": "priority",
          "java_type": "java.lang.String"
        },
        {
          "property": "openedOn",
          "column": "opened_on",
          "java_type": "java.time.LocalDate"
        }
      ],
      "relations": [
        {
          "property": "customer",
          "local_column": "customer_id",
          "target_table": "customers",
          "target_alias": "cu",
          "target_column": "id",
          "nullable": false
        },
        {
          "property": "assignedTechnician",
          "local_column": "assigned_technician_id",
          "target_table": "technicians",
          "target_alias": "te",
          "target_column": "id",
          "nullable": true
        },
        {
          "property": "status",
          "local_column": "status_code",
          "target_table": "ticket_statuses",
          "target_alias": "ts",
          "target_column": "code",
          "nullable": false
        }
      ]
    },
    {
      "java_type": "dev.mortar.examples.querycorpus.postgres.TechnicianRecord",
      "table": "technicians",
      "alias": "te",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        },
        {
          "property": "displayName",
          "column": "display_name",
          "java_type": "java.lang.String"
        },
        {
          "property": "region",
          "column": "region",
          "java_type": "java.lang.String"
        }
      ],
      "relations": []
    }
  ]
}"#,
        )
        .expect("R17 metadata should parse");
        let database_schema = DatabaseSchema {
            tables: vec![
                DatabaseTable {
                    name: "tickets".to_string(),
                    columns: vec![
                        "id".to_string(),
                        "summary".to_string(),
                        "priority".to_string(),
                        "opened_on".to_string(),
                    ],
                },
                DatabaseTable {
                    name: "customers".to_string(),
                    columns: vec!["id".to_string()],
                },
                DatabaseTable {
                    name: "technicians".to_string(),
                    columns: vec!["id".to_string(), "display_name".to_string()],
                },
                DatabaseTable {
                    name: "ticket_statuses".to_string(),
                    columns: vec![],
                },
            ],
        };

        let drift = detect_schema_drift(&metadata, &database_schema);

        let messages = drift
            .iter()
            .map(|drift| drift.message.as_str())
            .collect::<Vec<_>>();
        assert_eq!(drift.len(), 5);
        assert!(messages.contains(&"Missing database column: tickets.customer_id"));
        assert!(messages.contains(&"Missing database column: tickets.assigned_technician_id"));
        assert!(messages.contains(&"Missing database column: tickets.status_code"));
        assert!(messages.contains(&"Missing database column: ticket_statuses.code"));
        assert!(messages.contains(&"Missing database column: technicians.region"));
    }
}
