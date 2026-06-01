use lsp_server::{Connection, Message, RequestId, Response};
use lsp_types::notification::{
    DidChangeTextDocument, DidOpenTextDocument, Notification, PublishDiagnostics,
};
use lsp_types::request::{
    CodeActionRequest, HoverRequest, Request as LspRequest, Shutdown as ShutdownRequest,
};
use lsp_types::{
    CodeAction, CodeActionKind, CodeActionOptions, CodeActionOrCommand, CodeActionParams,
    CodeActionProviderCapability, Command, Diagnostic, DiagnosticSeverity,
    DidChangeTextDocumentParams, DidOpenTextDocumentParams, Hover, HoverContents, HoverParams,
    HoverProviderCapability, InitializeParams, MarkedString, Position, PublishDiagnosticsParams,
    Range, ServerCapabilities, TextDocumentSyncCapability, TextDocumentSyncKind, Uri,
};
use mortar_compiler::{
    DatabaseSchema, MortarCompilerError, detect_schema_drift, parse_mortar_metadata_file,
    parse_sql_snapshot_file,
};
use std::collections::BTreeMap;
use std::path::PathBuf;

pub type LspResult<T> = Result<T, Box<dyn std::error::Error + Send + Sync>>;

pub fn server_capabilities() -> ServerCapabilities {
    ServerCapabilities {
        text_document_sync: Some(TextDocumentSyncCapability::Kind(TextDocumentSyncKind::FULL)),
        hover_provider: Some(HoverProviderCapability::Simple(true)),
        code_action_provider: Some(CodeActionProviderCapability::Options(CodeActionOptions {
            code_action_kinds: Some(vec![CodeActionKind::SOURCE]),
            ..CodeActionOptions::default()
        })),
        ..ServerCapabilities::default()
    }
}

pub fn hover_for_snapshot(
    snapshot_content: &str,
    snapshot_name: &str,
) -> Result<Option<Hover>, MortarCompilerError> {
    let snapshot_file = parse_sql_snapshot_file(snapshot_content)?;
    Ok(snapshot_file
        .snapshots
        .into_iter()
        .find(|snapshot| snapshot.name == snapshot_name)
        .map(|snapshot| Hover {
            contents: HoverContents::Scalar(MarkedString::String(format!(
                "Mortar SQL for `{}`\n\n```sql\n{}\n```",
                snapshot.name, snapshot.sql
            ))),
            range: None,
        }))
}

pub fn diagnostics_for_schema(
    metadata_content: &str,
    database_schema: &DatabaseSchema,
) -> Result<Vec<Diagnostic>, MortarCompilerError> {
    let metadata = parse_mortar_metadata_file(metadata_content)?;
    Ok(detect_schema_drift(&metadata, database_schema)
        .into_iter()
        .map(|drift| Diagnostic {
            range: Range {
                start: Position {
                    line: 0,
                    character: 0,
                },
                end: Position {
                    line: 0,
                    character: 0,
                },
            },
            severity: Some(DiagnosticSeverity::ERROR),
            code: Some(lsp_types::NumberOrString::String(
                "mortar-schema-drift".to_string(),
            )),
            code_description: None,
            source: Some("mortar".to_string()),
            message: drift.message,
            related_information: None,
            tags: None,
            data: None,
        })
        .collect())
}

pub fn copy_sql_code_action_for_snapshot(
    snapshot_content: &str,
    snapshot_name: &str,
) -> Result<Vec<CodeActionOrCommand>, MortarCompilerError> {
    sql_command_code_action_for_snapshot(
        snapshot_content,
        snapshot_name,
        "Copy generated SQL",
        "mortar.copySql",
    )
}

pub fn explain_sql_code_action_for_snapshot(
    snapshot_content: &str,
    snapshot_name: &str,
) -> Result<Vec<CodeActionOrCommand>, MortarCompilerError> {
    sql_command_code_action_for_snapshot(
        snapshot_content,
        snapshot_name,
        "Run PostgreSQL EXPLAIN",
        "mortar.explainSql",
    )
}

#[derive(Debug, Clone)]
pub struct LspState {
    workspace_roots: Vec<PathBuf>,
    documents: BTreeMap<String, String>,
}

impl LspState {
    pub fn new(workspace_root: PathBuf) -> Self {
        Self::with_workspace_roots(vec![workspace_root])
    }

    pub fn with_workspace_roots(workspace_roots: Vec<PathBuf>) -> Self {
        Self {
            workspace_roots: non_empty_workspace_roots(workspace_roots),
            documents: BTreeMap::new(),
        }
    }

    pub fn open_document(&mut self, uri: &Uri, text: String) {
        self.documents.insert(uri.as_str().to_string(), text);
    }

    pub fn change_document(&mut self, uri: &Uri, text: String) {
        self.documents.insert(uri.as_str().to_string(), text);
    }

    pub fn hover(&self, uri: &Uri, position: Position) -> LspResult<Option<Hover>> {
        let Some(snapshot_name) = self.snapshot_name_at(uri, position) else {
            return Ok(None);
        };
        let Some(snapshot_content) = self.read_snapshot_content(uri)? else {
            return Ok(None);
        };

        hover_for_snapshot(&snapshot_content, &snapshot_name).map_err(Into::into)
    }

    pub fn code_actions(
        &self,
        uri: &Uri,
        position: Position,
    ) -> LspResult<Vec<CodeActionOrCommand>> {
        let Some(snapshot_name) = self.snapshot_name_at(uri, position) else {
            return Ok(Vec::new());
        };
        let Some(snapshot_content) = self.read_snapshot_content(uri)? else {
            return Ok(Vec::new());
        };

        let mut actions = copy_sql_code_action_for_snapshot(&snapshot_content, &snapshot_name)?;
        actions.extend(explain_sql_code_action_for_snapshot(
            &snapshot_content,
            &snapshot_name,
        )?);
        Ok(actions)
    }

    pub fn document_diagnostics(&self, uri: &Uri) -> Vec<Diagnostic> {
        let Some(document) = self.documents.get(uri.as_str()) else {
            return Vec::new();
        };
        let markers = explicit_snapshot_markers(document);
        if markers.is_empty() {
            return Vec::new();
        }

        let Some(snapshot_content) = self.read_snapshot_content(uri).ok().flatten() else {
            return markers
                .into_iter()
                .map(|marker| marker.diagnostic("Mortar SQL snapshot file was not found"))
                .collect();
        };
        let snapshot_file = match parse_sql_snapshot_file(&snapshot_content) {
            Ok(snapshot_file) => snapshot_file,
            Err(error) => {
                return markers
                    .into_iter()
                    .map(|marker| {
                        marker.diagnostic(format!("Mortar SQL snapshot file is invalid: {error}"))
                    })
                    .collect();
            }
        };

        markers
            .into_iter()
            .filter(|marker| {
                !snapshot_file
                    .snapshots
                    .iter()
                    .any(|snapshot| snapshot.name == marker.snapshot_name)
            })
            .map(|marker| {
                marker.diagnostic(format!(
                    "Mortar SQL snapshot was not found: {}",
                    marker.snapshot_name
                ))
            })
            .collect()
    }

    fn snapshot_name_at(&self, uri: &Uri, position: Position) -> Option<String> {
        let document = self.documents.get(uri.as_str())?;
        explicit_snapshot_marker_before(document, position.line)
    }

    fn read_snapshot_content(&self, uri: &Uri) -> std::io::Result<Option<String>> {
        let snapshot_path = self
            .workspace_root_for_uri(uri)
            .join("mortar.sql.snap.json");
        if !snapshot_path.exists() {
            return Ok(None);
        }

        std::fs::read_to_string(snapshot_path).map(Some)
    }

    fn workspace_root_for_uri(&self, uri: &Uri) -> &PathBuf {
        let Some(document_path) = file_uri_to_path(uri.as_str()) else {
            return &self.workspace_roots[0];
        };

        self.workspace_roots
            .iter()
            .filter(|root| document_path.starts_with(root))
            .max_by_key(|root| root.components().count())
            .unwrap_or(&self.workspace_roots[0])
    }
}

fn sql_command_code_action_for_snapshot(
    snapshot_content: &str,
    snapshot_name: &str,
    title: &str,
    command: &str,
) -> Result<Vec<CodeActionOrCommand>, MortarCompilerError> {
    let snapshot_file = parse_sql_snapshot_file(snapshot_content)?;
    let Some(snapshot) = snapshot_file
        .snapshots
        .into_iter()
        .find(|snapshot| snapshot.name == snapshot_name)
    else {
        return Ok(Vec::new());
    };

    Ok(vec![CodeActionOrCommand::CodeAction(CodeAction {
        title: title.to_string(),
        kind: Some(CodeActionKind::SOURCE),
        diagnostics: None,
        edit: None,
        command: Some(Command {
            title: title.to_string(),
            command: command.to_string(),
            arguments: Some(vec![serde_json::json!(snapshot.sql)]),
        }),
        is_preferred: Some(true),
        disabled: None,
        data: None,
    })])
}

pub fn serve_stdio() -> LspResult<()> {
    let (connection, io_threads) = Connection::stdio();
    let initialize_result = serde_json::to_value(server_capabilities())?;
    let initialize_params = connection.initialize(initialize_result)?;
    let state =
        LspState::with_workspace_roots(workspace_roots_from_initialize_params(initialize_params));
    serve_initialized_connection(&connection, state)?;
    io_threads.join()?;
    Ok(())
}

pub fn serve_initialized_connection(connection: &Connection, mut state: LspState) -> LspResult<()> {
    for message in &connection.receiver {
        match message {
            Message::Request(request) => {
                if request.method == ShutdownRequest::METHOD {
                    connection.handle_shutdown(&request)?;
                    return Ok(());
                }
                handle_request(connection, &request, &state)?;
            }
            Message::Notification(notification) => {
                handle_notification(connection, &notification, &mut state)?;
            }
            Message::Response(_) => {}
        }
    }

    Ok(())
}

fn handle_notification(
    connection: &Connection,
    notification: &lsp_server::Notification,
    state: &mut LspState,
) -> LspResult<()> {
    match notification.method.as_str() {
        DidOpenTextDocument::METHOD => {
            let params: DidOpenTextDocumentParams =
                serde_json::from_value(notification.params.clone())?;
            let uri = params.text_document.uri;
            state.open_document(&uri, params.text_document.text);
            publish_document_diagnostics(connection, &uri, state)?;
        }
        DidChangeTextDocument::METHOD => {
            let params: DidChangeTextDocumentParams =
                serde_json::from_value(notification.params.clone())?;
            if let Some(change) = params.content_changes.into_iter().last() {
                let uri = params.text_document.uri;
                state.change_document(&uri, change.text);
                publish_document_diagnostics(connection, &uri, state)?;
            }
        }
        _ => {}
    }

    Ok(())
}

fn publish_document_diagnostics(
    connection: &Connection,
    uri: &Uri,
    state: &LspState,
) -> LspResult<()> {
    let params = PublishDiagnosticsParams {
        uri: uri.clone(),
        diagnostics: state.document_diagnostics(uri),
        version: None,
    };
    connection
        .sender
        .send(Message::Notification(lsp_server::Notification::new(
            PublishDiagnostics::METHOD.to_string(),
            params,
        )))?;
    Ok(())
}

fn handle_request(
    connection: &Connection,
    request: &lsp_server::Request,
    state: &LspState,
) -> LspResult<()> {
    match request.method.as_str() {
        HoverRequest::METHOD => {
            let params: HoverParams = serde_json::from_value(request.params.clone())?;
            let uri = params.text_document_position_params.text_document.uri;
            let position = params.text_document_position_params.position;
            let hover = state.hover(&uri, position)?;
            send_ok(connection, request.id.clone(), &hover)?;
        }
        CodeActionRequest::METHOD => {
            let params: CodeActionParams = serde_json::from_value(request.params.clone())?;
            let actions = state.code_actions(&params.text_document.uri, params.range.start)?;
            send_ok(connection, request.id.clone(), &actions)?;
        }
        _ => {
            let response = Response::new_err(
                request.id.clone(),
                lsp_server::ErrorCode::MethodNotFound as i32,
                format!("Mortar LSP method is not implemented: {}", request.method),
            );
            connection.sender.send(Message::Response(response))?;
        }
    }

    Ok(())
}

fn send_ok<T: serde::Serialize>(
    connection: &Connection,
    id: RequestId,
    result: &T,
) -> LspResult<()> {
    let response = Response::new_ok(id, serde_json::to_value(result)?);
    connection.sender.send(Message::Response(response))?;
    Ok(())
}

fn workspace_roots_from_initialize_params(params: serde_json::Value) -> Vec<PathBuf> {
    let Ok(params) = serde_json::from_value::<InitializeParams>(params) else {
        return vec![current_dir_or_default()];
    };

    if let Some(workspace_folders) = &params.workspace_folders {
        let roots = workspace_folders
            .iter()
            .filter_map(|folder| file_uri_to_path(folder.uri.as_str()))
            .collect::<Vec<_>>();
        if !roots.is_empty() {
            return roots;
        }
    }

    vec![root_uri_from_initialize_params(&params).unwrap_or_else(current_dir_or_default)]
}

#[allow(deprecated)]
fn root_uri_from_initialize_params(params: &InitializeParams) -> Option<PathBuf> {
    params
        .root_uri
        .as_ref()
        .and_then(|root_uri| file_uri_to_path(root_uri.as_str()))
}

fn file_uri_to_path(uri: &str) -> Option<PathBuf> {
    let path = uri.strip_prefix("file://")?;
    let decoded = percent_decode(path);
    Some(PathBuf::from(normalize_file_uri_path(&decoded)))
}

fn normalize_file_uri_path(decoded: &str) -> String {
    if let Some(localhost_path) = decoded.strip_prefix("localhost/") {
        return normalize_file_uri_path(localhost_path);
    }

    if let Some(windows_path) = decoded.strip_prefix('/')
        && is_windows_drive_path(windows_path)
    {
        return windows_path.to_string();
    }

    if is_windows_drive_path(decoded) {
        return decoded.to_string();
    }

    if !decoded.starts_with('/') && decoded.contains('/') {
        return format!("//{decoded}");
    }

    decoded.to_string()
}

fn is_windows_drive_path(path: &str) -> bool {
    let bytes = path.as_bytes();
    bytes.len() >= 2 && bytes[0].is_ascii_alphabetic() && bytes[1] == b':'
}

fn percent_decode(value: &str) -> String {
    let mut output = String::new();
    let mut index = 0;
    let bytes = value.as_bytes();
    while index < bytes.len() {
        if bytes[index] == b'%'
            && index + 2 < bytes.len()
            && let Ok(hex) = u8::from_str_radix(&value[index + 1..index + 3], 16)
        {
            output.push(hex as char);
            index += 3;
            continue;
        }

        output.push(bytes[index] as char);
        index += 1;
    }
    output
}

fn current_dir_or_default() -> PathBuf {
    std::env::current_dir().unwrap_or_else(|_| PathBuf::from("."))
}

fn non_empty_workspace_roots(workspace_roots: Vec<PathBuf>) -> Vec<PathBuf> {
    if workspace_roots.is_empty() {
        vec![current_dir_or_default()]
    } else {
        workspace_roots
    }
}

fn explicit_snapshot_marker_before(document: &str, line: u32) -> Option<String> {
    let lines = document.lines().collect::<Vec<_>>();
    if lines.is_empty() {
        return None;
    }
    let end = usize::try_from(line)
        .ok()
        .map(|line| line.min(lines.len().saturating_sub(1)))?;

    lines[..=end]
        .iter()
        .rev()
        .find_map(|line| parse_snapshot_marker(line))
}

fn parse_snapshot_marker(line: &str) -> Option<String> {
    let marker_start = line.find("mortar:snapshot")?;
    let marker = &line[marker_start + "mortar:snapshot".len()..];
    let snapshot_name = marker
        .trim_start_matches(|character: char| {
            character == '=' || character == ':' || character.is_whitespace()
        })
        .split_whitespace()
        .next()?;
    if snapshot_name.is_empty() {
        None
    } else {
        Some(snapshot_name.to_string())
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct SnapshotMarker {
    line: u32,
    snapshot_name: String,
}

impl SnapshotMarker {
    fn diagnostic(&self, message: impl Into<String>) -> Diagnostic {
        Diagnostic {
            range: Range {
                start: Position {
                    line: self.line,
                    character: 0,
                },
                end: Position {
                    line: self.line,
                    character: u32::try_from(self.snapshot_name.len()).unwrap_or(u32::MAX),
                },
            },
            severity: Some(DiagnosticSeverity::WARNING),
            code: Some(lsp_types::NumberOrString::String(
                "mortar-snapshot-missing".to_string(),
            )),
            code_description: None,
            source: Some("mortar".to_string()),
            message: message.into(),
            related_information: None,
            tags: None,
            data: None,
        }
    }
}

fn explicit_snapshot_markers(document: &str) -> Vec<SnapshotMarker> {
    document
        .lines()
        .enumerate()
        .filter_map(|(line, content)| {
            parse_snapshot_marker(content).map(|snapshot_name| SnapshotMarker {
                line: u32::try_from(line).unwrap_or(u32::MAX),
                snapshot_name,
            })
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::{
        LspState, copy_sql_code_action_for_snapshot, diagnostics_for_schema,
        explain_sql_code_action_for_snapshot, file_uri_to_path, handle_notification,
        handle_request, hover_for_snapshot, server_capabilities,
    };
    use lsp_server::{
        Connection, Message, Notification as ServerNotification, Request as ServerRequest,
        RequestId,
    };
    use lsp_types::notification::{DidOpenTextDocument, Notification, PublishDiagnostics};
    use lsp_types::request::{CodeActionRequest, HoverRequest, Request};
    use lsp_types::{
        CodeActionContext, CodeActionKind, CodeActionOptions, CodeActionOrCommand,
        CodeActionParams, CodeActionProviderCapability, DiagnosticSeverity,
        DidOpenTextDocumentParams, HoverContents, HoverParams, HoverProviderCapability,
        MarkedString, PartialResultParams, Position, PublishDiagnosticsParams, Range,
        TextDocumentIdentifier, TextDocumentItem, TextDocumentPositionParams,
        TextDocumentSyncCapability, TextDocumentSyncKind, Uri, WorkDoneProgressParams,
    };
    use mortar_compiler::{DatabaseSchema, DatabaseTable};
    use std::path::{Path, PathBuf};
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn publishes_sql_transparency_capabilities() {
        let capabilities = server_capabilities();

        assert!(matches!(
            capabilities.text_document_sync,
            Some(TextDocumentSyncCapability::Kind(TextDocumentSyncKind::FULL))
        ));
        assert!(matches!(
            capabilities.hover_provider,
            Some(HoverProviderCapability::Simple(true))
        ));
        assert!(matches!(
            capabilities.code_action_provider,
            Some(CodeActionProviderCapability::Options(CodeActionOptions {
                code_action_kinds: Some(ref kinds),
                ..
            })) if kinds == &vec![CodeActionKind::SOURCE]
        ));
        assert!(capabilities.definition_provider.is_none());
    }

    #[test]
    fn builds_hover_markdown_for_snapshot_sql() {
        let hover = hover_for_snapshot(
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.findById",
      "sql": "select c.id from clients c where c.id = ?",
      "parameters": [],
      "metadata": {
        "tables": ["clients"],
        "columns": ["clients.id"],
        "joins": []
      }
    }
  ]
}"#,
            "ClientRepository.findById",
        )
        .expect("snapshot file should parse")
        .expect("snapshot should exist");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("```sql\nselect c.id from clients c where c.id = ?\n```")
        ));
    }

    #[test]
    fn returns_no_hover_when_snapshot_is_unknown() {
        let hover = hover_for_snapshot(
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": []
}"#,
            "ClientRepository.missing",
        )
        .expect("snapshot file should parse");

        assert!(hover.is_none());
    }

    #[test]
    fn builds_diagnostics_for_schema_drift() {
        let diagnostics = diagnostics_for_schema(
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
            &DatabaseSchema {
                tables: vec![DatabaseTable {
                    name: "clients".to_string(),
                    columns: vec!["id".to_string()],
                }],
            },
        )
        .expect("metadata should parse");

        assert_eq!(diagnostics.len(), 1);
        assert_eq!(
            diagnostics[0].message,
            "Missing database column: clients.name"
        );
        assert_eq!(diagnostics[0].severity, Some(DiagnosticSeverity::ERROR));
        assert_eq!(diagnostics[0].source.as_deref(), Some("mortar"));
    }

    #[test]
    fn builds_copy_sql_code_action_for_snapshot() {
        let actions = copy_sql_code_action_for_snapshot(
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.findById",
      "sql": "select c.id from clients c where c.id = ?",
      "parameters": [],
      "metadata": {
        "tables": ["clients"],
        "columns": ["clients.id"],
        "joins": []
      }
    }
  ]
}"#,
            "ClientRepository.findById",
        )
        .expect("snapshot file should parse");

        assert_eq!(actions.len(), 1);
        let CodeActionOrCommand::CodeAction(action) = &actions[0] else {
            panic!("copy SQL should be a code action");
        };
        assert_eq!(action.title, "Copy generated SQL");
        assert_eq!(action.kind, Some(CodeActionKind::SOURCE));
        let command = action.command.as_ref().expect("code action needs command");
        assert_eq!(command.command, "mortar.copySql");
        assert_eq!(
            command.arguments.as_ref().expect("SQL argument")[0],
            serde_json::json!("select c.id from clients c where c.id = ?")
        );
    }

    #[test]
    fn builds_explain_sql_code_action_for_snapshot() {
        let actions = explain_sql_code_action_for_snapshot(
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.findById",
      "sql": "select c.id from clients c where c.id = ?",
      "parameters": [],
      "metadata": {
        "tables": ["clients"],
        "columns": ["clients.id"],
        "joins": []
      }
    }
  ]
}"#,
            "ClientRepository.findById",
        )
        .expect("snapshot file should parse");

        assert_eq!(actions.len(), 1);
        let CodeActionOrCommand::CodeAction(action) = &actions[0] else {
            panic!("explain SQL should be a code action");
        };
        assert_eq!(action.title, "Run PostgreSQL EXPLAIN");
        let command = action.command.as_ref().expect("code action needs command");
        assert_eq!(command.command, "mortar.explainSql");
        assert_eq!(
            command.arguments.as_ref().expect("SQL argument")[0],
            serde_json::json!("select c.id from clients c where c.id = ?")
        );
    }

    #[test]
    fn routes_hover_request_for_explicit_snapshot_marker() {
        let workspace = create_snapshot_workspace();
        let uri: Uri = "file:///ClientRepository.java"
            .parse()
            .expect("URI should parse");
        let mut state = LspState::new(workspace);
        state.open_document(
            &uri,
            r#"package example;

public final class ClientRepository {
    // mortar:snapshot=ClientRepository.findById
    public void findById(Long id) {}
}
"#
            .to_string(),
        );
        let (server, client) = Connection::memory();
        let request = ServerRequest {
            id: RequestId::from(1),
            method: HoverRequest::METHOD.to_string(),
            params: serde_json::to_value(HoverParams {
                text_document_position_params: TextDocumentPositionParams {
                    text_document: TextDocumentIdentifier { uri },
                    position: Position {
                        line: 4,
                        character: 17,
                    },
                },
                work_done_progress_params: WorkDoneProgressParams::default(),
            })
            .expect("hover params should serialize"),
        };

        handle_request(&server, &request, &state).expect("hover request should route");
        let Message::Response(response) = client.receiver.recv().expect("response should be sent")
        else {
            panic!("expected response");
        };
        let hover = serde_json::from_value::<Option<lsp_types::Hover>>(
            response.result.expect("response should be ok"),
        )
        .expect("hover should deserialize")
        .expect("hover should exist");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
        ));
    }

    #[test]
    fn routes_code_action_request_for_explicit_snapshot_marker() {
        let workspace = create_snapshot_workspace();
        let uri: Uri = "file:///ClientRepository.java"
            .parse()
            .expect("URI should parse");
        let mut state = LspState::new(workspace);
        state.open_document(
            &uri,
            r#"package example;

public final class ClientRepository {
    // mortar:snapshot=ClientRepository.findById
    public void findById(Long id) {}
}
"#
            .to_string(),
        );
        let (server, client) = Connection::memory();
        let request = ServerRequest {
            id: RequestId::from(2),
            method: CodeActionRequest::METHOD.to_string(),
            params: serde_json::to_value(CodeActionParams {
                text_document: TextDocumentIdentifier { uri },
                range: Range {
                    start: Position {
                        line: 4,
                        character: 17,
                    },
                    end: Position {
                        line: 4,
                        character: 17,
                    },
                },
                context: CodeActionContext::default(),
                work_done_progress_params: WorkDoneProgressParams::default(),
                partial_result_params: PartialResultParams::default(),
            })
            .expect("code action params should serialize"),
        };

        handle_request(&server, &request, &state).expect("code action request should route");
        let Message::Response(response) = client.receiver.recv().expect("response should be sent")
        else {
            panic!("expected response");
        };
        let actions = serde_json::from_value::<Vec<CodeActionOrCommand>>(
            response.result.expect("response should be ok"),
        )
        .expect("actions should deserialize");

        assert_eq!(actions.len(), 2);
    }

    #[test]
    fn publishes_diagnostic_when_snapshot_marker_is_missing() {
        let workspace = create_snapshot_workspace();
        let uri: Uri = "file:///ClientRepository.java"
            .parse()
            .expect("URI should parse");
        let mut state = LspState::new(workspace);
        let (server, client) = Connection::memory();
        let notification = ServerNotification {
            method: DidOpenTextDocument::METHOD.to_string(),
            params: serde_json::to_value(DidOpenTextDocumentParams {
                text_document: TextDocumentItem {
                    uri,
                    language_id: "java".to_string(),
                    version: 1,
                    text: r#"package example;

public final class ClientRepository {
    // mortar:snapshot=ClientRepository.missing
    public void findById(Long id) {}
}
"#
                    .to_string(),
                },
            })
            .expect("open params should serialize"),
        };

        handle_notification(&server, &notification, &mut state)
            .expect("open notification should publish diagnostics");
        let Message::Notification(notification) =
            client.receiver.recv().expect("diagnostics should be sent")
        else {
            panic!("expected diagnostics notification");
        };
        assert_eq!(notification.method, PublishDiagnostics::METHOD);
        let params: PublishDiagnosticsParams =
            serde_json::from_value(notification.params).expect("diagnostics should parse");

        assert_eq!(params.diagnostics.len(), 1);
        assert_eq!(
            params.diagnostics[0].message,
            "Mortar SQL snapshot was not found: ClientRepository.missing"
        );
        assert_eq!(
            params.diagnostics[0].severity,
            Some(DiagnosticSeverity::WARNING)
        );
    }

    #[test]
    fn preserves_unix_absolute_paths_from_file_uris() {
        let path = file_uri_to_path("file:///home/ricardo/mortar%20workspace")
            .expect("URI should convert to a path");

        assert_eq!(
            path.to_string_lossy().replace('\\', "/"),
            "/home/ricardo/mortar workspace"
        );
    }

    #[test]
    fn normalizes_windows_drive_paths_from_file_uris() {
        let path =
            file_uri_to_path("file:///Z:/workspace/mortar").expect("URI should convert to a path");

        assert_eq!(
            path.to_string_lossy().replace('\\', "/"),
            "Z:/workspace/mortar"
        );
    }

    #[test]
    fn uses_document_workspace_root_for_multi_root_snapshots() {
        let first_workspace = create_snapshot_workspace_with(
            "FirstRepository.find",
            "select first_value from first_table",
        );
        let second_workspace = create_snapshot_workspace_with(
            "SecondRepository.find",
            "select second_value from second_table",
        );
        let document_path = second_workspace.join("SecondRepository.java");
        let uri = file_uri_for_path(&document_path);
        let mut state = LspState::with_workspace_roots(vec![first_workspace, second_workspace]);
        state.open_document(
            &uri,
            r#"package example;

public final class SecondRepository {
    // mortar:snapshot=SecondRepository.find
    public void find() {}
}
"#
            .to_string(),
        );

        let hover = state
            .hover(
                &uri,
                Position {
                    line: 4,
                    character: 16,
                },
            )
            .expect("hover should resolve")
            .expect("hover should use the second workspace snapshot");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select second_value from second_table")
        ));
    }

    fn create_snapshot_workspace() -> PathBuf {
        create_snapshot_workspace_with(
            "ClientRepository.findById",
            "select c.id from clients c where c.id = ?",
        )
    }

    fn create_snapshot_workspace_with(snapshot_name: &str, sql: &str) -> PathBuf {
        let id = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("time should be monotonic")
            .as_nanos();
        let workspace = std::env::temp_dir().join(format!("mortar-lsp-test-{id}"));
        std::fs::create_dir_all(&workspace).expect("workspace should be created");
        std::fs::write(
            workspace.join("mortar.sql.snap.json"),
            format!(
                r#"{{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {{
      "name": "{snapshot_name}",
      "sql": "{sql}",
      "parameters": [],
      "metadata": {{
        "tables": ["clients"],
        "columns": ["clients.id"],
        "joins": []
      }}
    }}
  ]
}}"#
            ),
        )
        .expect("snapshot file should be written");
        workspace
    }

    fn file_uri_for_path(path: &Path) -> Uri {
        let normalized = path.to_string_lossy().replace('\\', "/");
        format!("file:///{normalized}")
            .parse()
            .expect("path URI should parse")
    }
}
