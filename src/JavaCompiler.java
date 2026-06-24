import java.util.*;
import java.util.regex.*;

public class JavaCompiler {

    /* ── Public result ───────────────────────────────────────────────── */
    public static class CompileResult {
        public final List<CompilerError> errors;
        public final List<String> output;

        CompileResult(List<CompilerError> e, List<String> o) {
            errors = e;
            output = o;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /* ── Token types ─────────────────────────────────────────────────── */
    private enum TT {
        KEYWORD, PRIMITIVE, ACCESS_MOD, IDENTIFIER,
        NUMBER, STRING, CHAR_LIT, BOOL_LIT, NULL_LIT,
        OPERATOR, SEPARATOR, COMMENT, UNKNOWN
    }

    private record Token(String text, TT type, int line, int col) {
    }

    /* ── Static sets ─────────────────────────────────────────────────── */
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "break", "case", "catch", "class", "const", "continue",
            "default", "do", "else", "enum", "extends", "final", "finally", "for", "goto",
            "if", "implements", "import", "instanceof", "interface", "new", "package",
            "return", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "volatile", "while", "record", "sealed",
            "permits", "yield", "var");
    private static final Set<String> PRIMITIVES = Set.of(
            "boolean", "byte", "char", "double", "float", "int", "long", "short", "void", "String");
    private static final Set<String> ACCESS_MODS = Set.of("public", "private", "protected");
    private static final Set<String> BOOL_LITS = Set.of("true", "false");
    private static final Set<String> OPEN_BRACKS = Set.of("{", "(", "[");
    private static final Set<String> CLOSE_BRACKS = Set.of("}", ")", "]");
    private static final Map<String, String> MATCHING = Map.of("}", "{", ")", "(", "]", "[");
    private static final Set<String> CTRL_KW = Set.of(
            "for", "while", "if", "else", "try", "catch", "finally", "switch", "do", "case", "default");
    private static final Set<String> DECL_MODIFIERS = Set.of(
            "public", "private", "protected", "static", "final", "abstract",
            "strictfp", "sealed", "non-sealed", "transient", "volatile");
    private static final Set<String> TYPE_DECLS = Set.of("class", "interface", "enum", "record");
    private static final Set<String> BUILT_IN = Set.of(
            "System", "Math", "String", "Object", "Integer", "Double", "Boolean", "Long", "Float",
            "Short", "Byte", "Character", "Arrays", "List", "ArrayList", "Map", "HashMap", "Set",
            "HashSet", "Scanner", "PrintStream", "out", "err", "in",
            "println", "print", "printf", "length", "size", "toString", "equals",
            "charAt", "substring", "contains", "main", "args");

    /* ── Token regex ─────────────────────────────────────────────────── */
    private static final Pattern TOKEN_RE = Pattern.compile(
            "//[^\n]*"
                    + "|\\/\\*[\\s\\S]*?\\*\\/"
                    + "|\"(\\\\.|[^\"\n])*\""
                    + "|'(\\\\.|[^'\n])'"
                    + "|[a-zA-Z_$][a-zA-Z0-9_$]*"
                    + "|\\d+\\.\\d+"
                    + "|\\d+"
                    + "|==|!=|>=|<=|&&|\\|\\||\\+\\+|--|\\+=|-=|\\*=|\\/=|%=|->|::"
                    + "|[+\\-*/%=<>&|!^~?:@]"
                    + "|[{}()\\[\\];,.]");

    /* ── Entry point ─────────────────────────────────────────────────── */
    public CompileResult compile(String source) {
        String[] rawLines = source.split("\\R", -1);
        List<List<Token>> byLine = lex(rawLines);
        List<CompilerError> errors = new ArrayList<>();

        syntaxCheck(rawLines, byLine, errors);
        if (errors.isEmpty())
            semanticCheck(byLine, errors);

        errors.sort(Comparator.comparingInt(CompilerError::getLine));
        List<String> output = errors.isEmpty() ? simulateOutput(source) : List.of();
        return new CompileResult(errors, output);
    }

    /*
     * ═══════════════════════════════════════════════════════════════════
     * PASS 1 – LEXER
     * ═══════════════════════════════════════════════════════════════════
     */
    private List<List<Token>> lex(String[] lines) {
        List<List<Token>> result = new ArrayList<>();
        boolean inBlock = false;

        for (int i = 0; i < lines.length; i++) {
            List<Token> row = new ArrayList<>();
            String line = lines[i];

            if (inBlock) {
                int end = line.indexOf("*/");
                if (end >= 0) {
                    row.add(new Token(line.substring(0, end + 2), TT.COMMENT, i + 1, 0));
                    line = line.substring(end + 2);
                    inBlock = false;
                } else {
                    row.add(new Token(line, TT.COMMENT, i + 1, 0));
                    result.add(row);
                    continue;
                }
            }

            Matcher m = TOKEN_RE.matcher(line);
            while (m.find()) {
                String t = m.group();
                TT tt;
                if (t.startsWith("//") || t.startsWith("/*"))
                    tt = TT.COMMENT;
                else if (t.startsWith("\""))
                    tt = TT.STRING;
                else if (t.startsWith("'"))
                    tt = TT.CHAR_LIT;
                else if (BOOL_LITS.contains(t))
                    tt = TT.BOOL_LIT;
                else if ("null".equals(t))
                    tt = TT.NULL_LIT;
                else if (ACCESS_MODS.contains(t))
                    tt = TT.ACCESS_MOD;
                else if (PRIMITIVES.contains(t))
                    tt = TT.PRIMITIVE;
                else if (KEYWORDS.contains(t))
                    tt = TT.KEYWORD;
                else if (t.matches("[{}()\\[\\];,.]"))
                    tt = TT.SEPARATOR;
                else if (t.matches("\\d+(\\.\\d+)?"))
                    tt = TT.NUMBER;
                else if (t.matches("[a-zA-Z_$][a-zA-Z0-9_$]*"))
                    tt = TT.IDENTIFIER;
                else
                    tt = TT.OPERATOR;

                row.add(new Token(t, tt, i + 1, m.start()));

                if (t.startsWith("/*") && !t.endsWith("*/")) {
                    inBlock = true;
                    break;
                }
                if (t.startsWith("//"))
                    break;
            }
            result.add(row);
        }
        return result;
    }

    /*
     * ═══════════════════════════════════════════════════════════════════
     * PASS 2 – SYNTAX
     * ═══════════════════════════════════════════════════════════════════
     */
    private void syntaxCheck(String[] raw, List<List<Token>> byLine,
            List<CompilerError> errors) {

        // 2a. Bracket balance
        Deque<Token> stack = new ArrayDeque<>();
        for (List<Token> row : byLine) {
            for (Token tk : row) {
                if (tk.type() == TT.COMMENT)
                    continue;
                if (OPEN_BRACKS.contains(tk.text())) {
                    stack.push(tk);
                } else if (CLOSE_BRACKS.contains(tk.text())) {
                    if (stack.isEmpty()) {
                        errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                                tk.line(), "Unmatched closing '" + tk.text() + "'"));
                    } else {
                        Token open = stack.pop();
                        if (!open.text().equals(MATCHING.get(tk.text()))) {
                            errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                                    tk.line(), "Mismatched bracket: expected closing for '"
                                            + open.text() + "' opened at line " + open.line()));
                        }
                    }
                }
            }
        }
        for (Token unclosed : stack) {
            errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                    unclosed.line(), "Unclosed '" + unclosed.text() + "'"));
        }

        // 2b. Unterminated string / char
        for (int i = 0; i < raw.length; i++) {
            List<Token> nc = nonComment(byLine.get(i));
            if (nc.isEmpty())
                continue;
            if (unterminatedString(raw[i]))
                errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                        i + 1, "Unterminated string literal"));
            if (unterminatedChar(raw[i]))
                errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                        i + 1, "Unterminated or malformed char literal"));
        }

        // 2c. Missing semicolons
        for (int i = 0; i < byLine.size(); i++) {
            List<Token> nc = nonComment(byLine.get(i));
            if (nc.isEmpty())
                continue;
            if (needsSemicolon(nc)) {
                Token last = nc.get(nc.size() - 1);
                if (!last.text().equals(";")) {
                    errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                            i + 1, "Missing semicolon ';' at end of statement"));
                }
            }
        }

        // 2d. Malformed declarations (e.g. int 123)
        for (int i = 0; i < byLine.size(); i++) {
            List<Token> nc = nonComment(byLine.get(i));
            for (int j = 0; j < nc.size() - 1; j++) {
                if (nc.get(j).type() == TT.PRIMITIVE && nc.get(j + 1).type() == TT.NUMBER) {
                    errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                            i + 1, "Invalid identifier '" + nc.get(j + 1).text()
                                    + "' after type '" + nc.get(j).text() + "'"));
                }
            }
        }

        // 2e. Control flow missing parens
        for (int i = 0; i < byLine.size(); i++) {
            List<Token> nc = nonComment(byLine.get(i));
            if (nc.isEmpty())
                continue;
            String kw = nc.get(0).text();
            if (Set.of("if", "while", "for", "switch").contains(kw)) {
                if (nc.size() < 2 || !nc.get(1).text().equals("(")) {
                    errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                            i + 1, "'" + kw + "' requires '(' after keyword"));
                }
            }
        }

        // 2f. Dangling else
        boolean prevElse = false;
        for (List<Token> row : byLine) {
            List<Token> nc = nonComment(row);
            if (nc.isEmpty())
                continue;
            String first = nc.get(0).text();
            if (first.equals("else")) {
                if (prevElse) {
                    errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                            nc.get(0).line(), "'else' without matching 'if'"));
                }
                prevElse = true;
            } else if (first.equals("if")) {
                prevElse = false;
            } else {
                prevElse = false;
            }
        }

        // 2g. Class/interface/enum/record declaration syntax
        for (int i = 0; i < byLine.size(); i++) {
            List<Token> nc = nonComment(byLine.get(i));
            for (int j = 0; j < nc.size(); j++) {
                Token tk = nc.get(j);
                if (TYPE_DECLS.contains(tk.text())) {
                    if (j + 1 >= nc.size() || nc.get(j + 1).type() != TT.IDENTIFIER) {
                        errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                                tk.line(), "Expected identifier after '" + tk.text() + "'"));
                    }
                    for (int k = j - 1; k >= 0; k--) {
                        String prev = nc.get(k).text();
                        if (DECL_MODIFIERS.contains(prev))
                            continue;
                        if (prev.equals("{") || prev.equals("}") || prev.equals(";")
                                || prev.equals("(") || prev.equals(")"))
                            break;
                        if (!prev.equals("@")) {
                            errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                                    tk.line(), "Invalid token '" + prev + "' before '" + tk.text() + "' declaration"));
                        }
                        break;
                    }
                }
            }
        }

        // 2h. Multiple consecutive semicolons
        for (int i = 0; i < byLine.size(); i++) {
            List<Token> nc = nonComment(byLine.get(i));
            for (int j = 0; j < nc.size() - 1; j++) {
                if (nc.get(j).text().equals(";") && nc.get(j + 1).text().equals(";")) {
                    errors.add(new CompilerError(CompilerError.ErrorType.SYNTAX,
                            i + 1, "Unexpected token ';' — multiple semicolons are not allowed"));
                    break;
                }
            }
        }
    }

    private boolean unterminatedString(String line) {
        boolean inStr = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && inStr) {
                i++;
                continue;
            }
            if (c == '"')
                inStr = !inStr;
            if (!inStr && i + 1 < line.length() && c == '/' && line.charAt(i + 1) == '/')
                break;
        }
        return inStr;
    }

    private boolean unterminatedChar(String line) {
        String stripped = Pattern.compile("'(\\\\.|[^'\\\\])'").matcher(line).replaceAll("''");
        return stripped.chars().filter(c -> c == '\'').count() % 2 != 0;
    }

    private boolean needsSemicolon(List<Token> toks) {
        if (toks.isEmpty())
            return false;
        Token first = toks.get(0);
        Token last = toks.get(toks.size() - 1);
        String lt = last.text();
        if (lt.equals(";") || lt.equals("{") || lt.equals("}"))
            return false;
        if (first.text().startsWith("@"))
            return false;
        for (Token t : toks)
            if (t.text().equals("class") || t.text().equals("interface")
                    || t.text().equals("enum") || t.text().equals("record"))
                return false;
        if (first.type() == TT.KEYWORD && CTRL_KW.contains(first.text()))
            return false;
        if (lt.equals(")")) {
            if (hasToken(toks, "main"))
                return false;
            for (Token t : toks)
                if (PRIMITIVES.contains(t.text()) || ACCESS_MODS.contains(t.text()))
                    return false;
            return true;
        }
        if (first.type() == TT.KEYWORD &&
                Set.of("return", "throw", "break", "continue").contains(first.text()))
            return true;

        int idx = 0;
        if (idx < toks.size() && toks.get(idx).type() == TT.ACCESS_MOD)
            idx++;
        if (idx < toks.size() && Set.of("static", "final").contains(toks.get(idx).text()))
            idx++;
        if (idx < toks.size() && Set.of("static", "final").contains(toks.get(idx).text()))
            idx++;
        if (idx + 1 < toks.size()
                && (toks.get(idx).type() == TT.PRIMITIVE || toks.get(idx).type() == TT.IDENTIFIER)
                && toks.get(idx + 1).type() == TT.IDENTIFIER)
            return true;
        if (toks.size() >= 3 && toks.get(0).type() == TT.IDENTIFIER
                && toks.get(1).type() == TT.OPERATOR && toks.get(1).text().contains("="))
            return true;
        if (toks.size() >= 2 && toks.get(0).type() == TT.IDENTIFIER
                && toks.get(1).text().equals("."))
            return true;
        if (toks.size() >= 2 && toks.get(0).type() == TT.IDENTIFIER
                && toks.get(1).text().equals("("))
            return true;
        return false;
    }

    /*
     * ═══════════════════════════════════════════════════════════════════
     * PASS 3 – SEMANTIC
     * ═══════════════════════════════════════════════════════════════════
     */
    private void semanticCheck(List<List<Token>> byLine, List<CompilerError> errors) {
        Map<String, String> symTab = new LinkedHashMap<>();
        Set<String> classNames = new HashSet<>();
        String retType = null;

        for (int i = 0; i < byLine.size(); i++) {
            List<Token> nc = nonComment(byLine.get(i));
            if (nc.isEmpty())
                continue;
            int ln = i + 1;

            int ci = indexOf(nc, "class");
            if (ci >= 0 && ci + 1 < nc.size()) {
                String className = nc.get(ci + 1).text();
                if (classNames.contains(className)) {
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC,
                            ln, "Duplicate class declaration: class '" + className + "' is already declared"));
                } else {
                    classNames.add(className);
                }
            }

            String newRet = detectMethodRet(nc);
            if (newRet != null)
                retType = newRet;

            checkMethodSignature(nc, ln, errors);
            detectVarDecl(nc, ln, symTab, errors, classNames);
            detectAssign(nc, ln, symTab, errors);

            if (nc.size() >= 2 && nc.get(0).text().equals("void")
                    && nc.get(1).type() == TT.IDENTIFIER && !hasToken(nc, "("))
                errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC,
                        ln, "Cannot use 'void' as a variable type"));

            checkUndeclared(nc, ln, symTab, classNames, errors);

            if (retType != null)
                checkReturn(nc, ln, retType, errors);
        }
    }

    private String detectMethodRet(List<Token> nc) {
        int idx = 0;
        while (idx < nc.size() && (nc.get(idx).type() == TT.ACCESS_MOD
                || Set.of("static", "final", "abstract", "synchronized").contains(nc.get(idx).text())))
            idx++;
        if (idx + 2 < nc.size()
                && (nc.get(idx).type() == TT.PRIMITIVE || nc.get(idx).type() == TT.IDENTIFIER)
                && nc.get(idx + 1).type() == TT.IDENTIFIER
                && nc.get(idx + 2).text().equals("("))
            return nc.get(idx).text();
        return null;
    }

    private void checkMethodSignature(List<Token> nc, int ln, List<CompilerError> errors) {
        int idx = 0;
        boolean hasPublic = false, hasStatic = false, hasVoid = false;

        while (idx < nc.size() && (nc.get(idx).type() == TT.ACCESS_MOD
                || Set.of("static", "final", "abstract", "synchronized").contains(nc.get(idx).text()))) {
            String mod = nc.get(idx).text();
            if (mod.equals("public"))
                hasPublic = true;
            if (mod.equals("static"))
                hasStatic = true;
            idx++;
        }
        if (idx >= nc.size())
            return;
        if (nc.get(idx).text().equals("void"))
            hasVoid = true;

        if (idx + 2 < nc.size() && nc.get(idx + 1).type() == TT.IDENTIFIER
                && nc.get(idx + 2).text().equals("(")) {
            String methodName = nc.get(idx + 1).text();
            if (methodName.equals("main")) {
                boolean validMain = hasPublic && hasStatic && hasVoid && nc.get(idx).text().equals("void");
                if (!hasPublic)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "main method must be declared 'public'"));
                if (!hasStatic)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "main method must be declared 'static'"));
                if (!hasVoid)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "main method must have return type 'void'"));
                if (!validMain) {
                    int closeParenIdx = indexOf(nc, ")");
                    if (closeParenIdx > 0) {
                        String params = nc.subList(idx + 2, closeParenIdx).stream()
                                .map(Token::text).reduce("", (a, b) -> a + b);
                        if (!params.contains("String") || !params.contains("args")) {
                            errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                                    "main method parameter must be 'String[] args'"));
                        }
                    }
                }
            } else if (!nc.get(idx).text().equals("class") && !nc.get(idx).text().equals("interface")) {
                if (!hasPublic && !hasStatic) {
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Method '" + methodName + "' is missing a return type"));
                }
            }
        }
    }

    private void detectVarDecl(List<Token> nc, int ln,
            Map<String, String> symTab,
            List<CompilerError> errors,
            Set<String> classNames) {
        int idx = 0;
        while (idx < nc.size() && (nc.get(idx).type() == TT.ACCESS_MOD
                || Set.of("static", "final").contains(nc.get(idx).text())))
            idx++;
        if (idx + 1 >= nc.size())
            return;
        Token typ = nc.get(idx), name = nc.get(idx + 1);
        if ((typ.type() == TT.PRIMITIVE || typ.type() == TT.IDENTIFIER)
                && name.type() == TT.IDENTIFIER
                && (idx + 2 >= nc.size() || !nc.get(idx + 2).text().equals("("))) {
            if (symTab.containsKey(name.text()))
                errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC,
                        ln, "Duplicate variable declaration: '" + name.text() + "'"));
            else
                symTab.put(name.text(), typ.text());

            int ei = indexOf(nc, "=");
            if (ei > 0 && ei + 1 < nc.size())
                checkTypeMismatch(typ.text(), nc.get(ei + 1), ln, errors);
        }
    }

    private void detectAssign(List<Token> nc, int ln,
            Map<String, String> symTab,
            List<CompilerError> errors) {
        if (nc.size() < 3)
            return;
        if (nc.get(0).type() == TT.IDENTIFIER
                && nc.get(1).type() == TT.OPERATOR
                && nc.get(1).text().equals("=")) {
            String v = nc.get(0).text();
            if (!symTab.containsKey(v))
                errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC,
                        ln, "Assignment to undeclared variable: '" + v + "'"));
            else
                checkTypeMismatch(symTab.get(v), nc.get(2), ln, errors);
        }
    }

    private void checkTypeMismatch(String decl, Token rhs, int ln,
            List<CompilerError> errors) {
        if (rhs == null)
            return;
        switch (decl) {
            case "int", "long", "short", "byte" -> {
                if (rhs.type() == TT.STRING)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Type mismatch: cannot assign String to '" + decl + "'"));
                else if (rhs.type() == TT.BOOL_LIT)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Type mismatch: cannot assign boolean to '" + decl + "'"));
            }
            case "double", "float" -> {
                if (rhs.type() == TT.STRING)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Type mismatch: cannot assign String to '" + decl + "'"));
                else if (rhs.type() == TT.BOOL_LIT)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Type mismatch: cannot assign boolean to '" + decl + "'"));
            }
            case "boolean" -> {
                if (rhs.type() == TT.NUMBER)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Type mismatch: cannot assign number to 'boolean'"));
                else if (rhs.type() == TT.STRING)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Type mismatch: cannot assign String to 'boolean'"));
            }
            case "String" -> {
                if (rhs.type() == TT.NUMBER)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Type mismatch: cannot assign number to 'String'"));
                else if (rhs.type() == TT.BOOL_LIT)
                    errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC, ln,
                            "Type mismatch: cannot assign boolean to 'String'"));
            }
        }
    }

    private void checkUndeclared(List<Token> nc, int ln,
            Map<String, String> symTab,
            Set<String> classNames,
            List<CompilerError> errors) {
        for (int i = 0; i < nc.size() - 1; i++) {
            Token cur = nc.get(i);
            Token next = nc.get(i + 1);
            if (cur.type() == TT.IDENTIFIER
                    && (next.text().equals("(") || next.text().equals("."))
                    && !symTab.containsKey(cur.text())
                    && !classNames.contains(cur.text())
                    && !BUILT_IN.contains(cur.text())
                    && !KEYWORDS.contains(cur.text())
                    && !PRIMITIVES.contains(cur.text())) {
                errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC,
                        ln, "Undeclared identifier: '" + cur.text() + "'"));
            }
        }
    }

    private void checkReturn(List<Token> nc, int ln, String ret,
            List<CompilerError> errors) {
        if (nc.isEmpty() || !nc.get(0).text().equals("return"))
            return;
        if (ret.equals("void") && nc.size() > 2)
            errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC,
                    ln, "Cannot return a value from a void method"));
        if (!ret.equals("void") && nc.size() == 2 && nc.get(1).text().equals(";"))
            errors.add(new CompilerError(CompilerError.ErrorType.SEMANTIC,
                    ln, "Missing return value: method declared to return '" + ret + "'"));
    }

    /*
     * ═══════════════════════════════════════════════════════════════════
     * OUTPUT SIMULATION
     * ═══════════════════════════════════════════════════════════════════
     */
    private List<String> simulateOutput(String source) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> variables = new HashMap<>();
        String[] lines = source.split("\\R", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            if (line.startsWith("//") || line.isEmpty()) {
                i++;
                continue;
            }
            if (line.startsWith("int ")) {
                String[] parts = line.substring(4).split("=");
                if (parts.length == 2) {
                    String var = parts[0].trim();
                    String valStr = parts[1].trim().replace(";", "");
                    try {
                        int value = Integer.parseInt(valStr);
                        variables.put(var, value);
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (line.contains("=") && !line.startsWith("if") && !line.startsWith("else")) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    String var = parts[0].trim();
                    String valStr = parts[1].trim().replace(";", "");
                    try {
                        int value = Integer.parseInt(valStr);
                        variables.put(var, value);
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (line.startsWith("if ")) {
                i = handleIf(lines, i, variables, out);
                continue;
            } else {
                executeStatement(line, variables, out);
            }
            i++;
        }
        if (out.isEmpty())
            out.add("[Program compiled successfully — no console output detected.]");
        return out;
    }

    private int handleIf(String[] lines, int i, Map<String, Integer> variables, List<String> out) {
        String line = lines[i].trim();
        int parenStart = line.indexOf('(');
        int parenEnd = line.indexOf(')');
        String condition = line.substring(parenStart + 1, parenEnd).trim();
        boolean cond = evaluateCondition(condition, variables);
        int braceStart = line.indexOf('{');
        if (braceStart >= 0) {
            i++;
            List<String> block = parseBlock(lines, i);
            i += block.size() + 1;
            if (cond)
                executeBlock(block, variables, out);
        } else {
            String stmt = line.substring(parenEnd + 1).trim().replace(";", "");
            if (cond)
                executeStatement(stmt, variables, out);
            i++;
        }
        if (i < lines.length && lines[i].trim().startsWith("else")) {
            String elseLine = lines[i].trim();
            i++;
            if (elseLine.equals("else")) {
                if (i < lines.length) {
                    String stmt = lines[i].trim().replace(";", "");
                    if (!cond)
                        executeStatement(stmt, variables, out);
                    i++;
                }
            } else if (elseLine.startsWith("else")) {
                int elseBrace = elseLine.indexOf('{');
                if (elseBrace >= 0) {
                    List<String> block = parseBlock(lines, i);
                    i += block.size() + 1;
                    if (!cond)
                        executeBlock(block, variables, out);
                } else {
                    String stmt = elseLine.substring(4).trim().replace(";", "");
                    if (!cond)
                        executeStatement(stmt, variables, out);
                }
            }
        }
        return i;
    }

    private List<String> parseBlock(String[] lines, int start) {
        List<String> block = new ArrayList<>();
        int braceCount = 1;
        int i = start;
        while (i < lines.length && braceCount > 0) {
            String l = lines[i].trim();
            if (l.contains("{"))
                braceCount++;
            if (l.contains("}"))
                braceCount--;
            if (braceCount > 0)
                block.add(l);
            i++;
        }
        return block;
    }

    private void executeBlock(List<String> block, Map<String, Integer> variables, List<String> out) {
        for (String stmt : block)
            executeStatement(stmt, variables, out);
    }

    private void executeStatement(String stmt, Map<String, Integer> variables, List<String> out) {
        if (stmt.contains("System.out.println")) {
            Pattern p = Pattern.compile("System\\.out\\.println\\s*\\(([^;]*)\\)\\s*;?");
            Matcher m = p.matcher(stmt);
            if (m.find()) {
                String arg = m.group(1).trim();
                out.add(evalArg(arg) + "\n");
            }
        } else if (stmt.contains("System.out.print")) {
            Pattern p = Pattern.compile("System\\.out\\.print\\s*\\(([^;]*)\\)\\s*;?");
            Matcher m = p.matcher(stmt);
            if (m.find()) {
                String arg = m.group(1).trim();
                out.add(evalArg(arg));
            }
        }
    }

    private boolean evaluateCondition(String condition, Map<String, Integer> variables) {
        if (condition.contains(">=")) {
            String[] parts = condition.split(">=");
            String var = parts[0].trim();
            int val = Integer.parseInt(parts[1].trim());
            return variables.getOrDefault(var, 0) >= val;
        }
        return false;
    }

    private String evalArg(String arg) {
        StringBuilder sb = new StringBuilder();
        for (String part : arg.split("\\+")) {
            String p = part.trim();
            if (p.startsWith("\"") && p.endsWith("\"") && p.length() >= 2)
                sb.append(p, 1, p.length() - 1);
            else if (!p.isEmpty())
                sb.append(p);
        }
        return sb.toString();
    }

    /* ── Helpers ──────────────────────────────────────────────────────── */
    private List<Token> nonComment(List<Token> toks) {
        List<Token> r = new ArrayList<>();
        for (Token t : toks)
            if (t.type() != TT.COMMENT)
                r.add(t);
        return r;
    }

    private int indexOf(List<Token> toks, String text) {
        for (int i = 0; i < toks.size(); i++)
            if (toks.get(i).text().equals(text))
                return i;
        return -1;
    }

    private boolean hasToken(List<Token> toks, String text) {
        return indexOf(toks, text) >= 0;
    }
}