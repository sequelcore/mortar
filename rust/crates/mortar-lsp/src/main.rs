fn main() {
    if let Err(error) = mortar_lsp::serve_stdio() {
        eprintln!("{error}");
        std::process::exit(1);
    }
}
