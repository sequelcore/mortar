use clap::{Parser, Subcommand};
use mortar_compiler::{SqlSnapshot, SqlSnapshotMetadata};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Parser)]
#[command(name = "mortar")]
#[command(about = "Mortar query tooling for Java-first, SQL-transparent systems.")]
struct Cli {
    #[command(subcommand)]
    command: Command,
}

#[derive(Debug, Subcommand)]
enum Command {
    Doctor {
        #[arg(long)]
        json: bool,
    },
    Inspect {
        #[arg(long)]
        sql: Option<String>,
        #[arg(long)]
        metadata_file: Option<PathBuf>,
        #[arg(long)]
        json: bool,
    },
    Snapshot {
        #[command(subcommand)]
        command: SnapshotCommand,
    },
    Schema {
        #[command(subcommand)]
        command: SchemaCommand,
    },
    Explain {
        #[arg(long)]
        connection: String,
        #[arg(long)]
        sql: String,
    },
    Report {
        #[arg(long)]
        metadata_file: PathBuf,
        #[arg(long)]
        json: bool,
    },
}

#[derive(Debug, Subcommand)]
enum SnapshotCommand {
    Check {
        #[arg(long)]
        file: PathBuf,
        #[arg(long)]
        json: bool,
    },
    Update {
        #[arg(long)]
        file: PathBuf,
        #[arg(long)]
        name: String,
        #[arg(long)]
        sql: String,
    },
}

#[derive(Debug, Subcommand)]
enum SchemaCommand {
    Check {
        #[arg(long)]
        connection: String,
        #[arg(long)]
        metadata_file: PathBuf,
    },
}

fn main() {
    let cli = Cli::parse();

    match cli.command {
        Command::Doctor { json } => {
            if json {
                println!("{}", mortar_cli::doctor_report_json());
            } else {
                println!("{}", mortar_cli::doctor_report());
            }
        }
        Command::Inspect {
            sql,
            metadata_file,
            json,
        } => inspect(sql, metadata_file, json),
        Command::Explain { connection, sql } => {
            if let Err(error) = explain(&connection, &sql) {
                eprintln!("{error}");
                std::process::exit(1);
            }
        }
        Command::Report {
            metadata_file,
            json,
        } => {
            if let Err(error) = report(&metadata_file, json) {
                eprintln!("{error}");
                std::process::exit(1);
            }
        }
        Command::Snapshot { command } => match command {
            SnapshotCommand::Check { file, json } => {
                if let Err(error) = check_snapshot_file(&file) {
                    eprintln!("{error}");
                    std::process::exit(1);
                }
                if json {
                    println!(
                        "{}",
                        serde_json::json!({"file": file.display().to_string(), "status": "ok"})
                    );
                } else {
                    println!("SQL snapshot OK {}", file.display());
                }
            }
            SnapshotCommand::Update { file, name, sql } => {
                if let Err(error) = update_snapshot_file(&file, name, sql) {
                    eprintln!("{error}");
                    std::process::exit(1);
                }
                println!("Updated SQL snapshot {}", file.display());
            }
        },
        Command::Schema { command } => match command {
            SchemaCommand::Check {
                connection,
                metadata_file,
            } => {
                if let Err(error) = schema_check(&connection, &metadata_file) {
                    eprintln!("{error}");
                    std::process::exit(1);
                }
            }
        },
    }
}

fn schema_check(connection: &str, metadata_file: &Path) -> Result<(), Box<dyn std::error::Error>> {
    let content = fs::read_to_string(metadata_file)?;
    println!(
        "{}",
        mortar_cli::schema_check_postgres_text(connection, &content)?
    );
    Ok(())
}

fn report(metadata_file: &Path, json: bool) -> Result<(), Box<dyn std::error::Error>> {
    let content = fs::read_to_string(metadata_file)?;
    if json {
        println!("{}", mortar_cli::metadata_report_json(&content)?);
    } else {
        println!("{}", mortar_cli::metadata_report_text(&content)?);
    }
    Ok(())
}

fn explain(connection: &str, sql: &str) -> Result<(), Box<dyn std::error::Error>> {
    match mortar_cli::explain_postgres_text(connection, sql) {
        Ok(plan) => println!("{plan}"),
        Err(error) => {
            return Err(format!(
                "PostgreSQL explain failed for {}: {error}",
                mortar_compiler::redact_connection_string(connection)
            )
            .into());
        }
    }
    Ok(())
}

fn check_snapshot_file(file: &Path) -> Result<(), Box<dyn std::error::Error>> {
    let content = fs::read_to_string(file)?;
    mortar_cli::check_sql_snapshot_content(&content).map_err(Into::into)
}

fn inspect(sql: Option<String>, metadata_file: Option<PathBuf>, json: bool) {
    let result = match (sql, metadata_file) {
        (Some(sql), None) => {
            if json {
                mortar_cli::inspect_sql_json(&sql).map_err(|error| error.to_string())
            } else {
                mortar_cli::inspect_sql_text(&sql).map_err(|error| error.to_string())
            }
        }
        (None, Some(metadata_file)) => fs::read_to_string(metadata_file)
            .map_err(|error| error.to_string())
            .and_then(|content| {
                if json {
                    mortar_cli::inspect_metadata_json(&content).map_err(|error| error.to_string())
                } else {
                    mortar_cli::inspect_metadata_text(&content).map_err(|error| error.to_string())
                }
            }),
        _ => Err("Provide exactly one of --sql or --metadata-file".to_string()),
    };

    match result {
        Ok(output) => println!("{output}"),
        Err(error) => {
            eprintln!("{error}");
            std::process::exit(1);
        }
    }
}

fn update_snapshot_file(
    file: &Path,
    name: String,
    sql: String,
) -> Result<(), Box<dyn std::error::Error>> {
    let existing = if file.exists() {
        Some(fs::read_to_string(file)?)
    } else {
        None
    };
    let snapshot = SqlSnapshot {
        name,
        sql,
        parameters: Vec::new(),
        metadata: SqlSnapshotMetadata {
            tables: Vec::new(),
            columns: Vec::new(),
            joins: Vec::new(),
        },
    };
    let updated = mortar_cli::update_sql_snapshot_content(existing.as_deref(), snapshot)?;

    if let Some(parent) = file.parent() {
        if !parent.as_os_str().is_empty() {
            fs::create_dir_all(parent)?;
        }
    }
    fs::write(file, updated)?;

    Ok(())
}
