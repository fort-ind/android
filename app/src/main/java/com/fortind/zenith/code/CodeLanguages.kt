package com.fortind.zenith.code

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class CodeLanguage(val grammar: String, val label: String)

data class ResolvedLanguage(val language: CodeLanguage?, val label: String?)

object CodeLanguages {
    private val table: Map<String, CodeLanguage> = buildMap {
        fun add(grammar: String, label: String, vararg tags: String) = tags.forEach { put(it, CodeLanguage(grammar, label)) }
        add("javascript", "JavaScript", "javascript", "js", "jsx", "mjs", "cjs", "node")
        add("javascript", "TypeScript", "typescript", "ts", "tsx")
        add("json", "JSON", "json", "jsonc", "json5")
        add("markup", "HTML", "html", "xhtml", "markup")
        add("markup", "XML", "xml", "svg", "rss", "atom", "xsl", "xslt", "mathml")
        add("css", "CSS", "css")
        add("python", "Python", "python", "py", "py3", "python3", "pyw")
        add("kotlin", "Kotlin", "kotlin", "kt", "kts")
        add("java", "Java", "java")
        add("groovy", "Groovy", "groovy", "gradle")
        add("scala", "Scala", "scala")
        add("c", "C", "c", "h")
        add("cpp", "C++", "cpp", "c++", "cc", "cxx", "hpp", "hh", "hxx")
        add("csharp", "C#", "csharp", "cs", "c#", "dotnet")
        add("go", "Go", "go", "golang")
        add("swift", "Swift", "swift")
        add("dart", "Dart", "dart")
        add("bash", "Bash", "bash", "zsh")
        add("bash", "Shell", "sh", "shell")
        add("sql", "SQL", "sql", "mysql", "pgsql", "postgres", "postgresql", "sqlite", "plsql")
        add("yaml", "YAML", "yaml", "yml")
        add("ini", "INI", "ini", "cfg", "conf")
        add("ini", "TOML", "toml")
        add("makefile", "Makefile", "makefile", "make", "mk")
        add("diff", "Diff", "diff", "patch")
        add("git", "Git", "git")
        add("latex", "LaTeX", "latex", "tex")
        add("regex", "Regex", "regex", "regexp")
        add("clojure", "Clojure", "clojure", "clj")
    }

    val grammars: Set<String> get() = table.values.mapTo(HashSet()) { it.grammar }

    fun resolve(tag: String?, code: String): ResolvedLanguage {
        val clean = tag?.trim().orEmpty()
        if (clean.isEmpty()) {
            val guessed = guess(code)
            return ResolvedLanguage(guessed, guessed?.label)
        }
        val known = table[clean.lowercase()]
        val label = known?.label ?: clean.takeIf(::isLanguageTag)
        return ResolvedLanguage(known, label)
    }

    fun isLanguageTag(text: String): Boolean = text.length in 1..MAX_TAG_LENGTH && text.all { it.isLetterOrDigit() || it in "+#-_." }

    fun guess(code: String): CodeLanguage? {
        val text = code.trimStart()
        return when {
            text.startsWith("<?xml") -> table.getValue("xml")
            looksLikeMarkup(text) -> table.getValue("html")
            (text.startsWith("{") || text.startsWith("[")) && isJson(text) -> table.getValue("json")
            else -> null
        }
    }

    private fun looksLikeMarkup(text: String): Boolean =
        text.length > 1 && text[0] == '<' && (text[1].isLetter() || text[1] == '!' || text[1] == '/') && '>' in text

    private fun isJson(text: String): Boolean = try {
        if (text.startsWith("{")) JSONObject(text) else JSONArray(text)
        true
    } catch (_: JSONException) {
        false
    }

    private const val MAX_TAG_LENGTH = 20
}
