pub const MORTAR_CLI_NAME: &str = "mortar";

use mortar_compiler::{
    DatabaseSchema, DatabaseTable, MortarCompilerError, SqlSnapshot, empty_sql_snapshot_file,
    parse_sql_snapshot_file, render_sql_snapshot_file,
};
use postgres::{Client, NoTls};
use std::collections::BTreeMap;

pub fn doctor_report() -> String {
    format!(
        "{name} toolchain ready\nsnapshot format: {format}",
        name = MORTAR_CLI_NAME,
        format = mortar_compiler::sql_snapshot_format()
    )
}

pub fn doctor_report_json() -> String {
    serde_json::json!({
        "snapshot_format": mortar_compiler::sql_snapshot_format(),
        "status": "ready",
        "tool": MORTAR_CLI_NAME
    })
    .to_string()
}

pub fn inspect_sql_text(sql: &str) -> Result<String, MortarCompilerError> {
    let inspection = mortar_compiler::inspect_sql(sql)?;
    Ok(inspection.sql)
}

pub fn inspect_sql_json(sql: &str) -> Result<String, MortarCompilerError> {
    let inspection = mortar_compiler::inspect_sql(sql)?;
    Ok(serde_json::json!({
        "sql": inspection.sql,
        "tables": inspection.tables
    })
    .to_string())
}

pub fn inspect_metadata_text(content: &str) -> Result<String, MortarCompilerError> {
    let metadata = mortar_compiler::parse_mortar_metadata_file(content)?;
    let mut output = format!("entities: {}", metadata.entities.len());
    for entity in metadata.entities {
        output.push_str(&format!(
            "\n- {} -> {} as {}",
            entity.java_type, entity.table, entity.alias
        ));
    }
    Ok(output)
}

pub fn inspect_metadata_json(content: &str) -> Result<String, MortarCompilerError> {
    let metadata = mortar_compiler::parse_mortar_metadata_file(content)?;
    Ok(serde_json::to_string(&metadata)?)
}

pub fn metadata_report_text(content: &str) -> Result<String, MortarCompilerError> {
    let metadata = mortar_compiler::parse_mortar_metadata_file(content)?;
    let mut output = format!(
        "Mortar query inventory\nentities: {}",
        metadata.entities.len()
    );
    for entity in metadata.entities {
        output.push_str(&format!(
            "\n- {}: {} as {} ({} columns, {} relations)",
            entity.java_type,
            entity.table,
            entity.alias,
            entity.columns.len(),
            entity.relations.len()
        ));
    }
    Ok(output)
}

pub fn metadata_report_json(content: &str) -> Result<String, MortarCompilerError> {
    let metadata = mortar_compiler::parse_mortar_metadata_file(content)?;
    let entities: Vec<_> = metadata
        .entities
        .into_iter()
        .map(|entity| {
            serde_json::json!({
                "alias": entity.alias,
                "column_count": entity.columns.len(),
                "java_type": entity.java_type,
                "relation_count": entity.relations.len(),
                "table": entity.table
            })
        })
        .collect();
    let entity_count = entities.len();
    Ok(serde_json::json!({
        "entities": entities,
        "entity_count": entity_count
    })
    .to_string())
}

pub fn update_sql_snapshot_content(
    existing_content: Option<&str>,
    snapshot: SqlSnapshot,
) -> Result<String, MortarCompilerError> {
    let mut snapshot_file = match existing_content {
        Some(content) => parse_sql_snapshot_file(content)?,
        None => empty_sql_snapshot_file(),
    };

    snapshot_file
        .snapshots
        .retain(|current| current.name != snapshot.name);
    snapshot_file.snapshots.push(snapshot);

    render_sql_snapshot_file(&snapshot_file)
}

pub fn check_sql_snapshot_content(content: &str) -> Result<(), String> {
    let snapshot_file = parse_sql_snapshot_file(content).map_err(|error| error.to_string())?;
    let rendered = render_sql_snapshot_file(&snapshot_file).map_err(|error| error.to_string())?;
    if content.replace("\r\n", "\n") != rendered {
        return Err("SQL snapshot file is not canonical; run mortar snapshot update".to_string());
    }
    Ok(())
}

pub fn explain_postgres_text(
    connection: &str,
    sql: &str,
) -> Result<String, Box<dyn std::error::Error>> {
    let explain_sql = mortar_compiler::postgres_explain_sql(sql)?;
    let mut client = Client::connect(connection, NoTls)?;
    let mut lines = Vec::new();
    for row in client.query(explain_sql.as_str(), &[])? {
        lines.push(row.get::<usize, String>(0));
    }
    Ok(lines.join("\n"))
}

pub fn schema_check_postgres_text(
    connection: &str,
    metadata_content: &str,
) -> Result<String, Box<dyn std::error::Error>> {
    let metadata = mortar_compiler::parse_mortar_metadata_file(metadata_content)?;
    let database_schema = load_postgres_schema(connection)?;
    let drift = mortar_compiler::detect_schema_drift(&metadata, &database_schema);
    if drift.is_empty() {
        return Ok("Schema OK".to_string());
    }

    Ok(drift
        .into_iter()
        .map(|drift| drift.message)
        .collect::<Vec<_>>()
        .join("\n"))
}

fn load_postgres_schema(connection: &str) -> Result<DatabaseSchema, Box<dyn std::error::Error>> {
    let mut client = Client::connect(connection, NoTls)?;
    let rows = client.query(
        r#"
        select table_name, column_name
        from information_schema.columns
        where table_schema = 'public'
        order by table_name, ordinal_position
        "#,
        &[],
    )?;
    let mut tables = BTreeMap::<String, Vec<String>>::new();
    for row in rows {
        let table_name: String = row.get(0);
        let column_name: String = row.get(1);
        tables.entry(table_name).or_default().push(column_name);
    }

    Ok(DatabaseSchema {
        tables: tables
            .into_iter()
            .map(|(name, columns)| DatabaseTable { name, columns })
            .collect(),
    })
}

#[cfg(test)]
mod tests {
    use super::{
        check_sql_snapshot_content, doctor_report, doctor_report_json, inspect_metadata_json,
        inspect_metadata_text, inspect_sql_json, inspect_sql_text, metadata_report_json,
        metadata_report_text, update_sql_snapshot_content,
    };
    use mortar_compiler::{SqlSnapshot, SqlSnapshotMetadata};
    use serde_json::Value;

    #[test]
    fn builds_doctor_report() {
        assert_eq!(
            doctor_report(),
            "mortar toolchain ready\nsnapshot format: mortar-sql-snapshot-v1"
        );
    }

    #[test]
    fn builds_doctor_json_report() {
        let value: Value =
            serde_json::from_str(&doctor_report_json()).expect("doctor JSON should parse");

        assert_eq!(value["tool"], "mortar");
        assert_eq!(value["status"], "ready");
        assert_eq!(value["snapshot_format"], "mortar-sql-snapshot-v1");
    }

    #[test]
    fn inspects_sql_for_text_output() {
        let output = inspect_sql_text(" select 1 ").expect("SQL should inspect");

        assert_eq!(output, "select 1");
    }

    #[test]
    fn inspects_sql_for_json_output() {
        let value: Value =
            serde_json::from_str(&inspect_sql_json(" select 1 ").expect("SQL JSON should render"))
                .expect("SQL JSON should parse");

        assert_eq!(value["sql"], "select 1");
        assert_eq!(
            value["tables"]
                .as_array()
                .expect("tables should be an array")
                .len(),
            0
        );
    }

    #[test]
    fn inspects_mortar_metadata_for_text_output() {
        let output = inspect_metadata_text(
            r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "example.Client",
      "table": "clients",
      "alias": "c",
      "columns": [],
      "relations": []
    }
  ]
}"#,
        )
        .expect("metadata should inspect");

        assert_eq!(output, "entities: 1\n- example.Client -> clients as c");
    }

    #[test]
    fn inspects_mortar_metadata_for_json_output() {
        let value: Value = serde_json::from_str(
            &inspect_metadata_json(
                r#"{
  "format": "mortar-metadata-v1",
  "entities": []
}"#,
            )
            .expect("metadata JSON should render"),
        )
        .expect("metadata JSON should parse");

        assert_eq!(value["format"], "mortar-metadata-v1");
        assert_eq!(
            value["entities"]
                .as_array()
                .expect("entities should be an array")
                .len(),
            0
        );
    }

    #[test]
    fn builds_metadata_report_for_query_inventory() {
        let output = metadata_report_text(
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
    }
  ]
}"#,
        )
        .expect("metadata report should render");

        assert_eq!(
            output,
            "Mortar query inventory\nentities: 1\n- example.Client: clients as c (2 columns, 0 relations)"
        );
    }

    #[test]
    fn builds_metadata_report_json_for_ci() {
        let value: Value = serde_json::from_str(
            &metadata_report_json(
                r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "example.Client",
      "table": "clients",
      "alias": "c",
      "columns": [],
      "relations": []
    }
  ]
}"#,
            )
            .expect("metadata report JSON should render"),
        )
        .expect("metadata report JSON should parse");

        assert_eq!(value["entity_count"], 1);
        assert_eq!(value["entities"][0]["java_type"], "example.Client");
    }

    #[test]
    fn creates_sql_snapshot_file() {
        let rendered = update_sql_snapshot_content(
            None,
            SqlSnapshot {
                name: "ClientRepository.find".to_string(),
                sql: "select c.id from clients c".to_string(),
                parameters: Vec::new(),
                metadata: SqlSnapshotMetadata {
                    tables: vec!["clients".to_string()],
                    columns: vec!["clients.id".to_string()],
                    joins: Vec::new(),
                },
            },
        )
        .expect("snapshot should render");

        assert_eq!(
            rendered,
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.find",
      "sql": "select c.id from clients c",
      "parameters": [],
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
}
"#
        );
    }

    #[test]
    fn replaces_existing_sql_snapshot_by_name() {
        let existing = r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.find",
      "sql": "select 1",
      "parameters": [],
      "metadata": {
        "tables": [],
        "columns": [],
        "joins": []
      }
    },
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
    }
  ]
}"#;

        let rendered = update_sql_snapshot_content(
            Some(existing),
            SqlSnapshot {
                name: "ClientRepository.find".to_string(),
                sql: "select c.id from clients c".to_string(),
                parameters: Vec::new(),
                metadata: SqlSnapshotMetadata {
                    tables: vec!["clients".to_string()],
                    columns: vec!["clients.id".to_string()],
                    joins: Vec::new(),
                },
            },
        )
        .expect("snapshot should render");

        assert!(rendered.contains(r#""name": "ClientRepository.count""#));
        assert!(rendered.contains(r#""sql": "select c.id from clients c""#));
        assert!(!rendered.contains(r#""sql": "select 1""#));
    }

    #[test]
    fn accepts_canonical_sql_snapshot_content() {
        let content = r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.find",
      "sql": "select c.id from clients c",
      "parameters": [],
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
}
"#;

        check_sql_snapshot_content(content).expect("canonical snapshot should pass");
    }

    #[test]
    fn rejects_non_canonical_sql_snapshot_content() {
        let content = r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.z",
      "sql": "select 2",
      "parameters": [],
      "metadata": {
        "tables": [],
        "columns": [],
        "joins": []
      }
    },
    {
      "name": "ClientRepository.a",
      "sql": "select 1",
      "parameters": [],
      "metadata": {
        "tables": [],
        "columns": [],
        "joins": []
      }
    }
  ]
}
"#;

        let error =
            check_sql_snapshot_content(content).expect_err("non-canonical snapshot should fail");

        assert_eq!(
            error,
            "SQL snapshot file is not canonical; run mortar snapshot update"
        );
    }

    #[test]
    fn explains_sql_against_postgres() -> Result<(), Box<dyn std::error::Error>> {
        use super::explain_postgres_text;
        use testcontainers_modules::postgres;
        use testcontainers_modules::testcontainers::runners::SyncRunner;

        let node = postgres::Postgres::default().with_host_auth().start()?;
        let connection = format!(
            "postgres://postgres@{}:{}/postgres",
            node.get_host()?,
            node.get_host_port_ipv4(5432)?
        );

        let plan = explain_postgres_text(&connection, "select 1")?;

        assert!(plan.contains("Result"));
        Ok(())
    }

    #[test]
    fn checks_schema_against_postgres() -> Result<(), Box<dyn std::error::Error>> {
        use super::schema_check_postgres_text;
        use testcontainers_modules::postgres;
        use testcontainers_modules::testcontainers::runners::SyncRunner;

        let node = postgres::Postgres::default()
            .with_host_auth()
            .with_init_sql(
                "create table clients (id bigint primary key, name text);"
                    .to_string()
                    .into_bytes(),
            )
            .start()?;
        let connection = format!(
            "postgres://postgres@{}:{}/postgres",
            node.get_host()?,
            node.get_host_port_ipv4(5432)?
        );
        let metadata = r#"{
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
    }
  ]
}"#;

        let output = schema_check_postgres_text(&connection, metadata)?;

        assert_eq!(output, "Schema OK");
        Ok(())
    }
}
