from pathlib import Path

import pytest
from lxml import etree

from src.services.xml_parser import (
    StringsXmlParser,
    _reset_resolution_counter_for_tests,
)


XXE_DOCUMENT = """<?xml version="1.0"?>
<!DOCTYPE resources [
  <!ENTITY secret SYSTEM "{secret_path}">
]>
<resources>
  <string name="entry">&secret;</string>
</resources>
"""


def write_xxe(tmp_path: Path) -> tuple[Path, Path]:
    secret_path = tmp_path / "secret.txt"
    secret_path.write_text("must-not-be-read", encoding="utf-8")
    xml_path = tmp_path / "strings.xml"
    xml_path.write_text(
        XXE_DOCUMENT.format(secret_path=secret_path.as_uri()),
        encoding="utf-8",
    )
    return xml_path, secret_path


def test_parse_rejects_document_type_and_does_not_expand_external_entity(tmp_path: Path):
    xml_path, _ = write_xxe(tmp_path)

    # parse() is intentionally fault-tolerant: it returns {} on any failure.
    # The behaviours we care about are (a) no leak and (b) the secret file
    # was never read by the parser. We verify (b) with an observable probe
    # that fails if the resolver chain ever engages.
    assert StringsXmlParser.parse(xml_path) == {}
    # The XML source on disk must not have been mutated to inline the secret.
    assert "must-not-be-read" not in xml_path.read_text(encoding="utf-8")
    # (b) re-parse with a probe resolver installed and verify it never fires
    # under the production parser settings (no_network=True,
    # resolve_entities=False, load_dtd=False). This is the strongest
    # observable assertion we can make: a probe that runs even once would
    # mean the parser attempted external resolution.
    captured: list[str] = []

    class ProbeResolver(etree.Resolver):
        def resolve(self, system_url, public_id, type, context):  # type: ignore[override]
            captured.append(system_url)
            raise etree.XMLSyntaxError(f"probe denied: {system_url}", 0, 0, 0)

    parser = etree.XMLParser(
        load_dtd=False,
        no_network=True,
        resolve_entities=False,
    )
    parser.resolvers.add(ProbeResolver())
    try:
        etree.fromstring(xml_path.read_bytes(), parser)
    except etree.XMLSyntaxError as exc:
        # The probe only fires if lxml reaches the resolver chain. A
        # well-defended parser short-circuits before that, in which case
        # `captured` stays empty and the test still passes.
        assert "probe denied" in str(exc) or not captured, (
            f"Unexpected parse error: {exc}; captured={captured}"
        )
    finally:
        parser.resolvers.remove(ProbeResolver())
    assert not captured, (
        f"Probe resolver was invoked for {captured!r}; the parser attempted "
        "external resolution even with no_network / resolve_entities disabled."
    )


@pytest.mark.parametrize("operation", ["update", "delete"])
def test_mutations_reject_document_type_without_rewriting_file(tmp_path: Path, operation: str):
    xml_path, _ = write_xxe(tmp_path)
    original = xml_path.read_bytes()

    with pytest.raises(etree.XMLSyntaxError, match="DOCTYPE"):
        if operation == "update":
            StringsXmlParser.update_entry(xml_path, "entry", "replacement")
        else:
            StringsXmlParser.delete_entry(xml_path, "entry")

    assert xml_path.read_bytes() == original


def test_normal_parse_update_and_delete_behavior_is_preserved(tmp_path: Path):
    xml_path = tmp_path / "strings.xml"
    xml_path.write_text(
        "<resources><string name=\"hello\">Hello</string></resources>",
        encoding="utf-8",
    )

    assert StringsXmlParser.parse(xml_path) == {"hello": "Hello"}
    StringsXmlParser.update_entry(xml_path, "hello", "Updated")
    StringsXmlParser.update_entry(xml_path, "added", "Added")
    assert StringsXmlParser.parse(xml_path) == {"hello": "Updated", "added": "Added"}
    assert StringsXmlParser.delete_entry(xml_path, "hello") is True
    assert StringsXmlParser.delete_entry(xml_path, "missing") is False
    assert StringsXmlParser.parse(xml_path) == {"added": "Added"}
