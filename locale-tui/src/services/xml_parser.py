"""Android strings.xml parser service."""

from pathlib import Path
from lxml import etree


# Module-level resolver counter. Tests inspect this to prove that the
# parser would have refused an external-resource lookup, rather than
# relying on the parser silently ignoring it.
_external_resolution_attempts = 0


class _DenyExternalResolver(etree.Resolver):
    """lxml resolver that refuses every external-resource lookup.

    Installed at parse time so that any attempt to fetch an entity from
    the file system, network, or elsewhere raises an XMLSyntaxError.
    """

    def resolve(self, system_url, public_id, type, context):  # type: ignore[override]
        global _external_resolution_attempts
        _external_resolution_attempts += 1
        raise etree.XMLSyntaxError(
            f"external resource resolution denied: {system_url}",
            0,
            0,
            0,
        )


def _reset_resolution_counter_for_tests() -> int:
    """Reset and return the previous counter. Intended for unit tests only."""
    global _external_resolution_attempts
    previous = _external_resolution_attempts
    _external_resolution_attempts = 0
    return previous


class StringsXmlParser:
    """Android strings.xml parser."""

    @staticmethod
    def _parse_tree(file_path: Path) -> etree._ElementTree:
        """Parse XML without loading DTDs, networks, or expanding entities."""
        parser = etree.XMLParser(
            load_dtd=False,
            no_network=True,
            resolve_entities=False,
        )
        # Install a denying resolver at parse time so any external-entity
        # lookup raises instead of silently succeeding via the default
        # resolver chain.
        resolver = _DenyExternalResolver()
        parser.resolvers.add(resolver)
        try:
            root = etree.fromstring(file_path.read_bytes(), parser)
        finally:
            parser.resolvers.remove(resolver)
        tree = etree.ElementTree(root)
        if tree.docinfo.doctype:
            raise etree.XMLSyntaxError("DOCTYPE is not allowed", 0, 0, 0)
        return tree

    @staticmethod
    def parse(file_path: Path) -> dict[str, str]:
        """Parse strings.xml file, returns {name: value} dict."""
        if not file_path.exists():
            return {}

        try:
            tree = StringsXmlParser._parse_tree(file_path)
            root = tree.getroot()
            result = {}

            for string_elem in root.findall("string"):
                name = string_elem.get("name")
                if name:
                    value = StringsXmlParser._get_text_content(string_elem)
                    result[name] = value

            return result
        except Exception:
            return {}

    @staticmethod
    def _get_text_content(elem) -> str:
        """Extract element text content."""
        if elem.text:
            return elem.text
        return ""

    @staticmethod
    def write(file_path: Path, entries: dict[str, str]) -> None:
        """Write strings.xml file."""
        root = etree.Element("resources")

        for name in sorted(entries.keys()):
            value = entries[name]
            string_elem = etree.SubElement(root, "string")
            string_elem.set("name", name)
            string_elem.text = value

        # Ensure directory exists
        file_path.parent.mkdir(parents=True, exist_ok=True)

        etree.indent(root, space="  ")
        tree = etree.ElementTree(root)
        tree.write(
            str(file_path), encoding="utf-8", xml_declaration=True, pretty_print=True
        )

    @staticmethod
    def update_entry(file_path: Path, key: str, value: str) -> None:
        """Update single entry."""
        if not file_path.exists():
            StringsXmlParser.write(file_path, {key: value})
            return

        tree = StringsXmlParser._parse_tree(file_path)
        root = tree.getroot()

        # Find existing entry
        found = False
        for string_elem in root.findall("string"):
            if string_elem.get("name") == key:
                string_elem.text = value
                found = True
                break

        # Add if not exists
        if not found:
            string_elem = etree.SubElement(root, "string")
            string_elem.set("name", key)
            string_elem.text = value

        # Clean up whitespace and format
        etree.indent(root, space="  ")

        tree.write(
            str(file_path), encoding="utf-8", xml_declaration=True, pretty_print=True
        )

        # Add newline at end of file
        with open(file_path, 'a', encoding='utf-8') as f:
            f.write('\n')

    @staticmethod
    def delete_entry(file_path: Path, key: str) -> bool:
        """Delete single entry."""
        if not file_path.exists():
            return False

        tree = StringsXmlParser._parse_tree(file_path)
        root = tree.getroot()

        for string_elem in root.findall("string"):
            if string_elem.get("name") == key:
                root.remove(string_elem)
                etree.indent(root, space="  ")
                tree.write(
                    str(file_path),
                    encoding="utf-8",
                    xml_declaration=True,
                    pretty_print=True,
                )
                return True

        return False
