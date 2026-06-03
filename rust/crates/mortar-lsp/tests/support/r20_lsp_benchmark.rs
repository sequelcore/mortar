use lsp_types::{Position, Uri};
use mortar_lsp::LspState;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

const ENTITIES_JSON: &str =
    include_str!("../../../mortar-compiler/test-fixtures/source-map-contract/r18/entities.json");
const SOURCE_MAP_JSON: &str =
    include_str!("../../../mortar-compiler/test-fixtures/source-map-contract/r18/source-map.json");
pub struct R20LspBenchmarkWorkspace {
    root: PathBuf,
}

impl R20LspBenchmarkWorkspace {
    pub fn fresh() -> Self {
        Self::with_source_map(SOURCE_MAP_JSON)
    }

    pub fn stale_source_map() -> Self {
        Self::with_source_map(&SOURCE_MAP_JSON.replace(
            "sha256:7196ccb7d298233e37f9f987263049279439572923730d97ba4315b24df36b84",
            "sha256:stale",
        ))
    }

    pub fn without_snapshot() -> Self {
        let workspace = Self::with_source_map(SOURCE_MAP_JSON);
        std::fs::write(
            workspace.root.join("mortar.sql.snap.json"),
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": []
}
"#,
        )
        .expect("snapshot fixture should be replaced");
        workspace
    }

    fn with_source_map(source_map: &str) -> Self {
        let id = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("time should be monotonic")
            .as_nanos();
        let root = std::env::temp_dir().join(format!("mortar-r20-lsp-benchmark-{id}"));
        let metadata_dir = root.join("META-INF").join("mortar");
        std::fs::create_dir_all(&metadata_dir).expect("metadata directory should be created");
        std::fs::write(metadata_dir.join("entities.json"), ENTITIES_JSON)
            .expect("metadata fixture should be written");
        std::fs::write(metadata_dir.join("source-map.json"), source_map)
            .expect("source-map fixture should be written");
        std::fs::write(root.join("mortar.sql.snap.json"), snapshot_json())
            .expect("snapshot fixture should be written");
        Self { root }
    }

    pub fn root(&self) -> &Path {
        &self.root
    }

    pub fn uri(&self, file_name: &str) -> Uri {
        file_uri_for_path(&self.root.join(file_name))
    }
}

impl Drop for R20LspBenchmarkWorkspace {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.root);
    }
}

pub fn canonical_generated_read_document() -> &'static str {
    r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#
}

pub fn local_metamodel_alias_document() -> &'static str {
    r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var client = QClient.CLIENT;
        client.read(renderer).findById(id);
    }
}
"#
}

pub fn local_read_namespace_alias_document() -> &'static str {
    r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var read = QClient.CLIENT.read(renderer);
        read.findById(id);
    }
}
"#
}

pub fn unsupported_alias_document() -> &'static str {
    r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var client = (QClient.CLIENT);
        client.read(renderer).findById(id);
    }
}
"#
}

pub fn stale_source_map_document() -> &'static str {
    r#"package example;

public final class ClientUsage {
    // mortar:snapshot=example.Client.findById
    void read(Object renderer, Long id) {
        var client = QClient.CLIENT;
        client.read(renderer).findById(id);
    }
}
"#
}

pub fn large_document_with_canonical_call(repetitions: usize) -> String {
    let mut document = String::from("package example;\n\npublic final class LargeClientUsage {\n");
    for index in 0..repetitions {
        document.push_str(&format!(
            "    // benchmark filler line {index}: ordinary Java text without generated calls\n"
        ));
    }
    document.push_str(
        "    void target(Object renderer, Long id) {\n        QClient.CLIENT.read(renderer).findById(id);\n    }\n}\n",
    );
    document
}

pub fn malformed_document() -> &'static str {
    r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
"#
}

pub fn state_with_document(
    workspace: &R20LspBenchmarkWorkspace,
    file_name: &str,
    document: &str,
) -> (LspState, Uri) {
    let uri = workspace.uri(file_name);
    let mut state = LspState::new(workspace.root().to_path_buf());
    state.open_document(&uri, document.to_string());
    (state, uri)
}

pub fn position_of(document: &str, needle: &str) -> Position {
    let offset = document.find(needle).expect("needle should exist");
    let before = &document[..offset];
    let line = before
        .chars()
        .filter(|character| *character == '\n')
        .count();
    let line_start = before.rfind('\n').map(|index| index + 1).unwrap_or(0);
    Position {
        line: u32::try_from(line).expect("line should fit in u32"),
        character: u32::try_from(document[line_start..offset].encode_utf16().count())
            .expect("character should fit in u32"),
    }
}

fn snapshot_json() -> &'static str {
    r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "example.Account.findAll",
      "sql": "select a.id, a.name from accounts a",
      "parameters": [],
      "metadata": {
        "tables": ["accounts"],
        "columns": ["accounts.id", "accounts.name"],
        "joins": []
      }
    },
    {
      "name": "example.Account.findById",
      "sql": "select a.id from accounts a where a.id = ?",
      "parameters": [],
      "metadata": {
        "tables": ["accounts"],
        "columns": ["accounts.id"],
        "joins": []
      }
    },
    {
      "name": "example.Client.findAll",
      "sql": "select c.id, c.name from clients c",
      "parameters": [],
      "metadata": {
        "tables": ["clients"],
        "columns": ["clients.id", "clients.name"],
        "joins": []
      }
    },
    {
      "name": "example.Client.findById",
      "sql": "select c.id from clients c where c.id = ?",
      "parameters": [],
      "metadata": {
        "tables": ["clients"],
        "columns": ["clients.id"],
        "joins": []
      }
    }
  ]
}
"#
}

fn file_uri_for_path(path: &Path) -> Uri {
    let normalized = path.to_string_lossy().replace('\\', "/");
    format!("file:///{normalized}")
        .parse()
        .expect("path URI should parse")
}
