use lsp_server::{Connection, Message, RequestId, Response};
use lsp_types::notification::{
    DidChangeTextDocument, DidOpenTextDocument, Notification, PublishDiagnostics,
};
use lsp_types::request::{
    CodeActionRequest, GotoDefinition, HoverRequest, Request as LspRequest,
    Shutdown as ShutdownRequest,
};
use lsp_types::{
    CodeAction, CodeActionKind, CodeActionOptions, CodeActionOrCommand, CodeActionParams,
    CodeActionProviderCapability, Command, Diagnostic, DiagnosticSeverity,
    DidChangeTextDocumentParams, DidOpenTextDocumentParams, GotoDefinitionParams,
    GotoDefinitionResponse, Hover, HoverContents, HoverParams, HoverProviderCapability,
    InitializeParams, Location, MarkedString, OneOf, Position, PublishDiagnosticsParams, Range,
    ServerCapabilities, TextDocumentSyncCapability, TextDocumentSyncKind, Uri,
};
use mortar_compiler::{
    DatabaseSchema, MortarCompilerError, detect_schema_drift, detect_source_map_freshness,
    parse_mortar_metadata_file, parse_mortar_source_map_file, parse_sql_snapshot_file,
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
        definition_provider: Some(OneOf::Left(true)),
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
        match self.source_map_snapshot_name_at(uri, position)? {
            GeneratedQueryResolution::Snapshot(snapshot_name) => {
                let Some(snapshot_content) = self.read_snapshot_content(uri)? else {
                    return Ok(None);
                };
                return Ok(hover_for_snapshot(&snapshot_content, &snapshot_name).unwrap_or(None));
            }
            GeneratedQueryResolution::FailClosed => return Ok(None),
            GeneratedQueryResolution::NotGeneratedCall => {}
        }

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
        match self.source_map_snapshot_name_at(uri, position)? {
            GeneratedQueryResolution::Snapshot(snapshot_name) => {
                let Some(snapshot_content) = self.read_snapshot_content(uri)? else {
                    return Ok(Vec::new());
                };
                let Ok(mut actions) =
                    copy_sql_code_action_for_snapshot(&snapshot_content, &snapshot_name)
                else {
                    return Ok(Vec::new());
                };
                let Ok(explain_actions) =
                    explain_sql_code_action_for_snapshot(&snapshot_content, &snapshot_name)
                else {
                    return Ok(Vec::new());
                };
                actions.extend(explain_actions);
                return Ok(actions);
            }
            GeneratedQueryResolution::FailClosed => return Ok(Vec::new()),
            GeneratedQueryResolution::NotGeneratedCall => {}
        }

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

    pub fn definition(
        &self,
        uri: &Uri,
        position: Position,
    ) -> LspResult<Option<GotoDefinitionResponse>> {
        let GeneratedQueryResolution::Snapshot(snapshot_name) =
            self.source_map_snapshot_name_at(uri, position)?
        else {
            return Ok(None);
        };
        let Some((snapshot_path, snapshot_content)) = self.read_snapshot(uri)? else {
            return Ok(None);
        };
        let Ok(snapshot_file) = parse_sql_snapshot_file(&snapshot_content) else {
            return Ok(None);
        };
        if !snapshot_file
            .snapshots
            .iter()
            .any(|snapshot| snapshot.name == snapshot_name)
        {
            return Ok(None);
        }
        let Some(range) = snapshot_name_range(&snapshot_content, &snapshot_name) else {
            return Ok(None);
        };

        Ok(Some(GotoDefinitionResponse::Scalar(Location {
            uri: file_uri_for_path(&snapshot_path)?,
            range,
        })))
    }

    pub fn document_diagnostics(&self, uri: &Uri) -> Vec<Diagnostic> {
        let Some(document) = self.documents.get(uri.as_str()) else {
            return Vec::new();
        };
        let mut diagnostics = self.generated_call_diagnostics(uri, document);
        let markers = explicit_snapshot_markers(document);
        if markers.is_empty() {
            return diagnostics;
        }

        let Some(snapshot_content) = self.read_snapshot_content(uri).ok().flatten() else {
            diagnostics.extend(
                markers
                    .into_iter()
                    .map(|marker| marker.diagnostic("Mortar SQL snapshot file was not found")),
            );
            return diagnostics;
        };
        let snapshot_file = match parse_sql_snapshot_file(&snapshot_content) {
            Ok(snapshot_file) => snapshot_file,
            Err(error) => {
                diagnostics.extend(markers.into_iter().map(|marker| {
                    marker.diagnostic(format!("Mortar SQL snapshot file is invalid: {error}"))
                }));
                return diagnostics;
            }
        };

        diagnostics.extend(
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
                }),
        );
        diagnostics
    }

    fn snapshot_name_at(&self, uri: &Uri, position: Position) -> Option<String> {
        let document = self.documents.get(uri.as_str())?;
        explicit_snapshot_marker_before(document, position.line)
    }

    fn source_map_snapshot_name_at(
        &self,
        uri: &Uri,
        position: Position,
    ) -> LspResult<GeneratedQueryResolution> {
        let Some(document) = self.documents.get(uri.as_str()) else {
            return Ok(GeneratedQueryResolution::NotGeneratedCall);
        };
        let generated_call = match generated_call_at(document, position) {
            GeneratedCallDetection::Call(call) => call,
            GeneratedCallDetection::FailClosed { .. } => {
                return Ok(GeneratedQueryResolution::FailClosed);
            }
            GeneratedCallDetection::NotGeneratedCall => {
                return Ok(GeneratedQueryResolution::NotGeneratedCall);
            }
        };
        let Some((metadata_content, source_map_content)) = self.read_source_map_inputs(uri)? else {
            return Ok(GeneratedQueryResolution::FailClosed);
        };

        let metadata = match parse_mortar_metadata_file(&metadata_content) {
            Ok(metadata) => metadata,
            Err(_) => return Ok(GeneratedQueryResolution::FailClosed),
        };
        let source_map = match parse_mortar_source_map_file(&source_map_content) {
            Ok(source_map) => source_map,
            Err(_) => return Ok(GeneratedQueryResolution::FailClosed),
        };
        if !detect_source_map_freshness(&metadata, &source_map).is_empty() {
            return Ok(GeneratedQueryResolution::FailClosed);
        }

        let mut candidates = source_map
            .queries
            .into_iter()
            .filter(|query| generated_call.matches(query))
            .collect::<Vec<_>>();
        if candidates.len() != 1 {
            return Ok(GeneratedQueryResolution::FailClosed);
        };
        Ok(GeneratedQueryResolution::Snapshot(
            candidates.remove(0).snapshot,
        ))
    }

    fn read_snapshot_content(&self, uri: &Uri) -> std::io::Result<Option<String>> {
        Ok(self.read_snapshot(uri)?.map(|(_, content)| content))
    }

    fn read_snapshot(&self, uri: &Uri) -> std::io::Result<Option<(PathBuf, String)>> {
        let snapshot_path = self
            .workspace_root_for_uri(uri)
            .join("mortar.sql.snap.json");
        if !snapshot_path.exists() {
            return Ok(None);
        }

        std::fs::read_to_string(&snapshot_path).map(|content| Some((snapshot_path, content)))
    }

    fn read_source_map_inputs(&self, uri: &Uri) -> std::io::Result<Option<(String, String)>> {
        let root = self.workspace_root_for_uri(uri);
        for base in mortar_metadata_bases(root) {
            let metadata_path = base.join("entities.json");
            let source_map_path = base.join("source-map.json");
            if metadata_path.exists() && source_map_path.exists() {
                return Ok(Some((
                    std::fs::read_to_string(metadata_path)?,
                    std::fs::read_to_string(source_map_path)?,
                )));
            }
        }
        Ok(None)
    }

    fn generated_call_diagnostics(&self, uri: &Uri, document: &str) -> Vec<Diagnostic> {
        generated_call_markers(document)
            .into_iter()
            .filter_map(|marker| {
                match self
                    .source_map_snapshot_name_at(uri, marker.position())
                    .unwrap_or(GeneratedQueryResolution::FailClosed)
                {
                    GeneratedQueryResolution::Snapshot(snapshot_name) => {
                        self.snapshot_exists(uri, &snapshot_name).map_or_else(
                            |error| {
                                Some(marker.diagnostic(format!(
                                    "Mortar SQL snapshot file is invalid: {error}"
                                )))
                            },
                            |exists| {
                                (!exists).then(|| {
                                    marker.diagnostic(format!(
                                        "Mortar SQL snapshot was not found: {snapshot_name}"
                                    ))
                                })
                            },
                        )
                    }
                    GeneratedQueryResolution::FailClosed => {
                        Some(marker.diagnostic("Mortar source-map metadata is stale or missing"))
                    }
                    GeneratedQueryResolution::NotGeneratedCall => None,
                }
            })
            .collect()
    }

    fn snapshot_exists(&self, uri: &Uri, snapshot_name: &str) -> LspResult<bool> {
        let Some(snapshot_content) = self.read_snapshot_content(uri)? else {
            return Ok(false);
        };
        let snapshot_file = parse_sql_snapshot_file(&snapshot_content)?;
        Ok(snapshot_file
            .snapshots
            .iter()
            .any(|snapshot| snapshot.name == snapshot_name))
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
        GotoDefinition::METHOD => {
            let params: GotoDefinitionParams = serde_json::from_value(request.params.clone())?;
            let uri = params.text_document_position_params.text_document.uri;
            let position = params.text_document_position_params.position;
            let definition = state.definition(&uri, position)?;
            send_ok(connection, request.id.clone(), &definition)?;
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

#[derive(Debug, Clone, PartialEq, Eq)]
enum GeneratedQueryResolution {
    Snapshot(String),
    FailClosed,
    NotGeneratedCall,
}

fn mortar_metadata_bases(root: &std::path::Path) -> Vec<PathBuf> {
    vec![
        root.join("build")
            .join("classes")
            .join("java")
            .join("main")
            .join("META-INF")
            .join("mortar"),
        root.join("build")
            .join("classes")
            .join("java")
            .join("test")
            .join("META-INF")
            .join("mortar"),
        root.join("META-INF").join("mortar"),
    ]
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum GeneratedCallDetection {
    Call(GeneratedCall),
    FailClosed { start: usize, end: usize },
    NotGeneratedCall,
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct GeneratedCall {
    generated_entity_type: String,
    generated_read_namespace: String,
    generated_member: String,
    start: usize,
    end: usize,
}

impl GeneratedCall {
    fn matches(&self, query: &mortar_compiler::MortarSourceMapQuery) -> bool {
        query.generated_member == self.generated_member
            && java_type_matches(&query.generated_entity_type, &self.generated_entity_type)
            && java_type_matches(
                &query.generated_read_namespace,
                &self.generated_read_namespace,
            )
    }
}

fn generated_call_at(document: &str, position: Position) -> GeneratedCallDetection {
    let Some(offset) = document_offset(document, position) else {
        return GeneratedCallDetection::NotGeneratedCall;
    };

    for method in ["findById", "findAll"] {
        for (method_start, method_end) in token_ranges(document, method) {
            if offset >= method_start && offset <= method_end {
                return generated_call_before_method(document, method_start, method);
            }
            match generated_call_before_method(document, method_start, method) {
                GeneratedCallDetection::Call(call)
                    if offset >= call.start && offset <= call.end =>
                {
                    return GeneratedCallDetection::Call(call);
                }
                GeneratedCallDetection::FailClosed { start, end }
                    if offset >= start && offset <= end =>
                {
                    return GeneratedCallDetection::FailClosed { start, end };
                }
                _ => {}
            }
        }
    }

    GeneratedCallDetection::NotGeneratedCall
}

fn generated_call_before_method(
    document: &str,
    method_start: usize,
    method: &str,
) -> GeneratedCallDetection {
    let Some(method_dot) = previous_non_whitespace(document, method_start) else {
        return GeneratedCallDetection::NotGeneratedCall;
    };
    if document.as_bytes()[method_dot] != b'.' {
        return GeneratedCallDetection::NotGeneratedCall;
    }
    let Some(read_call_end) = previous_non_whitespace(document, method_dot) else {
        return GeneratedCallDetection::NotGeneratedCall;
    };
    if document.as_bytes()[read_call_end] != b')' {
        return generated_looking_fail_closed(document, method_start);
    }
    let Some(read_call_start) = matching_open_paren(document, read_call_end) else {
        return GeneratedCallDetection::FailClosed {
            start: read_call_end,
            end: method_start + method.len(),
        };
    };
    let Some(read_name_start) = previous_identifier_start(document, read_call_start) else {
        return GeneratedCallDetection::FailClosed {
            start: read_call_start,
            end: method_start + method.len(),
        };
    };
    if &document[read_name_start..read_call_start] != "read" {
        return generated_looking_fail_closed(document, method_start);
    }
    let Some(read_dot) = previous_non_whitespace(document, read_name_start) else {
        return GeneratedCallDetection::FailClosed {
            start: read_name_start,
            end: method_start + method.len(),
        };
    };
    if document.as_bytes()[read_dot] != b'.' {
        return GeneratedCallDetection::FailClosed {
            start: read_name_start,
            end: method_start + method.len(),
        };
    }
    let receiver = receiver_chain_before(document, read_dot);
    let Some(receiver) = receiver else {
        return GeneratedCallDetection::FailClosed {
            start: read_name_start,
            end: method_start + method.len(),
        };
    };
    let call_end = method_call_end(document, method_start + method.len())
        .unwrap_or(method_start + method.len());
    let fail_closed = GeneratedCallDetection::FailClosed {
        start: receiver.start,
        end: call_end,
    };
    if receiver.parts.is_empty() {
        return fail_closed;
    }

    let imports = JavaImports::parse(document);
    let Some(generated_entity_type) = generated_entity_type_for_receiver(&receiver.parts, &imports)
    else {
        return if receiver_is_generated_like(&receiver.parts) {
            fail_closed
        } else {
            GeneratedCallDetection::NotGeneratedCall
        };
    };
    let generated_read_namespace = format!("{generated_entity_type}.Read");

    GeneratedCallDetection::Call(GeneratedCall {
        generated_entity_type,
        generated_read_namespace,
        generated_member: format!("read.{method}"),
        start: receiver.start,
        end: call_end,
    })
}

fn generated_looking_fail_closed(document: &str, method_start: usize) -> GeneratedCallDetection {
    let prefix = document[..method_start]
        .chars()
        .rev()
        .take(80)
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .collect::<String>();
    if prefix.contains(".read") || prefix.contains("read.") {
        GeneratedCallDetection::FailClosed {
            start: method_start.saturating_sub(prefix.len()),
            end: method_start,
        }
    } else {
        GeneratedCallDetection::NotGeneratedCall
    }
}

fn generated_call_markers(document: &str) -> Vec<GeneratedCallMarker> {
    ["findById", "findAll"]
        .into_iter()
        .flat_map(|method| {
            token_ranges(document, method)
                .into_iter()
                .filter_map(move |(start, _)| {
                    let member = format!("read.{method}");
                    match generated_call_before_method(document, start, method) {
                        GeneratedCallDetection::Call(_)
                        | GeneratedCallDetection::FailClosed { .. } => {
                            position_for_offset(document, start).map(|position| {
                                GeneratedCallMarker {
                                    line: position.line,
                                    character: position.character,
                                    member: member.clone(),
                                }
                            })
                        }
                        GeneratedCallDetection::NotGeneratedCall => None,
                    }
                })
        })
        .collect()
}

fn document_offset(document: &str, position: Position) -> Option<usize> {
    let target_line = usize::try_from(position.line).ok()?;
    let target_character = position.character;
    let mut offset = 0usize;
    for (line_index, line) in document.split_inclusive('\n').enumerate() {
        let line_without_newline = line.trim_end_matches(['\r', '\n']);
        if line_index == target_line {
            return Some(
                offset + byte_offset_for_utf16_character(line_without_newline, target_character),
            );
        }
        offset += line.len();
    }
    if target_line == document.lines().count() {
        return Some(document.len());
    }
    None
}

fn position_for_offset(document: &str, target_offset: usize) -> Option<Position> {
    let mut line = 0u32;
    let mut line_start = 0usize;
    for (offset, character) in document.char_indices() {
        if offset >= target_offset {
            return Some(Position {
                line,
                character: utf16_len(&document[line_start..target_offset]),
            });
        }
        if character == '\n' {
            line = line.saturating_add(1);
            line_start = offset + 1;
        }
    }
    Some(Position {
        line,
        character: utf16_len(&document[line_start..target_offset]),
    })
}

fn byte_offset_for_utf16_character(line: &str, target_character: u32) -> usize {
    let mut utf16_character = 0u32;
    for (byte_offset, character) in line.char_indices() {
        if utf16_character >= target_character {
            return byte_offset;
        }
        utf16_character = utf16_character.saturating_add(character.len_utf16() as u32);
    }
    line.len()
}

fn utf16_len(value: &str) -> u32 {
    u32::try_from(value.encode_utf16().count()).unwrap_or(u32::MAX)
}

fn token_ranges(document: &str, token: &str) -> Vec<(usize, usize)> {
    let mut ranges = Vec::new();
    let mut offset = 0usize;
    while let Some(index) = document[offset..].find(token) {
        let start = offset + index;
        let end = start + token.len();
        if token_start_boundary(document, start) && token_end_boundary(document, end) {
            ranges.push((start, end));
        }
        offset = end;
    }
    ranges
}

fn token_start_boundary(document: &str, index: usize) -> bool {
    if index == 0 {
        return true;
    }
    !document.as_bytes()[index - 1].is_ascii_alphanumeric()
        && document.as_bytes()[index - 1] != b'_'
        && document.as_bytes()[index - 1] != b'$'
}

fn token_end_boundary(document: &str, index: usize) -> bool {
    if index >= document.len() {
        return true;
    }
    !document.as_bytes()[index].is_ascii_alphanumeric()
        && document.as_bytes()[index] != b'_'
        && document.as_bytes()[index] != b'$'
}

fn previous_non_whitespace(document: &str, before: usize) -> Option<usize> {
    document[..before]
        .char_indices()
        .rev()
        .find_map(|(index, character)| (!character.is_whitespace()).then_some(index))
}

fn previous_identifier_start(document: &str, before: usize) -> Option<usize> {
    let end = previous_non_whitespace(document, before)? + 1;
    let mut start = end;
    for (index, character) in document[..end].char_indices().rev() {
        if is_java_identifier_part(character) {
            start = index;
        } else {
            break;
        }
    }
    (start < end).then_some(start)
}

fn matching_open_paren(document: &str, close_index: usize) -> Option<usize> {
    let mut depth = 0usize;
    for (index, character) in document[..=close_index].char_indices().rev() {
        match character {
            ')' => depth = depth.saturating_add(1),
            '(' => {
                depth = depth.saturating_sub(1);
                if depth == 0 {
                    return Some(index);
                }
            }
            _ => {}
        }
    }
    None
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct ReceiverChain {
    parts: Vec<String>,
    start: usize,
}

fn receiver_chain_before(document: &str, before_dot: usize) -> Option<ReceiverChain> {
    let prefix = document[..before_dot].trim_end();
    let mut start = prefix.len();
    for (index, character) in prefix.char_indices().rev() {
        if is_java_identifier_part(character) || character == '.' {
            start = index;
        } else if character.is_whitespace() {
            continue;
        } else {
            break;
        }
    }
    let parts = prefix[start..]
        .split('.')
        .map(str::trim)
        .filter(|part| !part.is_empty())
        .map(str::to_string)
        .collect::<Vec<_>>();
    Some(ReceiverChain { parts, start })
}

fn method_call_end(document: &str, after_method: usize) -> Option<usize> {
    let open_index = next_non_whitespace(document, after_method)?;
    if document.as_bytes()[open_index] != b'(' {
        return None;
    }
    matching_close_paren(document, open_index).map(|close| close + 1)
}

fn next_non_whitespace(document: &str, after: usize) -> Option<usize> {
    document[after..]
        .char_indices()
        .find_map(|(index, character)| (!character.is_whitespace()).then_some(after + index))
}

fn matching_close_paren(document: &str, open_index: usize) -> Option<usize> {
    let mut depth = 0usize;
    for (index, character) in document[open_index..].char_indices() {
        match character {
            '(' => depth = depth.saturating_add(1),
            ')' => {
                depth = depth.saturating_sub(1);
                if depth == 0 {
                    return Some(open_index + index);
                }
            }
            _ => {}
        }
    }
    None
}

fn generated_entity_type_for_receiver(
    receiver: &[String],
    imports: &JavaImports,
) -> Option<String> {
    match receiver {
        [metamodel, _constant] if metamodel.starts_with('Q') => Some(
            imports
                .type_imports
                .get(metamodel)
                .cloned()
                .unwrap_or_else(|| metamodel.clone()),
        ),
        [constant] => imports.static_field_imports.get(constant).cloned(),
        _ => None,
    }
}

fn receiver_is_generated_like(receiver: &[String]) -> bool {
    match receiver {
        [metamodel, _constant] => metamodel.starts_with('Q'),
        [constant] => constant.chars().all(|character| {
            character == '_' || character == '$' || character.is_ascii_uppercase()
        }),
        _ => false,
    }
}

fn java_type_matches(query_value: &str, call_value: &str) -> bool {
    query_value == call_value
        || query_value
            .strip_suffix(call_value)
            .is_some_and(|prefix| prefix.ends_with('.'))
}

fn simple_java_name(java_type: &str) -> &str {
    java_type.rsplit('.').next().unwrap_or(java_type)
}

fn is_java_identifier_part(character: char) -> bool {
    character == '_' || character == '$' || character.is_ascii_alphanumeric()
}

#[derive(Debug, Default)]
struct JavaImports {
    type_imports: BTreeMap<String, String>,
    static_field_imports: BTreeMap<String, String>,
}

impl JavaImports {
    fn parse(document: &str) -> Self {
        let mut imports = JavaImports::default();
        for line in document.lines() {
            let trimmed = line.trim();
            if let Some(imported) = trimmed
                .strip_prefix("import static ")
                .and_then(|value| value.strip_suffix(';'))
            {
                if imported.ends_with(".*") {
                    continue;
                }
                let Some((owner, field)) = imported.rsplit_once('.') else {
                    continue;
                };
                imports
                    .static_field_imports
                    .insert(field.to_string(), owner.to_string());
            } else if let Some(imported) = trimmed
                .strip_prefix("import ")
                .and_then(|value| value.strip_suffix(';'))
            {
                if imported.ends_with(".*") {
                    continue;
                }
                let simple = simple_java_name(imported).to_string();
                imports.type_imports.insert(simple, imported.to_string());
            }
        }
        imports
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct GeneratedCallMarker {
    line: u32,
    character: u32,
    member: String,
}

impl GeneratedCallMarker {
    fn position(&self) -> Position {
        Position {
            line: self.line,
            character: self.character,
        }
    }

    fn diagnostic(&self, message: impl Into<String>) -> Diagnostic {
        let member_len = utf16_len(&self.member);
        Diagnostic {
            range: Range {
                start: self.position(),
                end: Position {
                    line: self.line,
                    character: self.character.saturating_add(member_len),
                },
            },
            severity: Some(DiagnosticSeverity::WARNING),
            code: Some(lsp_types::NumberOrString::String(
                "mortar-source-map-stale".to_string(),
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

fn snapshot_name_range(snapshot_content: &str, snapshot_name: &str) -> Option<Range> {
    let needle = format!("\"name\": \"{snapshot_name}\"");
    snapshot_content
        .lines()
        .enumerate()
        .find_map(|(line, content)| {
            content.find(&needle).map(|character| Range {
                start: Position {
                    line: u32::try_from(line).unwrap_or(u32::MAX),
                    character: utf16_len(&content[..character]),
                },
                end: Position {
                    line: u32::try_from(line).unwrap_or(u32::MAX),
                    character: utf16_len(&content[..character + needle.len()]),
                },
            })
        })
}

fn file_uri_for_path(path: &std::path::Path) -> LspResult<Uri> {
    let normalized = path.to_string_lossy().replace('\\', "/");
    format!("file:///{normalized}").parse().map_err(Into::into)
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
                    character: utf16_len(&self.snapshot_name),
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
    use lsp_types::request::{CodeActionRequest, GotoDefinition, HoverRequest, Request};
    use lsp_types::{
        CodeActionContext, CodeActionKind, CodeActionOptions, CodeActionOrCommand,
        CodeActionParams, CodeActionProviderCapability, DiagnosticSeverity,
        DidOpenTextDocumentParams, GotoDefinitionParams, GotoDefinitionResponse, HoverContents,
        HoverParams, HoverProviderCapability, MarkedString, PartialResultParams, Position,
        PublishDiagnosticsParams, Range, TextDocumentIdentifier, TextDocumentItem,
        TextDocumentPositionParams, TextDocumentSyncCapability, TextDocumentSyncKind, Uri,
        WorkDoneProgressParams,
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
        assert!(capabilities.definition_provider.is_some());
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
    fn routes_hover_request_for_source_map_backed_generated_call() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "findById"))
            .expect("hover should resolve")
            .expect("source-map-backed hover should exist");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
        ));
    }

    #[test]
    fn generated_call_resolves_from_receiver_position_without_marker_fallback() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    // mortar:snapshot=example.Account.findById
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "QClient"))
            .expect("hover should resolve")
            .expect("receiver-position hover should resolve generated source-map call");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
                    && !value.contains("select a.id from accounts a where a.id = ?")
        ));
    }

    #[test]
    fn routes_hover_request_for_source_map_backed_find_all_call() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        state.open_document(
            &uri,
            r#"package example;

public final class ClientUsage {
    void read(Object renderer) {
        QClient.CLIENT.read(renderer).findAll();
    }
}
"#
            .to_string(),
        );

        let hover = state
            .hover(
                &uri,
                Position {
                    line: 4,
                    character: 40,
                },
            )
            .expect("hover should resolve")
            .expect("source-map-backed hover should exist");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id, c.name from clients c")
        ));
    }

    #[test]
    fn routes_code_actions_for_source_map_backed_generated_call() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        state.open_document(
            &uri,
            r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#
            .to_string(),
        );

        let actions = state
            .code_actions(
                &uri,
                Position {
                    line: 4,
                    character: 40,
                },
            )
            .expect("code actions should resolve");

        assert_eq!(actions.len(), 2);
        let titles = actions
            .iter()
            .map(|action| match action {
                CodeActionOrCommand::CodeAction(action) => action.title.as_str(),
                CodeActionOrCommand::Command(command) => command.title.as_str(),
            })
            .collect::<Vec<_>>();
        assert!(titles.contains(&"Copy generated SQL"));
        assert!(titles.contains(&"Run PostgreSQL EXPLAIN"));
    }

    #[test]
    fn routes_definition_request_to_source_map_snapshot_entry() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace.clone());
        state.open_document(
            &uri,
            r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#
            .to_string(),
        );
        let (server, client) = Connection::memory();
        let request = ServerRequest {
            id: RequestId::from(3),
            method: GotoDefinition::METHOD.to_string(),
            params: serde_json::to_value(GotoDefinitionParams {
                text_document_position_params: TextDocumentPositionParams {
                    text_document: TextDocumentIdentifier { uri },
                    position: Position {
                        line: 4,
                        character: 40,
                    },
                },
                work_done_progress_params: WorkDoneProgressParams::default(),
                partial_result_params: PartialResultParams::default(),
            })
            .expect("definition params should serialize"),
        };

        handle_request(&server, &request, &state).expect("definition request should route");
        let Message::Response(response) = client.receiver.recv().expect("response should be sent")
        else {
            panic!("expected response");
        };
        let definition = serde_json::from_value::<Option<GotoDefinitionResponse>>(
            response.result.expect("response should be ok"),
        )
        .expect("definition should deserialize")
        .expect("definition should exist");

        let GotoDefinitionResponse::Scalar(location) = definition else {
            panic!("expected one snapshot location");
        };
        assert_eq!(
            location.uri,
            file_uri_for_path(&workspace.join("mortar.sql.snap.json"))
        );
        assert_eq!(
            location.range.start.line,
            snapshot_line(&workspace, "example.Client.findById")
        );
    }

    #[test]
    fn generated_call_uses_static_import_to_resolve_metamodel_context() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        state.open_document(
            &uri,
            r#"package example;

import static example.QClient.CLIENT;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        CLIENT.read(renderer).findById(id);
    }
}
"#
            .to_string(),
        );

        let hover = state
            .hover(
                &uri,
                Position {
                    line: 6,
                    character: 32,
                },
            )
            .expect("hover should resolve")
            .expect("static-imported generated call should resolve");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
        ));
    }

    #[test]
    fn generated_call_disambiguates_shared_members_by_metamodel_context() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("AccountUsage.java"));
        let mut state = LspState::new(workspace);
        state.open_document(
            &uri,
            r#"package example;

public final class AccountUsage {
    void read(Object renderer, Long id) {
        QAccount.ACCOUNT.read(renderer).findById(id);
    }
}
"#
            .to_string(),
        );

        let hover = state
            .hover(
                &uri,
                Position {
                    line: 4,
                    character: 42,
                },
            )
            .expect("hover should resolve")
            .expect("metamodel-backed generated call should resolve");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select a.id from accounts a where a.id = ?")
        ));
    }

    #[test]
    fn generated_call_with_stale_source_map_does_not_fall_back_to_marker() {
        let workspace = create_stale_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    // mortar:snapshot=example.Client.findById
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "QClient"))
            .expect("hover should fail closed without an error");

        assert!(hover.is_none());

        let diagnostics = state.document_diagnostics(&uri);
        assert_eq!(diagnostics.len(), 1);
        assert_eq!(
            diagnostics[0].message,
            "Mortar source-map metadata is stale or missing"
        );
        assert_eq!(diagnostics[0].severity, Some(DiagnosticSeverity::WARNING));
    }

    #[test]
    fn generated_call_with_missing_snapshot_evidence_reports_diagnostic() {
        let workspace = create_source_map_workspace_without_snapshot("example.Client.findById");
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "QClient"))
            .expect("hover should fail closed without an error");
        assert!(hover.is_none());

        let diagnostics = state.document_diagnostics(&uri);
        assert_eq!(diagnostics.len(), 1);
        assert_eq!(
            diagnostics[0].message,
            "Mortar SQL snapshot was not found: example.Client.findById"
        );
    }

    #[test]
    fn generated_call_with_invalid_snapshot_file_reports_diagnostic() {
        let workspace = create_source_map_workspace_with_invalid_snapshot();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "QClient");

        assert!(
            state
                .hover(&uri, position)
                .expect("hover should fail closed without an error")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("code actions should fail closed without an error")
                .is_empty()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("definition should fail closed without an error")
                .is_none()
        );

        let diagnostics = state.document_diagnostics(&uri);
        assert_eq!(diagnostics.len(), 1);
        assert!(
            diagnostics[0]
                .message
                .starts_with("Mortar SQL snapshot file is invalid:")
        );
    }

    #[test]
    fn generated_call_positions_use_lsp_utf16_characters() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        String label = "café"; QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "findById"))
            .expect("hover should resolve")
            .expect("UTF-16 position should resolve generated call");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
        ));
    }

    #[test]
    fn generated_call_prefers_build_metadata_over_root_fixture_metadata() {
        let workspace = create_stale_source_map_workspace();
        let build_metadata_dir = workspace
            .join("build")
            .join("classes")
            .join("java")
            .join("main")
            .join("META-INF")
            .join("mortar");
        std::fs::create_dir_all(&build_metadata_dir)
            .expect("build metadata directory should be created");
        std::fs::write(
            build_metadata_dir.join("entities.json"),
            include_str!(
                "../../mortar-compiler/test-fixtures/source-map-contract/r18/entities.json"
            ),
        )
        .expect("build metadata should be written");
        std::fs::write(
            build_metadata_dir.join("source-map.json"),
            include_str!(
                "../../mortar-compiler/test-fixtures/source-map-contract/r18/source-map.json"
            ),
        )
        .expect("build source map should be written");

        let uri = file_uri_for_path(&workspace.join("AccountUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class AccountUsage {
    void read(Object renderer, Long id) {
        QAccount.ACCOUNT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "findById"))
            .expect("hover should resolve")
            .expect("build metadata should shadow stale root metadata");

        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select a.id from accounts a where a.id = ?")
        ));
    }

    #[test]
    fn generated_call_with_duplicate_fresh_source_map_entries_fails_closed() {
        let workspace = create_source_map_workspace_with(&duplicate_client_find_by_id_source_map());
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    // mortar:snapshot=example.Client.findById
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "QClient"))
            .expect("hover should fail closed without an error");

        assert!(hover.is_none());
    }

    #[test]
    fn ordinary_read_find_by_id_chain_is_not_a_generated_call() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(ExternalReader reader, Object renderer) {
        reader.read(renderer).findById(7L);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "findById"))
            .expect("ordinary read chain should not error");

        assert!(hover.is_none());
        assert!(state.document_diagnostics(&uri).is_empty());
    }

    #[test]
    fn generated_call_without_resolvable_metamodel_context_fails_closed() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        state.open_document(
            &uri,
            r#"package example;

public final class ClientUsage {
    // mortar:snapshot=example.Client.findById
    void read(Object renderer, Long id) {
        CLIENT.read(renderer).findById(id);
    }
}
"#
            .to_string(),
        );

        let hover = state
            .hover(
                &uri,
                Position {
                    line: 5,
                    character: 32,
                },
            )
            .expect("hover should fail closed without an error");

        assert!(hover.is_none());
        assert!(
            state
                .code_actions(
                    &uri,
                    Position {
                        line: 5,
                        character: 32,
                    },
                )
                .expect("code actions should fail closed without an error")
                .is_empty()
        );
        assert!(
            state
                .definition(
                    &uri,
                    Position {
                        line: 5,
                        character: 32,
                    },
                )
                .expect("definition should fail closed without an error")
                .is_none()
        );
    }

    #[test]
    fn non_canonical_read_member_shape_fails_closed() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        state.open_document(
            &uri,
            r#"package example;

public final class ClientUsage {
    // mortar:snapshot=example.Client.findById
    void read(QClient client, Object renderer) {
        client.read.findById(renderer);
    }
}
"#
            .to_string(),
        );

        let hover = state
            .hover(
                &uri,
                Position {
                    line: 5,
                    character: 21,
                },
            )
            .expect("hover should fail closed without an error");

        assert!(hover.is_none());
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

    fn create_source_map_workspace() -> PathBuf {
        create_source_map_workspace_with(include_str!(
            "../../mortar-compiler/test-fixtures/source-map-contract/r18/source-map.json"
        ))
    }

    fn create_stale_source_map_workspace() -> PathBuf {
        let stale_source_map = include_str!(
            "../../mortar-compiler/test-fixtures/source-map-contract/r18/source-map.json"
        )
        .replace(
            "sha256:7196ccb7d298233e37f9f987263049279439572923730d97ba4315b24df36b84",
            "sha256:stale",
        );
        create_source_map_workspace_with(&stale_source_map)
    }

    fn create_source_map_workspace_without_snapshot(snapshot_name: &str) -> PathBuf {
        let workspace = create_source_map_workspace();
        let snapshot_path = workspace.join("mortar.sql.snap.json");
        let snapshot_content =
            std::fs::read_to_string(&snapshot_path).expect("snapshot file should be readable");
        let mut snapshot_file = mortar_compiler::parse_sql_snapshot_file(&snapshot_content)
            .expect("snapshot file should parse");
        snapshot_file
            .snapshots
            .retain(|snapshot| snapshot.name != snapshot_name);
        let rendered = mortar_compiler::render_sql_snapshot_file(&snapshot_file)
            .expect("snapshot file should render");
        std::fs::write(snapshot_path, rendered).expect("snapshot file should be rewritten");
        workspace
    }

    fn create_source_map_workspace_with_invalid_snapshot() -> PathBuf {
        let workspace = create_source_map_workspace();
        std::fs::write(workspace.join("mortar.sql.snap.json"), "{ invalid")
            .expect("invalid snapshot file should be written");
        workspace
    }

    fn create_source_map_workspace_with(source_map: &str) -> PathBuf {
        let id = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .expect("time should be monotonic")
            .as_nanos();
        let workspace = std::env::temp_dir().join(format!("mortar-lsp-source-map-test-{id}"));
        let metadata_dir = workspace.join("META-INF").join("mortar");
        std::fs::create_dir_all(&metadata_dir).expect("metadata directory should be created");
        std::fs::write(
            metadata_dir.join("entities.json"),
            include_str!(
                "../../mortar-compiler/test-fixtures/source-map-contract/r18/entities.json"
            ),
        )
        .expect("metadata file should be written");
        std::fs::write(metadata_dir.join("source-map.json"), source_map)
            .expect("source map file should be written");
        std::fs::write(
            workspace.join("mortar.sql.snap.json"),
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
"#,
        )
        .expect("snapshot file should be written");
        workspace
    }

    fn duplicate_client_find_by_id_source_map() -> String {
        let mut source_map = mortar_compiler::parse_mortar_source_map_file(include_str!(
            "../../mortar-compiler/test-fixtures/source-map-contract/r18/source-map.json"
        ))
        .expect("source-map fixture should parse");
        let duplicate = source_map
            .queries
            .iter()
            .find(|query| query.id == "example.Client.findById")
            .expect("client findById query should exist")
            .clone();
        source_map.queries.push(duplicate);
        serde_json::to_string_pretty(&source_map).expect("source-map should serialize")
    }

    fn snapshot_line(workspace: &Path, snapshot_name: &str) -> u32 {
        let snapshot_content = std::fs::read_to_string(workspace.join("mortar.sql.snap.json"))
            .expect("snapshot file should be readable");
        snapshot_content
            .lines()
            .position(|line| line.contains(&format!("\"name\": \"{snapshot_name}\"")))
            .and_then(|line| u32::try_from(line).ok())
            .expect("snapshot name should exist")
    }

    fn position_of(document: &str, needle: &str) -> Position {
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

    fn file_uri_for_path(path: &Path) -> Uri {
        let normalized = path.to_string_lossy().replace('\\', "/");
        format!("file:///{normalized}")
            .parse()
            .expect("path URI should parse")
    }
}
