use mortar_compiler::{
    detect_source_map_freshness, parse_mortar_metadata_file, parse_mortar_source_map_file,
};

#[test]
fn accepts_java_emitted_r18_source_map_contract_fixture() {
    let metadata = parse_mortar_metadata_file(include_str!(
        "../test-fixtures/source-map-contract/r18/entities.json"
    ))
    .expect("Java-emitted metadata fixture should parse");
    let source_map = parse_mortar_source_map_file(include_str!(
        "../test-fixtures/source-map-contract/r18/source-map.json"
    ))
    .expect("Java-emitted source-map fixture should parse");

    assert!(
        detect_source_map_freshness(&metadata, &source_map).is_empty(),
        "Java-emitted source-map fixture should be fresh against Java-emitted metadata"
    );
}
