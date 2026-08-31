package com.orchords.highlight.languages

import com.orchords.highlight.core.Language
import com.orchords.highlight.languages.bash.bash
import com.orchords.highlight.languages.c.c
import com.orchords.highlight.languages.cmake.cmake
import com.orchords.highlight.languages.cpp.cpp
import com.orchords.highlight.languages.csharp.csharp
import com.orchords.highlight.languages.css.css
import com.orchords.highlight.languages.dart.dart
import com.orchords.highlight.languages.diff.diff
import com.orchords.highlight.languages.dockerfile.dockerfile
import com.orchords.highlight.languages.go.go
import com.orchords.highlight.languages.glsl.glsl
import com.orchords.highlight.languages.ini.ini
import com.orchords.highlight.languages.java.java
import com.orchords.highlight.languages.javascript.javascript
import com.orchords.highlight.languages.json.json
import com.orchords.highlight.languages.kotlin.kotlin
import com.orchords.highlight.languages.latex.latex
import com.orchords.highlight.languages.lua.lua
import com.orchords.highlight.languages.markdown.markdown
import com.orchords.highlight.languages.php.php
import com.orchords.highlight.languages.powershell.powershell
import com.orchords.highlight.languages.properties.properties
import com.orchords.highlight.languages.python.python
import com.orchords.highlight.languages.rust.rust
import com.orchords.highlight.languages.ruby.ruby
import com.orchords.highlight.languages.sql.sql
import com.orchords.highlight.languages.swift.swift
import com.orchords.highlight.languages.typescript.typescript
import com.orchords.highlight.languages.xml.xml
import com.orchords.highlight.languages.yaml.yaml

/**
 * Every grammar bundled with the highlighter.
 *
 * Each entry builds a fresh mode tree: compilation mutates modes in place, mirroring `highlight.js`.
 */
internal fun builtinLanguages(): List<Language> = listOf(
    json(),
    ini(),
    cmake(),
    go(),
    glsl(),
    yaml(),
    bash(),
    dockerfile(),
    javascript(),
    typescript(),
    xml(),
    css(),
    dart(),
    java(),
    kotlin(),
    latex(),
    lua(),
    powershell(),
    properties(),
    python(),
    c(),
    cpp(),
    csharp(),
    sql(),
    diff(),
    markdown(),
    rust(),
    ruby(),
    php(),
    swift(),
)
