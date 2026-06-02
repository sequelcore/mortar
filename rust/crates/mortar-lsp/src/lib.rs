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
use tree_sitter::{Node, Parser};

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
            GeneratedQueryResolution::FailClosed { .. } => return Ok(None),
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
            GeneratedQueryResolution::FailClosed { .. } => return Ok(Vec::new()),
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
            GeneratedCallDetection::FailClosed { reason, .. } => {
                return Ok(GeneratedQueryResolution::FailClosed { reason });
            }
            GeneratedCallDetection::NotGeneratedCall => {
                return Ok(GeneratedQueryResolution::NotGeneratedCall);
            }
        };
        let Some((metadata_content, source_map_content)) = self.read_source_map_inputs(uri)? else {
            return Ok(GeneratedQueryResolution::FailClosed {
                reason: GeneratedCallFailClosedReason::SourceMapEvidence,
            });
        };

        let metadata = match parse_mortar_metadata_file(&metadata_content) {
            Ok(metadata) => metadata,
            Err(_) => {
                return Ok(GeneratedQueryResolution::FailClosed {
                    reason: GeneratedCallFailClosedReason::SourceMapEvidence,
                });
            }
        };
        let source_map = match parse_mortar_source_map_file(&source_map_content) {
            Ok(source_map) => source_map,
            Err(_) => {
                return Ok(GeneratedQueryResolution::FailClosed {
                    reason: GeneratedCallFailClosedReason::SourceMapEvidence,
                });
            }
        };
        if !detect_source_map_freshness(&metadata, &source_map).is_empty() {
            return Ok(GeneratedQueryResolution::FailClosed {
                reason: GeneratedCallFailClosedReason::SourceMapEvidence,
            });
        }

        let mut candidates = source_map
            .queries
            .into_iter()
            .filter(|query| generated_call.matches(query))
            .collect::<Vec<_>>();
        if candidates.len() != 1 {
            return Ok(GeneratedQueryResolution::FailClosed {
                reason: GeneratedCallFailClosedReason::SourceMapEvidence,
            });
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
                    .unwrap_or(GeneratedQueryResolution::FailClosed {
                        reason: GeneratedCallFailClosedReason::SourceMapEvidence,
                    }) {
                    GeneratedQueryResolution::Snapshot(snapshot_name) => {
                        self.snapshot_exists(uri, &snapshot_name).map_or_else(
                            |error| {
                                Some(marker.diagnostic(format!(
                                    "Mortar SQL snapshot file is invalid: {error}"
                                )))
                            },
                            |exists| {
                                (!exists).then(|| {
                                    marker.diagnostic_with_code(
                                        "mortar-snapshot-missing",
                                        format!(
                                            "Mortar SQL snapshot was not found: {snapshot_name}"
                                        ),
                                    )
                                })
                            },
                        )
                    }
                    GeneratedQueryResolution::FailClosed { reason } => {
                        Some(marker.diagnostic_for_fail_closed(reason))
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
    FailClosed {
        reason: GeneratedCallFailClosedReason,
    },
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
    FailClosed {
        start: usize,
        end: usize,
        reason: GeneratedCallFailClosedReason,
    },
    NotGeneratedCall,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum GeneratedCallFailClosedReason {
    SourceMapEvidence,
    UnsupportedAliasShape,
    AmbiguousAlias,
    ReassignedAlias,
    MalformedJavaBuffer,
}

impl GeneratedCallFailClosedReason {
    fn diagnostic_code(self) -> &'static str {
        match self {
            GeneratedCallFailClosedReason::SourceMapEvidence => "mortar-source-map-stale",
            GeneratedCallFailClosedReason::UnsupportedAliasShape => "mortar-alias-unsupported",
            GeneratedCallFailClosedReason::AmbiguousAlias => "mortar-alias-ambiguous",
            GeneratedCallFailClosedReason::ReassignedAlias => "mortar-alias-reassigned",
            GeneratedCallFailClosedReason::MalformedJavaBuffer => "mortar-java-buffer-malformed",
        }
    }

    fn diagnostic_message(self) -> &'static str {
        match self {
            GeneratedCallFailClosedReason::SourceMapEvidence => {
                "Mortar source-map metadata is stale or missing"
            }
            GeneratedCallFailClosedReason::UnsupportedAliasShape => {
                "Mortar generated call uses unsupported alias syntax"
            }
            GeneratedCallFailClosedReason::AmbiguousAlias => "Mortar generated alias is ambiguous",
            GeneratedCallFailClosedReason::ReassignedAlias => {
                "Mortar generated alias is reassigned"
            }
            GeneratedCallFailClosedReason::MalformedJavaBuffer => {
                "Mortar generated call is inside a malformed Java buffer"
            }
        }
    }
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

    let Some(syntax) = JavaSyntaxResolution::parse(document) else {
        return GeneratedCallDetection::NotGeneratedCall;
    };
    syntax.generated_call_at(offset)
}

fn generated_call_markers(document: &str) -> Vec<GeneratedCallMarker> {
    JavaSyntaxResolution::parse(document)
        .map(|syntax| syntax.generated_call_markers())
        .unwrap_or_default()
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum SyntaxGeneratedCall {
    Call {
        call: GeneratedCall,
        method_start: usize,
    },
    FailClosed {
        start: usize,
        end: usize,
        method_start: usize,
        member: String,
        reason: GeneratedCallFailClosedReason,
    },
}

impl SyntaxGeneratedCall {
    fn contains(&self, offset: usize) -> bool {
        match self {
            SyntaxGeneratedCall::Call { call, .. } => offset >= call.start && offset <= call.end,
            SyntaxGeneratedCall::FailClosed { start, end, .. } => {
                offset >= *start && offset <= *end
            }
        }
    }

    fn detection(&self) -> GeneratedCallDetection {
        match self {
            SyntaxGeneratedCall::Call { call, .. } => GeneratedCallDetection::Call(call.clone()),
            SyntaxGeneratedCall::FailClosed {
                start, end, reason, ..
            } => GeneratedCallDetection::FailClosed {
                start: *start,
                end: *end,
                reason: *reason,
            },
        }
    }

    fn marker(&self, document: &str) -> Option<GeneratedCallMarker> {
        let (method_start, member) = match self {
            SyntaxGeneratedCall::Call { call, method_start } => {
                (*method_start, call.generated_member.clone())
            }
            SyntaxGeneratedCall::FailClosed {
                method_start,
                member,
                ..
            } => (*method_start, member.clone()),
        };
        position_for_offset(document, method_start).map(|position| GeneratedCallMarker {
            line: position.line,
            character: position.character,
            member,
        })
    }

    fn fail_closed_with_reason(&self, reason: GeneratedCallFailClosedReason) -> Self {
        match self {
            SyntaxGeneratedCall::Call { call, method_start } => SyntaxGeneratedCall::FailClosed {
                start: call.start,
                end: call.end,
                method_start: *method_start,
                member: call.generated_member.clone(),
                reason,
            },
            SyntaxGeneratedCall::FailClosed {
                start,
                end,
                method_start,
                member,
                ..
            } => SyntaxGeneratedCall::FailClosed {
                start: *start,
                end: *end,
                method_start: *method_start,
                member: member.clone(),
                reason,
            },
        }
    }
}

#[derive(Debug, Clone)]
struct JavaSyntaxResolution<'a> {
    document: &'a str,
    calls: Vec<SyntaxGeneratedCall>,
}

impl<'a> JavaSyntaxResolution<'a> {
    fn parse(document: &'a str) -> Option<Self> {
        let mut parser = Parser::new();
        let language = tree_sitter_java::LANGUAGE;
        parser.set_language(&language.into()).ok()?;
        let tree = parser.parse(document, None)?;
        let root = tree.root_node();
        let has_syntax_error = root.has_error();
        let imports = JavaImports::parse_syntax(document, root);
        let mut calls = Vec::new();
        collect_generated_method_invocations(document, root, &imports, &mut calls);
        if has_syntax_error {
            calls = calls
                .into_iter()
                .map(|call| {
                    call.fail_closed_with_reason(GeneratedCallFailClosedReason::MalformedJavaBuffer)
                })
                .collect();
        }
        Some(Self { document, calls })
    }

    fn generated_call_at(&self, offset: usize) -> GeneratedCallDetection {
        self.calls
            .iter()
            .find(|call| call.contains(offset))
            .map(SyntaxGeneratedCall::detection)
            .unwrap_or(GeneratedCallDetection::NotGeneratedCall)
    }

    fn generated_call_markers(&self) -> Vec<GeneratedCallMarker> {
        self.calls
            .iter()
            .filter_map(|call| call.marker(self.document))
            .collect()
    }
}

fn collect_generated_method_invocations(
    document: &str,
    node: Node<'_>,
    imports: &JavaImports,
    calls: &mut Vec<SyntaxGeneratedCall>,
) {
    if node.kind() == "method_invocation"
        && let Some(call) = syntax_generated_call_for_method_invocation(document, node, imports)
    {
        calls.push(call);
    }

    let mut cursor = node.walk();
    for child in node.children(&mut cursor) {
        collect_generated_method_invocations(document, child, imports, calls);
    }
}

fn syntax_generated_call_for_method_invocation(
    document: &str,
    node: Node<'_>,
    imports: &JavaImports,
) -> Option<SyntaxGeneratedCall> {
    let method_name = node.child_by_field_name("name")?;
    let method = node_text(document, method_name);
    if !matches!(method, "findById" | "findAll") {
        return None;
    }
    let member = format!("read.{method}");
    let object = unwrapped_expression_node(node.child_by_field_name("object")?);
    let method_start = method_name.start_byte();

    if object.kind() != "method_invocation" {
        return generated_call_from_read_namespace_alias(
            document,
            object,
            node,
            imports,
            method_start,
            member.clone(),
        )
        .or_else(|| {
            generated_looking_syntax_fail_closed(document, object, node, method_start, member)
        });
    }

    let Some(read_name) = object.child_by_field_name("name") else {
        return Some(syntax_fail_closed(
            object.start_byte(),
            node.end_byte(),
            method_start,
            member,
            GeneratedCallFailClosedReason::UnsupportedAliasShape,
        ));
    };
    if node_text(document, read_name) != "read" {
        return generated_looking_syntax_fail_closed(document, object, node, method_start, member);
    }

    let Some(receiver_node) = object.child_by_field_name("object") else {
        return Some(syntax_fail_closed(
            object.start_byte(),
            node.end_byte(),
            method_start,
            member,
            GeneratedCallFailClosedReason::UnsupportedAliasShape,
        ));
    };
    let receiver_node = unwrapped_expression_node(receiver_node);
    let receiver = receiver_chain_from_syntax(document, receiver_node);
    let fail_closed = syntax_fail_closed(
        receiver
            .as_ref()
            .map(|receiver| receiver.start)
            .unwrap_or_else(|| receiver_node.start_byte()),
        node.end_byte(),
        method_start,
        member.clone(),
        GeneratedCallFailClosedReason::UnsupportedAliasShape,
    );
    let Some(receiver) = receiver else {
        return Some(fail_closed);
    };
    if receiver.parts.is_empty() {
        return Some(fail_closed);
    }

    let generated_entity_type = match generated_entity_type_for_receiver(&receiver.parts, imports)
        .into_option()
        .or_else(|| {
            generated_entity_type_for_local_metamodel_alias(
                document,
                node,
                &receiver.parts,
                imports,
            )
        }) {
        Some(generated_entity_type) => generated_entity_type,
        None => {
            return match local_alias_fail_closed_reason(document, node, &receiver.parts, imports) {
                Some(reason) => Some(fail_closed_with_reason(fail_closed, reason)),
                None if receiver_is_generated_like(&receiver.parts)
                    || receiver_is_generated_like_local_alias(document, node, &receiver.parts) =>
                {
                    Some(fail_closed)
                }
                None => None,
            };
        }
    };
    let generated_read_namespace = format!("{generated_entity_type}.Read");

    Some(SyntaxGeneratedCall::Call {
        call: GeneratedCall {
            generated_entity_type,
            generated_read_namespace,
            generated_member: member,
            start: receiver.start,
            end: node.end_byte(),
        },
        method_start,
    })
}

fn generated_call_from_read_namespace_alias(
    document: &str,
    object: Node<'_>,
    node: Node<'_>,
    imports: &JavaImports,
    method_start: usize,
    member: String,
) -> Option<SyntaxGeneratedCall> {
    let object = unwrapped_expression_node(object);
    if object.kind() != "identifier" {
        return None;
    }

    let alias_name = node_text(document, object);
    let fail_closed = syntax_fail_closed(
        object.start_byte(),
        node.end_byte(),
        method_start,
        member.clone(),
        GeneratedCallFailClosedReason::UnsupportedAliasShape,
    );
    match local_generated_binding_before_call(document, node, alias_name, imports) {
        LocalGeneratedBindingResolution::Resolved(LocalGeneratedBinding::ReadNamespace {
            generated_entity_type,
        }) => {
            let generated_read_namespace = format!("{generated_entity_type}.Read");
            Some(SyntaxGeneratedCall::Call {
                call: GeneratedCall {
                    generated_entity_type,
                    generated_read_namespace,
                    generated_member: member,
                    start: object.start_byte(),
                    end: node.end_byte(),
                },
                method_start,
            })
        }
        LocalGeneratedBindingResolution::Resolved(LocalGeneratedBinding::Metamodel { .. }) => {
            Some(fail_closed)
        }
        LocalGeneratedBindingResolution::Unsupported => Some(fail_closed),
        LocalGeneratedBindingResolution::Ambiguous => Some(fail_closed_with_reason(
            fail_closed,
            GeneratedCallFailClosedReason::AmbiguousAlias,
        )),
        LocalGeneratedBindingResolution::Reassigned => Some(fail_closed_with_reason(
            fail_closed,
            GeneratedCallFailClosedReason::ReassignedAlias,
        )),
        LocalGeneratedBindingResolution::Unresolved => None,
    }
}

fn generated_looking_syntax_fail_closed(
    document: &str,
    object: Node<'_>,
    node: Node<'_>,
    method_start: usize,
    member: String,
) -> Option<SyntaxGeneratedCall> {
    let object = unwrapped_expression_node(object);
    (object_is_generated_like_local_alias(document, node, object)
        || object_is_generated_read_field(document, node, object))
    .then(|| {
        syntax_fail_closed(
            object.start_byte(),
            node.end_byte(),
            method_start,
            member,
            GeneratedCallFailClosedReason::UnsupportedAliasShape,
        )
    })
}

fn syntax_fail_closed(
    start: usize,
    end: usize,
    method_start: usize,
    member: String,
    reason: GeneratedCallFailClosedReason,
) -> SyntaxGeneratedCall {
    SyntaxGeneratedCall::FailClosed {
        start,
        end,
        method_start,
        member,
        reason,
    }
}

fn fail_closed_with_reason(
    call: SyntaxGeneratedCall,
    reason: GeneratedCallFailClosedReason,
) -> SyntaxGeneratedCall {
    call.fail_closed_with_reason(reason)
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum LocalGeneratedBinding {
    Metamodel { generated_entity_type: String },
    ReadNamespace { generated_entity_type: String },
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum LocalGeneratedBindingResolution {
    Unresolved,
    Resolved(LocalGeneratedBinding),
    Unsupported,
    Ambiguous,
    Reassigned,
}

#[derive(Debug, Default)]
struct LocalGeneratedBindingState {
    binding: Option<LocalGeneratedBinding>,
    shadowed: bool,
    unsupported: bool,
    ambiguous: bool,
    reassigned: bool,
}

fn generated_entity_type_for_local_metamodel_alias(
    document: &str,
    call_node: Node<'_>,
    receiver: &[String],
    imports: &JavaImports,
) -> Option<String> {
    let [name] = receiver else {
        return None;
    };
    match local_generated_binding_before_call(document, call_node, name, imports) {
        LocalGeneratedBindingResolution::Resolved(LocalGeneratedBinding::Metamodel {
            generated_entity_type,
        }) => Some(generated_entity_type),
        _ => None,
    }
}

fn local_alias_fail_closed_reason(
    document: &str,
    call_node: Node<'_>,
    receiver: &[String],
    imports: &JavaImports,
) -> Option<GeneratedCallFailClosedReason> {
    let [name] = receiver else {
        return None;
    };
    match local_generated_binding_before_call(document, call_node, name, imports) {
        LocalGeneratedBindingResolution::Unsupported => {
            Some(GeneratedCallFailClosedReason::UnsupportedAliasShape)
        }
        LocalGeneratedBindingResolution::Ambiguous => {
            Some(GeneratedCallFailClosedReason::AmbiguousAlias)
        }
        LocalGeneratedBindingResolution::Reassigned => {
            Some(GeneratedCallFailClosedReason::ReassignedAlias)
        }
        LocalGeneratedBindingResolution::Resolved(LocalGeneratedBinding::ReadNamespace {
            ..
        }) => Some(GeneratedCallFailClosedReason::UnsupportedAliasShape),
        LocalGeneratedBindingResolution::Resolved(LocalGeneratedBinding::Metamodel { .. })
        | LocalGeneratedBindingResolution::Unresolved => None,
    }
}

fn local_generated_binding_before_call(
    document: &str,
    call_node: Node<'_>,
    name: &str,
    imports: &JavaImports,
) -> LocalGeneratedBindingResolution {
    if local_alias_success_is_disallowed(call_node) {
        return if local_name_is_generated_like_before_call(document, call_node, name) {
            LocalGeneratedBindingResolution::Unsupported
        } else {
            LocalGeneratedBindingResolution::Unresolved
        };
    }
    let Some(scope) = nearest_local_resolution_scope(call_node) else {
        return LocalGeneratedBindingResolution::Unresolved;
    };
    let mut state = LocalGeneratedBindingState::default();
    collect_local_generated_binding_before_call(
        document,
        scope,
        call_node.start_byte(),
        name,
        imports,
        &mut state,
    );
    if state.reassigned {
        LocalGeneratedBindingResolution::Reassigned
    } else if state.ambiguous {
        LocalGeneratedBindingResolution::Ambiguous
    } else if state.unsupported {
        LocalGeneratedBindingResolution::Unsupported
    } else if state.shadowed {
        LocalGeneratedBindingResolution::Unresolved
    } else if let Some(binding) = state.binding {
        LocalGeneratedBindingResolution::Resolved(binding)
    } else {
        LocalGeneratedBindingResolution::Unresolved
    }
}

fn collect_local_generated_binding_before_call(
    document: &str,
    node: Node<'_>,
    call_start: usize,
    target_name: &str,
    imports: &JavaImports,
    state: &mut LocalGeneratedBindingState,
) {
    if node.start_byte() >= call_start {
        return;
    }

    match node.kind() {
        "variable_declarator" => {
            let Some(name) = node.child_by_field_name("name") else {
                return;
            };
            if node_text(document, name) == target_name {
                let Some(value) = node.child_by_field_name("value") else {
                    state.unsupported = true;
                    return;
                };
                match local_generated_binding_from_initializer(document, value, imports) {
                    LocalGeneratedBindingResolution::Resolved(binding) => {
                        if state.binding.is_some() {
                            state.ambiguous = true;
                        } else {
                            state.binding = Some(binding);
                        }
                    }
                    LocalGeneratedBindingResolution::Ambiguous => state.ambiguous = true,
                    LocalGeneratedBindingResolution::Unsupported => state.unsupported = true,
                    LocalGeneratedBindingResolution::Unresolved => {
                        state.binding = None;
                        state.shadowed = true;
                    }
                    LocalGeneratedBindingResolution::Reassigned => {}
                }
            }
        }
        "assignment_expression" => {
            if assignment_targets_name(document, node, target_name) {
                state.reassigned = true;
            }
        }
        _ => {}
    }

    let mut cursor = node.walk();
    for child in node.children(&mut cursor) {
        if is_local_resolution_boundary(child) && !node_contains_offset(child, call_start) {
            continue;
        }
        collect_local_generated_binding_before_call(
            document,
            child,
            call_start,
            target_name,
            imports,
            state,
        );
    }
}

fn local_generated_binding_from_initializer(
    document: &str,
    value: Node<'_>,
    imports: &JavaImports,
) -> LocalGeneratedBindingResolution {
    match value.kind() {
        "field_access" => receiver_chain_from_syntax(document, value)
            .map(
                |receiver| match generated_entity_type_for_receiver(&receiver.parts, imports) {
                    GeneratedEntityTypeResolution::Resolved(generated_entity_type) => {
                        LocalGeneratedBindingResolution::Resolved(
                            LocalGeneratedBinding::Metamodel {
                                generated_entity_type,
                            },
                        )
                    }
                    GeneratedEntityTypeResolution::Ambiguous => {
                        LocalGeneratedBindingResolution::Ambiguous
                    }
                    GeneratedEntityTypeResolution::Unresolved => {
                        LocalGeneratedBindingResolution::Unresolved
                    }
                },
            )
            .unwrap_or(LocalGeneratedBindingResolution::Unresolved),
        "method_invocation" => {
            local_generated_binding_from_method_initializer(document, value, imports)
        }
        "ternary_expression" => {
            if initializer_contains_direct_generated_binding(document, value, imports) {
                LocalGeneratedBindingResolution::Ambiguous
            } else {
                LocalGeneratedBindingResolution::Unresolved
            }
        }
        _ => {
            if expression_is_generated_like(document, value) {
                LocalGeneratedBindingResolution::Unsupported
            } else {
                LocalGeneratedBindingResolution::Unresolved
            }
        }
    }
}

fn local_generated_read_binding_from_initializer(
    document: &str,
    value: Node<'_>,
    imports: &JavaImports,
) -> LocalGeneratedBindingResolution {
    let Some(name) = value.child_by_field_name("name") else {
        return LocalGeneratedBindingResolution::Unresolved;
    };
    if node_text(document, name) != "read" {
        return LocalGeneratedBindingResolution::Unresolved;
    }
    let Some(object) = value.child_by_field_name("object") else {
        return LocalGeneratedBindingResolution::Unsupported;
    };
    let Some(receiver) = receiver_chain_from_syntax(document, object) else {
        return LocalGeneratedBindingResolution::Unsupported;
    };
    match generated_entity_type_for_receiver(&receiver.parts, imports) {
        GeneratedEntityTypeResolution::Resolved(generated_entity_type) => {
            LocalGeneratedBindingResolution::Resolved(LocalGeneratedBinding::ReadNamespace {
                generated_entity_type,
            })
        }
        GeneratedEntityTypeResolution::Ambiguous => LocalGeneratedBindingResolution::Ambiguous,
        GeneratedEntityTypeResolution::Unresolved => LocalGeneratedBindingResolution::Unsupported,
    }
}

fn local_generated_binding_from_method_initializer(
    document: &str,
    value: Node<'_>,
    imports: &JavaImports,
) -> LocalGeneratedBindingResolution {
    let Some(name) = value.child_by_field_name("name") else {
        return LocalGeneratedBindingResolution::Unsupported;
    };
    if node_text(document, name) == "read" {
        local_generated_read_binding_from_initializer(document, value, imports)
    } else {
        LocalGeneratedBindingResolution::Unsupported
    }
}

fn initializer_contains_direct_generated_binding(
    document: &str,
    node: Node<'_>,
    imports: &JavaImports,
) -> bool {
    match node.kind() {
        "field_access" => receiver_chain_from_syntax(document, node).is_some_and(|receiver| {
            matches!(
                generated_entity_type_for_receiver(&receiver.parts, imports),
                GeneratedEntityTypeResolution::Resolved(_)
            )
        }),
        "method_invocation" => matches!(
            local_generated_read_binding_from_initializer(document, node, imports),
            LocalGeneratedBindingResolution::Resolved(_)
        ),
        _ => {
            let mut cursor = node.walk();
            node.children(&mut cursor).any(|child| {
                initializer_contains_direct_generated_binding(document, child, imports)
            })
        }
    }
}

fn assignment_targets_name(document: &str, node: Node<'_>, target_name: &str) -> bool {
    node.child_by_field_name("left")
        .is_some_and(|left| left.kind() == "identifier" && node_text(document, left) == target_name)
}

fn local_alias_success_is_disallowed(node: Node<'_>) -> bool {
    let mut current = node;
    while let Some(parent) = current.parent() {
        if matches!(
            parent.kind(),
            "lambda_expression" | "catch_clause" | "switch_expression" | "switch_statement"
        ) {
            return true;
        }
        if matches!(
            parent.kind(),
            "method_declaration" | "constructor_declaration" | "compact_constructor_declaration"
        ) {
            return false;
        }
        current = parent;
    }
    false
}

fn receiver_chain_from_syntax(document: &str, node: Node<'_>) -> Option<ReceiverChain> {
    match node.kind() {
        "identifier" => Some(ReceiverChain {
            parts: vec![node_text(document, node).to_string()],
            start: node.start_byte(),
        }),
        "field_access" => {
            let object = node.child_by_field_name("object")?;
            let field = node.child_by_field_name("field")?;
            let mut receiver = receiver_chain_from_syntax(document, object)?;
            receiver.parts.push(node_text(document, field).to_string());
            Some(receiver)
        }
        _ => None,
    }
}

fn node_text<'a>(document: &'a str, node: Node<'_>) -> &'a str {
    &document[node.start_byte()..node.end_byte()]
}

fn expression_is_generated_like(document: &str, node: Node<'_>) -> bool {
    let node = unwrapped_expression_node(node);
    match node.kind() {
        "field_access" => receiver_chain_from_syntax(document, node)
            .is_some_and(|receiver| receiver_is_generated_like(&receiver.parts)),
        "method_invocation" => {
            node.child_by_field_name("name")
                .is_some_and(|name| node_text(document, name) == "read")
                && node
                    .child_by_field_name("object")
                    .and_then(|object| {
                        receiver_chain_from_syntax(document, unwrapped_expression_node(object))
                    })
                    .is_some_and(|receiver| receiver_is_generated_like(&receiver.parts))
        }
        _ => false,
    }
}

fn unwrapped_expression_node(mut node: Node<'_>) -> Node<'_> {
    loop {
        match node.kind() {
            "parenthesized_expression" => {
                let Some(child) = first_named_child(node) else {
                    return node;
                };
                node = child;
            }
            "cast_expression" => {
                let Some(child) = node.child_by_field_name("value") else {
                    return node;
                };
                node = child;
            }
            _ => return node,
        }
    }
}

fn first_named_child(node: Node<'_>) -> Option<Node<'_>> {
    let mut cursor = node.walk();
    node.named_children(&mut cursor).next()
}

fn receiver_is_generated_like_local_alias(
    document: &str,
    call_node: Node<'_>,
    receiver: &[String],
) -> bool {
    matches!(
        receiver,
        [name] if local_name_is_generated_like_before_call(document, call_node, name)
    )
}

fn object_is_generated_like_local_alias(
    document: &str,
    call_node: Node<'_>,
    object: Node<'_>,
) -> bool {
    object.kind() == "identifier"
        && local_name_is_generated_like_before_call(
            document,
            call_node,
            node_text(document, object),
        )
}

fn object_is_generated_read_field(document: &str, call_node: Node<'_>, object: Node<'_>) -> bool {
    if object.kind() != "field_access" {
        return false;
    }
    let Some(field) = object.child_by_field_name("field") else {
        return false;
    };
    if node_text(document, field) != "read" {
        return false;
    }
    let Some(receiver_node) = object.child_by_field_name("object") else {
        return false;
    };
    receiver_chain_from_syntax(document, unwrapped_expression_node(receiver_node)).is_some_and(
        |receiver| {
            receiver_is_generated_like(&receiver.parts)
                || receiver_is_generated_like_local_alias(document, call_node, &receiver.parts)
        },
    )
}

fn local_name_is_generated_like_before_call(
    document: &str,
    call_node: Node<'_>,
    name: &str,
) -> bool {
    let Some(scope) = nearest_local_resolution_scope(call_node) else {
        return false;
    };
    let mut status = LocalGeneratedLikeStatus::Unknown;
    collect_local_name_status_before_call(
        document,
        scope,
        call_node.start_byte(),
        name,
        &mut status,
    );
    status == LocalGeneratedLikeStatus::GeneratedLike
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum LocalGeneratedLikeStatus {
    Unknown,
    Ordinary,
    GeneratedLike,
}

fn collect_local_name_status_before_call(
    document: &str,
    node: Node<'_>,
    call_start: usize,
    target_name: &str,
    status: &mut LocalGeneratedLikeStatus,
) {
    if node.start_byte() >= call_start {
        return;
    }

    match node.kind() {
        "formal_parameter" => {
            let Some(name) = node.child_by_field_name("name") else {
                return;
            };
            if node_text(document, name) == target_name
                && declaration_type_is_generated_like(document, node)
            {
                *status = LocalGeneratedLikeStatus::GeneratedLike;
            }
        }
        "variable_declarator" => {
            let Some(name) = node.child_by_field_name("name") else {
                return;
            };
            let Some(value) = node.child_by_field_name("value") else {
                return;
            };
            if node_text(document, name) == target_name {
                *status = if expression_is_generated_like(document, value)
                    || declaration_type_is_generated_like(document, node)
                {
                    LocalGeneratedLikeStatus::GeneratedLike
                } else {
                    LocalGeneratedLikeStatus::Ordinary
                };
            }
        }
        "assignment_expression" => {
            let Some(left) = node.child_by_field_name("left") else {
                return;
            };
            let Some(right) = node.child_by_field_name("right") else {
                return;
            };
            if left.kind() == "identifier" && node_text(document, left) == target_name {
                *status = if expression_is_generated_like(document, right) {
                    LocalGeneratedLikeStatus::GeneratedLike
                } else {
                    LocalGeneratedLikeStatus::Ordinary
                };
            }
        }
        _ => {}
    }

    let mut cursor = node.walk();
    for child in node.children(&mut cursor) {
        if is_local_resolution_boundary(child) && !node_contains_offset(child, call_start) {
            continue;
        }
        collect_local_name_status_before_call(document, child, call_start, target_name, status);
    }
}

fn is_local_resolution_boundary(node: Node<'_>) -> bool {
    matches!(
        node.kind(),
        "block"
            | "lambda_expression"
            | "if_statement"
            | "while_statement"
            | "do_statement"
            | "for_statement"
            | "enhanced_for_statement"
            | "switch_expression"
            | "switch_statement"
            | "try_statement"
            | "catch_clause"
            | "synchronized_statement"
    )
}

fn node_contains_offset(node: Node<'_>, offset: usize) -> bool {
    node.start_byte() <= offset && offset < node.end_byte()
}

fn nearest_local_resolution_scope(node: Node<'_>) -> Option<Node<'_>> {
    let mut current = node;
    while let Some(parent) = current.parent() {
        match parent.kind() {
            "method_declaration"
            | "constructor_declaration"
            | "compact_constructor_declaration" => {
                return Some(parent);
            }
            _ => current = parent,
        }
    }
    None
}

fn declaration_type_is_generated_like(document: &str, node: Node<'_>) -> bool {
    node.child_by_field_name("type")
        .or_else(|| {
            node.parent()
                .and_then(|parent| parent.child_by_field_name("type"))
        })
        .is_some_and(|node| java_type_is_generated_like(node_text(document, node)))
}

fn java_type_is_generated_like(type_text: &str) -> bool {
    let without_generics = type_text.split('<').next().unwrap_or(type_text);
    let simple = simple_java_name(without_generics.trim())
        .trim_end_matches("[]")
        .trim();
    simple.starts_with('Q')
        && simple
            .chars()
            .nth(1)
            .is_some_and(|character| character.is_ascii_uppercase())
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

#[derive(Debug, Clone, PartialEq, Eq)]
struct ReceiverChain {
    parts: Vec<String>,
    start: usize,
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum GeneratedEntityTypeResolution {
    Unresolved,
    Resolved(String),
    Ambiguous,
}

impl GeneratedEntityTypeResolution {
    fn into_option(self) -> Option<String> {
        match self {
            GeneratedEntityTypeResolution::Resolved(value) => Some(value),
            GeneratedEntityTypeResolution::Unresolved
            | GeneratedEntityTypeResolution::Ambiguous => None,
        }
    }
}

fn generated_entity_type_for_receiver(
    receiver: &[String],
    imports: &JavaImports,
) -> GeneratedEntityTypeResolution {
    match receiver {
        [metamodel, _constant] if metamodel.starts_with('Q') => imports
            .type_imports
            .get(metamodel)
            .cloned()
            .map(GeneratedEntityTypeResolution::from_import_binding)
            .unwrap_or_else(|| GeneratedEntityTypeResolution::Resolved(metamodel.clone())),
        [constant] => imports
            .static_field_imports
            .get(constant)
            .cloned()
            .map(GeneratedEntityTypeResolution::from_import_binding)
            .unwrap_or(GeneratedEntityTypeResolution::Unresolved),
        _ => GeneratedEntityTypeResolution::Unresolved,
    }
}

impl GeneratedEntityTypeResolution {
    fn from_import_binding(binding: ImportBinding) -> Self {
        match binding {
            ImportBinding::Resolved(value) => GeneratedEntityTypeResolution::Resolved(value),
            ImportBinding::Ambiguous => GeneratedEntityTypeResolution::Ambiguous,
        }
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

#[derive(Debug, Clone, PartialEq, Eq)]
enum ImportBinding {
    Resolved(String),
    Ambiguous,
}

#[derive(Debug, Default)]
struct JavaImports {
    type_imports: BTreeMap<String, ImportBinding>,
    static_field_imports: BTreeMap<String, ImportBinding>,
}

impl JavaImports {
    fn parse_syntax(document: &str, root: Node<'_>) -> Self {
        let mut imports = JavaImports::default();
        collect_java_imports(document, root, &mut imports);
        imports
    }

    fn add_import_declaration(&mut self, declaration: &str) {
        let trimmed = declaration.trim();
        if let Some(imported) = trimmed
            .strip_prefix("import static ")
            .and_then(|value| value.strip_suffix(';'))
        {
            if imported.ends_with(".*") {
                return;
            }
            let Some((owner, field)) = imported.rsplit_once('.') else {
                return;
            };
            add_import_binding(&mut self.static_field_imports, field, owner);
        } else if let Some(imported) = trimmed
            .strip_prefix("import ")
            .and_then(|value| value.strip_suffix(';'))
        {
            if imported.ends_with(".*") {
                return;
            }
            let simple = simple_java_name(imported).to_string();
            add_import_binding(&mut self.type_imports, &simple, imported);
        }
    }
}

fn add_import_binding(imports: &mut BTreeMap<String, ImportBinding>, name: &str, target: &str) {
    match imports.get(name) {
        None => {
            imports.insert(
                name.to_string(),
                ImportBinding::Resolved(target.to_string()),
            );
        }
        Some(ImportBinding::Resolved(existing)) if existing == target => {}
        Some(_) => {
            imports.insert(name.to_string(), ImportBinding::Ambiguous);
        }
    }
}

fn collect_java_imports(document: &str, node: Node<'_>, imports: &mut JavaImports) {
    if node.kind() == "import_declaration" {
        imports.add_import_declaration(node_text(document, node));
        return;
    }

    let mut cursor = node.walk();
    for child in node.children(&mut cursor) {
        collect_java_imports(document, child, imports);
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

    fn diagnostic_for_fail_closed(&self, reason: GeneratedCallFailClosedReason) -> Diagnostic {
        self.diagnostic_with_code(reason.diagnostic_code(), reason.diagnostic_message())
    }

    fn diagnostic(&self, message: impl Into<String>) -> Diagnostic {
        self.diagnostic_with_code("mortar-source-map-stale", message)
    }

    fn diagnostic_with_code(&self, code: &str, message: impl Into<String>) -> Diagnostic {
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
            code: Some(lsp_types::NumberOrString::String(code.to_string())),
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
    use lsp_types::notification::{
        DidChangeTextDocument, DidOpenTextDocument, Notification, PublishDiagnostics,
    };
    use lsp_types::request::{CodeActionRequest, GotoDefinition, HoverRequest, Request};
    use lsp_types::{
        CodeActionContext, CodeActionKind, CodeActionOptions, CodeActionOrCommand,
        CodeActionParams, CodeActionProviderCapability, DiagnosticSeverity,
        DidChangeTextDocumentParams, DidOpenTextDocumentParams, GotoDefinitionParams,
        GotoDefinitionResponse, HoverContents, HoverParams, HoverProviderCapability, MarkedString,
        PartialResultParams, Position, PublishDiagnosticsParams, Range,
        TextDocumentContentChangeEvent, TextDocumentIdentifier, TextDocumentItem,
        TextDocumentPositionParams, TextDocumentSyncCapability, TextDocumentSyncKind, Uri,
        VersionedTextDocumentIdentifier, WorkDoneProgressParams,
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
    fn ambiguous_type_import_for_generated_receiver_fails_closed() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

import other.QClient;
import example.QClient;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("ambiguous type import hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("ambiguous type import code actions should fail closed")
                .is_empty()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("ambiguous type import definition should fail closed")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-unsupported",
            "Mortar generated call uses unsupported alias syntax",
        );
    }

    #[test]
    fn ambiguous_static_import_for_generated_constant_fails_closed() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

import static other.QClient.CLIENT;
import static example.QClient.CLIENT;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("ambiguous static import hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("ambiguous static import code actions should fail closed")
                .is_empty()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("ambiguous static import definition should fail closed")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-unsupported",
            "Mortar generated call uses unsupported alias syntax",
        );
    }

    #[test]
    fn parenthesized_canonical_receiver_still_resolves() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        (QClient.CLIENT).read(renderer).findById(id);
        (QClient.CLIENT.read(renderer)).findAll();
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let find_by_id_hover = state
            .hover(&uri, position_of(document, "findById"))
            .expect("hover should resolve")
            .expect("parenthesized generated receiver should resolve");
        let find_all_hover = state
            .hover(&uri, position_of(document, "findAll"))
            .expect("hover should resolve")
            .expect("parenthesized generated read namespace should resolve");

        assert!(matches!(
            find_by_id_hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
        ));
        assert!(matches!(
            find_all_hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id, c.name from clients c")
        ));
    }

    #[test]
    fn malformed_incremental_generated_call_fails_closed_until_buffer_recovers() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let (server, client) = Connection::memory();
        let malformed = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
"#;
        let valid = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
"#;

        handle_notification(
            &server,
            &ServerNotification {
                method: DidOpenTextDocument::METHOD.to_string(),
                params: serde_json::to_value(DidOpenTextDocumentParams {
                    text_document: TextDocumentItem {
                        uri: uri.clone(),
                        language_id: "java".to_string(),
                        version: 1,
                        text: malformed.to_string(),
                    },
                })
                .expect("open params should serialize"),
            },
            &mut state,
        )
        .expect("malformed open should publish diagnostics");

        let Message::Notification(notification) = client
            .receiver
            .recv()
            .expect("malformed diagnostics should be sent")
        else {
            panic!("expected diagnostics notification");
        };
        let params: PublishDiagnosticsParams =
            serde_json::from_value(notification.params).expect("diagnostics should parse");
        assert_eq!(params.diagnostics.len(), 1);
        assert_eq!(
            params.diagnostics[0].message,
            "Mortar generated call is inside a malformed Java buffer"
        );
        assert!(matches!(
            params.diagnostics[0].code,
            Some(lsp_types::NumberOrString::String(ref code))
                if code == "mortar-java-buffer-malformed"
        ));
        let malformed_position = position_of(malformed, "findById");
        assert!(
            state
                .hover(&uri, malformed_position)
                .expect("malformed hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, malformed_position)
                .expect("malformed code actions should fail closed")
                .is_empty()
        );
        assert!(
            state
                .definition(&uri, malformed_position)
                .expect("malformed definition should fail closed")
                .is_none()
        );

        handle_notification(
            &server,
            &ServerNotification {
                method: DidChangeTextDocument::METHOD.to_string(),
                params: serde_json::to_value(DidChangeTextDocumentParams {
                    text_document: VersionedTextDocumentIdentifier {
                        uri: uri.clone(),
                        version: 2,
                    },
                    content_changes: vec![TextDocumentContentChangeEvent {
                        range: None,
                        range_length: None,
                        text: valid.to_string(),
                    }],
                })
                .expect("change params should serialize"),
            },
            &mut state,
        )
        .expect("valid change should publish diagnostics");

        let Message::Notification(notification) = client
            .receiver
            .recv()
            .expect("valid diagnostics should be sent")
        else {
            panic!("expected diagnostics notification");
        };
        let params: PublishDiagnosticsParams =
            serde_json::from_value(notification.params).expect("diagnostics should parse");
        assert!(params.diagnostics.is_empty());
        assert!(
            state
                .hover(&uri, position_of(valid, "findById"))
                .expect("valid hover should resolve")
                .is_some()
        );
        assert!(
            !state
                .code_actions(&uri, position_of(valid, "findById"))
                .expect("valid code actions should resolve")
                .is_empty()
        );
        assert!(
            state
                .definition(&uri, position_of(valid, "findById"))
                .expect("valid definition should resolve")
                .is_some()
        );
    }

    #[test]
    fn deeply_parenthesized_canonical_receivers_still_resolve() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        (((QClient.CLIENT))).read(renderer).findById(id);
        (((QClient.CLIENT.read(renderer)))).findAll();
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let find_by_id_hover = state
            .hover(&uri, position_of(document, "findById"))
            .expect("hover should resolve")
            .expect("deeply parenthesized generated receiver should resolve");
        let find_all_hover = state
            .hover(&uri, position_of(document, "findAll"))
            .expect("hover should resolve")
            .expect("deeply parenthesized read namespace should resolve");

        assert!(matches!(
            find_by_id_hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
        ));
        assert!(matches!(
            find_all_hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id, c.name from clients c")
        ));
    }

    #[test]
    fn cast_wrapped_canonical_receivers_still_resolve() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        ((QClient) QClient.CLIENT).read(renderer).findById(id);
        ((QClient.Read) QClient.CLIENT.read(renderer)).findAll();
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let find_by_id_hover = state
            .hover(&uri, position_of(document, "findById"))
            .expect("hover should resolve")
            .expect("cast-wrapped generated receiver should resolve");
        let find_all_hover = state
            .hover(&uri, position_of(document, "findAll"))
            .expect("hover should resolve")
            .expect("cast-wrapped read namespace should resolve");

        assert!(matches!(
            find_by_id_hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
        ));
        assert!(matches!(
            find_all_hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id, c.name from clients c")
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
        assert!(matches!(
            diagnostics[0].code,
            Some(lsp_types::NumberOrString::String(ref code))
                if code == "mortar-snapshot-missing"
        ));
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
    fn generated_looking_text_in_string_literal_is_not_resolved() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read() {
        String query = "QClient.CLIENT.read(renderer).findById(id)";
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("hover should not resolve string literal text")
                .is_none()
        );
        assert!(state.document_diagnostics(&uri).is_empty());
    }

    #[test]
    fn generated_looking_text_in_comment_is_not_resolved() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read() {
        // QClient.CLIENT.read(renderer).findById(id)
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("hover should not resolve comment text")
                .is_none()
        );
        assert!(state.document_diagnostics(&uri).is_empty());
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
    fn ordinary_read_prefix_method_is_not_fail_closed() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(ExternalReader reader) {
        reader.readable().findById(7L);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        let hover = state
            .hover(&uri, position_of(document, "findById"))
            .expect("ordinary read-prefixed chain should not error");

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
    fn helper_returned_generated_receiver_fails_closed() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        client().read(renderer).findById(id);
    }

    QClient client() {
        return QClient.CLIENT;
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

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
    }

    #[test]
    fn wildcard_static_import_is_not_a_success_path() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

import static example.QClient.*;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

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
    }

    #[test]
    fn local_metamodel_alias_resolves_source_map_backed_editor_features() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace.clone());
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var client = QClient.CLIENT;
        client.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        assert_client_find_by_id_editor_features(&state, &uri, &workspace, document);
    }

    #[test]
    fn local_read_namespace_alias_resolves_source_map_backed_editor_features() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace.clone());
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var read = QClient.CLIENT.read(renderer);
        read.findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());

        assert_client_find_by_id_editor_features(&state, &uri, &workspace, document);
    }

    #[test]
    fn local_alias_requires_explicit_local_declaration() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(QClient client, Object renderer, Long id) {
        client.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("type-only alias hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("type-only alias code actions should fail closed")
                .is_empty()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("type-only alias definition should fail closed")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-unsupported",
            "Mortar generated call uses unsupported alias syntax",
        );
    }

    #[test]
    fn local_alias_chain_fails_closed_with_unsupported_alias_diagnostic() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var client = QClient.CLIENT;
        var read = client.read(renderer);
        read.findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("alias-chain hover should fail closed")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-unsupported",
            "Mortar generated call uses unsupported alias syntax",
        );
    }

    #[test]
    fn helper_returned_metamodel_alias_fails_closed_with_unsupported_alias_diagnostic() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var client = client();
        client.read(renderer).findById(id);
    }

    QClient client() {
        return QClient.CLIENT;
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("helper-returned metamodel alias hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("helper-returned metamodel alias code actions should fail closed")
                .is_empty()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-unsupported",
            "Mortar generated call uses unsupported alias syntax",
        );
    }

    #[test]
    fn helper_returned_read_namespace_alias_fails_closed_with_unsupported_alias_diagnostic() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var read = client().read(renderer);
        read.findById(id);
    }

    QClient client() {
        return QClient.CLIENT;
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("helper-returned read alias hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("helper-returned read alias definition should fail closed")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-unsupported",
            "Mortar generated call uses unsupported alias syntax",
        );
    }

    #[test]
    fn conditional_local_alias_fails_closed_as_ambiguous() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id, boolean account) {
        var reader = account
            ? QAccount.ACCOUNT.read(renderer)
            : QClient.CLIENT.read(renderer);
        reader.findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("conditional alias hover should fail closed")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-ambiguous",
            "Mortar generated alias is ambiguous",
        );
    }

    #[test]
    fn local_alias_fail_closed_detection_is_not_file_wide() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void generatedAlias(Object renderer) {
        var client = QClient.CLIENT;
    }

    void ordinaryReader(ExternalReader client, Object renderer) {
        client.read(renderer).findById(7L);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("ordinary receiver should not be poisoned by another method")
                .is_none()
        );
        assert!(state.document_diagnostics(&uri).is_empty());
    }

    #[test]
    fn local_alias_fail_closed_detection_is_lexically_scoped() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void ordinaryReader(ExternalReader client, Object renderer) {
        {
            var client = QClient.CLIENT;
            client.toString();
        }
        client.read(renderer).findById(7L);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("ordinary receiver should not be poisoned by an inner block")
                .is_none()
        );
        assert!(state.document_diagnostics(&uri).is_empty());
    }

    #[test]
    fn inner_ordinary_local_declaration_shadows_generated_alias_without_leaking() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(ExternalReader external, Object renderer) {
        var client = QClient.CLIENT;
        {
            var client = external;
            client.read(renderer).findById(7L);
        }
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("shadowed ordinary receiver should not resolve outer alias")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("shadowed ordinary receiver should not offer SQL actions")
                .is_empty()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("shadowed ordinary receiver should not navigate")
                .is_none()
        );
        assert!(state.document_diagnostics(&uri).is_empty());
    }

    #[test]
    fn lambda_local_alias_call_fails_closed_with_unsupported_diagnostic() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        Runnable task = () -> {
            var client = QClient.CLIENT;
            client.read(renderer).findById(id);
        };
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("lambda alias hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("lambda alias code actions should fail closed")
                .is_empty()
        );
        assert_unsupported_generated_call_diagnostic(&state, &uri);
    }

    #[test]
    fn catch_local_alias_call_fails_closed_with_unsupported_diagnostic() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        try {
            throw new RuntimeException();
        } catch (RuntimeException ignored) {
            var client = QClient.CLIENT;
            client.read(renderer).findById(id);
        }
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("catch alias hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("catch alias definition should fail closed")
                .is_none()
        );
        assert_unsupported_generated_call_diagnostic(&state, &uri);
    }

    #[test]
    fn switch_local_alias_call_fails_closed_with_unsupported_diagnostic() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id, int branch) {
        switch (branch) {
            default -> {
                var client = QClient.CLIENT;
                client.read(renderer).findById(id);
            }
        }
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("switch alias hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("switch alias code actions should fail closed")
                .is_empty()
        );
        assert_unsupported_generated_call_diagnostic(&state, &uri);
    }

    #[test]
    fn parenthesized_local_alias_syntax_fails_closed() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var client = (QClient.CLIENT);
        client.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("hover should not resolve parenthesized aliases")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-unsupported",
            "Mortar generated call uses unsupported alias syntax",
        );
    }

    #[test]
    fn reassigned_local_alias_syntax_fails_closed_with_reassigned_diagnostic() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    void read(Object renderer, Long id) {
        var client = QClient.CLIENT;
        client = QAccount.ACCOUNT;
        client.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("hover should not resolve reassigned aliases")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("code actions should not resolve reassigned aliases")
                .is_empty()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-alias-reassigned",
            "Mortar generated alias is reassigned",
        );
    }

    #[test]
    fn local_alias_with_stale_source_map_fails_closed_without_marker_fallback() {
        let workspace = create_stale_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    // mortar:snapshot=example.Client.findById
    void read(Object renderer, Long id) {
        var client = QClient.CLIENT;
        client.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "client.read");

        assert!(
            state
                .hover(&uri, position)
                .expect("stale source-map alias hover should fail closed")
                .is_none()
        );
        assert!(
            state
                .code_actions(&uri, position)
                .expect("stale source-map alias code actions should fail closed")
                .is_empty()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("stale source-map alias definition should fail closed")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-source-map-stale",
            "Mortar source-map metadata is stale or missing",
        );
    }

    #[test]
    fn local_alias_with_missing_source_map_fails_closed_without_marker_fallback() {
        let workspace = create_snapshot_workspace_with(
            "example.Client.findById",
            "select c.id from clients c where c.id = ?",
        );
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    // mortar:snapshot=example.Client.findById
    void read(Object renderer, Long id) {
        var client = QClient.CLIENT;
        client.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "client.read");

        assert!(
            state
                .hover(&uri, position)
                .expect("missing source-map alias hover should fail closed")
                .is_none()
        );
        assert_fail_closed_diagnostic(
            &state,
            &uri,
            "mortar-source-map-stale",
            "Mortar source-map metadata is stale or missing",
        );
    }

    #[test]
    fn ambiguous_bare_receiver_is_not_overclaimed() {
        let workspace = create_source_map_workspace();
        let uri = file_uri_for_path(&workspace.join("ClientUsage.java"));
        let mut state = LspState::new(workspace);
        let document = r#"package example;

public final class ClientUsage {
    static final Object CLIENT = QClient.CLIENT;

    void read(Object renderer, Long id) {
        CLIENT.read(renderer).findById(id);
    }
}
"#;
        state.open_document(&uri, document.to_string());
        let position = position_of(document, "findById");

        assert!(
            state
                .hover(&uri, position)
                .expect("hover should not infer ambiguous bare receivers")
                .is_none()
        );
        assert!(
            state
                .definition(&uri, position)
                .expect("definition should not infer ambiguous bare receivers")
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
        let path = file_uri_to_path("file:///workspace/mortar%20workspace")
            .expect("URI should convert to a path");

        assert_eq!(
            path.to_string_lossy().replace('\\', "/"),
            "/workspace/mortar workspace"
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

    fn assert_client_find_by_id_editor_features(
        state: &LspState,
        uri: &Uri,
        workspace: &Path,
        document: &str,
    ) {
        let position = position_of(document, "findById");
        let hover = state
            .hover(uri, position)
            .expect("hover should resolve")
            .expect("generated alias hover should resolve");
        assert!(matches!(
            hover.contents,
            HoverContents::Scalar(MarkedString::String(ref value))
                if value.contains("select c.id from clients c where c.id = ?")
        ));

        let actions = state
            .code_actions(uri, position)
            .expect("code actions should resolve");
        let titles = actions
            .iter()
            .map(|action| match action {
                CodeActionOrCommand::CodeAction(action) => action.title.as_str(),
                CodeActionOrCommand::Command(command) => command.title.as_str(),
            })
            .collect::<Vec<_>>();
        assert!(titles.contains(&"Copy generated SQL"));
        assert!(titles.contains(&"Run PostgreSQL EXPLAIN"));

        let definition = state
            .definition(uri, position)
            .expect("definition should resolve")
            .expect("definition should navigate to snapshot");
        let GotoDefinitionResponse::Scalar(location) = definition else {
            panic!("expected one snapshot location");
        };
        assert_eq!(
            location.uri,
            file_uri_for_path(&workspace.join("mortar.sql.snap.json"))
        );
        assert_eq!(
            location.range.start.line,
            snapshot_line(workspace, "example.Client.findById")
        );
        assert!(state.document_diagnostics(uri).is_empty());
    }

    fn assert_unsupported_generated_call_diagnostic(state: &LspState, uri: &Uri) {
        assert_fail_closed_diagnostic(
            state,
            uri,
            "mortar-alias-unsupported",
            "Mortar generated call uses unsupported alias syntax",
        );
    }

    fn assert_fail_closed_diagnostic(
        state: &LspState,
        uri: &Uri,
        expected_code: &str,
        expected_message: &str,
    ) {
        let diagnostics = state.document_diagnostics(uri);
        assert_eq!(diagnostics.len(), 1);
        assert_eq!(diagnostics[0].message, expected_message);
        assert_eq!(diagnostics[0].severity, Some(DiagnosticSeverity::WARNING));
        assert!(matches!(
            diagnostics[0].code,
            Some(lsp_types::NumberOrString::String(ref code))
                if code == expected_code
        ));
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
