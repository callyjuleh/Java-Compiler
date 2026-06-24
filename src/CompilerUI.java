import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.regex.*;

public class CompilerUI extends JFrame {

    // ── Fonts
    public static Font header;
    public static Font body;
    public static Font bodyBold;
    public static Font monoFont = new Font("Monospaced", Font.PLAIN, 15);

    // ── Original palette
    public static Color DarkBlue = new Color(65, 112, 164);
    public static Color LightBlue = new Color(0xDDE6F0);
    public static Color LightOrange = new Color(255, 159, 66);
    public static Color DarkOrange = new Color(255, 126, 0);
    public static GradientPaint headerPaint = new GradientPaint(0, 0, new Color(0x2B4F7A), 1440, 0,
            new Color(0x1C3557));

    // ── Terminal colors
    private static final Color TERM_BG = new Color(0x1E1E1E);
    private static final Color TERM_FG = new Color(0xD4D4D4);
    private static final Color TERM_RED = new Color(0xF14C4C);
    private static final Color TERM_GREEN = new Color(0x4EC9B0);
    private static final Color TERM_DIM = new Color(0x6A9955);

    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+[A-Za-z_$][\\w$]*");
    private static final Pattern VAR_DECL_PATTERN = Pattern.compile(
            "^\\s*(?:(?:final|static|volatile)\\s+)*"
                    + "(int|long|short|byte|double|float|boolean|char"
                    + "|String|Integer|Long|Short|Byte|Double|Float"
                    + "|Boolean|Character|Object|[A-Za-z_$][\\w$]*(?:\\[\\])*)"
                    + "(?:\\[\\])?\\s+"
                    + "([A-Za-z_$][\\w$]*)"
                    + "\\s*(?:=([^;]+))?\\s*;\\s*$");

    // ── Widgets
    private JTextArea codeEditor;
    private JTextPane outputPane;
    private StyledDocument outDoc;
    private JLabel tabLabel;

    private final JavaCompiler compiler = new JavaCompiler();

    public CompilerUI() {
        loadFonts();
        setTitle("Mini-Compiler");
        setSize(1440, 1024);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);
        setLayout(new BorderLayout(0, 0));
        add(buildTopBar(), BorderLayout.NORTH);
        add(buildMainArea(), BorderLayout.CENTER);
        setVisible(true);
    }

    private void loadFonts() {
        try {
            header = Font.createFont(Font.TRUETYPE_FONT,
                    new File("util/fonts/Inter_18pt-Bold.ttf")).deriveFont(Font.BOLD, 48f);
            body = Font.createFont(Font.TRUETYPE_FONT,
                    new File("util/fonts/Inter_18pt-Regular.ttf")).deriveFont(Font.PLAIN, 16f);
            bodyBold = Font.createFont(Font.TRUETYPE_FONT,
                    new File("util/fonts/Inter_18pt-Bold.ttf")).deriveFont(Font.BOLD, 16f);
        } catch (Exception e) {
            header = new Font("SansSerif", Font.BOLD, 40);
            body = new Font("SansSerif", Font.PLAIN, 16);
            bodyBold = new Font("SansSerif", Font.BOLD, 16);
        }
    }

    // ══════════════════════════════════════════════════════
    // UI BUILDERS (unchanged)
    // ══════════════════════════════════════════════════════

    private JPanel buildTopBar() {
        JPanel topBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(headerPaint);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        topBar.setLayout(new BoxLayout(topBar, BoxLayout.Y_AXIS));
        topBar.setBackground(DarkBlue);
        topBar.setBorder(new EmptyBorder(20, 40, 20, 40));

        JLabel title = new JLabel("Mini-Compiler");
        title.setFont(header);
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Check your Java code for syntax and semantic errors, and output when available.");
        subtitle.setFont(body);
        subtitle.setForeground(Color.WHITE);

        topBar.add(title);
        topBar.add(Box.createVerticalStrut(5));
        topBar.add(subtitle);
        return topBar;
    }

    private JSplitPane buildMainArea() {
        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                buildEditorPanel(),
                buildOutputPanel());
        split.setDividerLocation(760);
        split.setDividerSize(3);
        split.setResizeWeight(0.53);
        split.setBorder(null);
        split.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(new Color(0x2B4F7A));
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        return split;
    }

    private JPanel buildEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(0x1E2D3D));

        JPanel tabBar = new JPanel(new BorderLayout());
        tabBar.setBackground(new Color(0x1C2B3A));
        tabBar.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x2B4F7A)));
        tabBar.setPreferredSize(new Dimension(0, 44));

        JPanel tabLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabLeft.setOpaque(false);

        JPanel fileTab = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
        fileTab.setBackground(new Color(0x253545));
        fileTab.setBorder(new MatteBorder(0, 0, 2, 0, DarkOrange));
        fileTab.setPreferredSize(new Dimension(130, 44));
        tabLabel = new JLabel("SOURCE CODE");
        tabLabel.setFont(bodyBold.deriveFont(Font.PLAIN, 12f));
        tabLabel.setForeground(Color.WHITE);
        fileTab.add(tabLabel);
        tabLeft.add(fileTab);
        tabBar.add(tabLeft, BorderLayout.WEST);

        JPanel tabRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 7));
        tabRight.setOpaque(false);
        tabRight.add(buildClearButton());
        tabRight.add(buildRunButton());
        tabBar.add(tabRight, BorderLayout.EAST);

        codeEditor = new JTextArea();
        codeEditor.setFont(monoFont);
        codeEditor.setForeground(TERM_FG);
        codeEditor.setBackground(new Color(0x1E2D3D));
        codeEditor.setCaretColor(Color.WHITE);
        codeEditor.setLineWrap(false);
        codeEditor.setTabSize(4);
        codeEditor.setSelectionColor(new Color(0x264F78));
        codeEditor.setBorder(new EmptyBorder(6, 8, 6, 8));

        JTextArea lineNums = new JTextArea("1");
        lineNums.setFont(monoFont);
        lineNums.setForeground(new Color(0x5A7A90));
        lineNums.setBackground(new Color(0x1A2633));
        lineNums.setEditable(false);
        lineNums.setFocusable(false);
        lineNums.setBorder(new EmptyBorder(6, 10, 6, 10));

        codeEditor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void sync() {
                int n = codeEditor.getLineCount();
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= n; i++)
                    sb.append(i).append(i < n ? "\n" : "");
                lineNums.setText(sb.toString());
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                sync();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                sync();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                sync();
            }
        });

        JScrollPane editorScroll = new JScrollPane(codeEditor);
        editorScroll.setBorder(null);
        editorScroll.setRowHeaderView(lineNums);
        editorScroll.getViewport().setBackground(new Color(0x1E2D3D));

        panel.add(tabBar, BorderLayout.NORTH);
        panel.add(editorScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(TERM_BG);

        JPanel tabBar = new JPanel(new BorderLayout());
        tabBar.setBackground(new Color(0x1C2B3A));
        tabBar.setBorder(new MatteBorder(0, 0, 1, 0, new Color(0x2B4F7A)));
        tabBar.setPreferredSize(new Dimension(0, 44));

        JPanel tabLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabLeft.setOpaque(false);
        tabLeft.add(buildTab("TERMINAL", true));
        tabBar.add(tabLeft, BorderLayout.WEST);

        outputPane = new JTextPane();
        outDoc = outputPane.getStyledDocument();
        outputPane.setEditable(false);
        outputPane.setBackground(TERM_BG);
        outputPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
        outputPane.setBorder(new EmptyBorder(10, 14, 10, 14));

        JScrollPane scroll = new JScrollPane(outputPane);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(TERM_BG);
        scroll.setBackground(TERM_BG);

        panel.add(tabBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildTab(String label, boolean active) {
        JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 14));
        tab.setBackground(active ? new Color(0x1E1E1E) : new Color(0x1C2B3A));
        tab.setBorder(active
                ? new MatteBorder(0, 0, 2, 0, DarkOrange)
                : new EmptyBorder(0, 0, 2, 0));
        tab.setPreferredSize(new Dimension(110, 44));
        JLabel lbl = new JLabel(label);
        lbl.setFont(bodyBold.deriveFont(Font.PLAIN, 12f));
        lbl.setForeground(active ? Color.WHITE : new Color(0x6A8BA0));
        tab.add(lbl);
        return tab;
    }

    private JButton buildRunButton() {
        JButton btn = new JButton("▶  Run") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g2);
                g2.dispose();
            }

            @Override
            protected void paintBorder(Graphics g) {
            }
        };
        btn.setFont(bodyBold.deriveFont(Font.PLAIN, 14f));
        btn.setForeground(Color.WHITE);
        btn.setBackground(DarkBlue);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorder(new EmptyBorder(4, 16, 4, 16));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(DarkBlue.brighter());
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(DarkBlue);
                btn.repaint();
            }
        });
        btn.addActionListener(e -> new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                compile();
                return null;
            }
        }.execute());
        return btn;
    }

    private JButton buildClearButton() {
        JButton btn = new JButton("Clear") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g2);
                g2.dispose();
            }

            @Override
            protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DarkOrange);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        btn.setFont(bodyBold.deriveFont(Font.PLAIN, 14f));
        btn.setForeground(Color.WHITE);
        btn.setBackground(DarkOrange);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorder(new EmptyBorder(4, 12, 4, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(DarkOrange.getRed(), DarkOrange.getGreen(), DarkOrange.getBlue(), 40));
                btn.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(DarkOrange);
                btn.repaint();
            }
        });
        btn.addActionListener(e -> {
            codeEditor.setText("");
            codeEditor.setForeground(TERM_FG);
            clearOutput();
        });
        return btn;
    }

    // ══════════════════════════════════════════════════════
    // MINI SEMANTIC EVALUATOR
    // ══════════════════════════════════════════════════════

    private static class EvalResult {
        List<String> errors = new ArrayList<>();
        List<String> output = new ArrayList<>();
        /**
         * true if any Runtime/Type-mismatch error was recorded — when set,
         * execution of subsequent statements (print, if-branches) is suppressed.
         */
        boolean fatalError = false;
    }

    private EvalResult evaluateSemantics(String[] lines) {
        EvalResult res = new EvalResult();
        Map<String, Object> values = new HashMap<>();
        Map<String, Boolean> inits = new HashMap<>();
        evaluateLinesIndexed(lines, 0, lines.length, values, inits, res);
        return res;
    }

    /**
     * Processes lines[start..end).
     * Returns the index of the first line AFTER the processed range.
     *
     * KEY INVARIANT: if res.fatalError is true at entry, we still walk every
     * line for *error detection* but we do NOT add to res.output and we do NOT
     * execute if/else branches for output.
     */
    private int evaluateLinesIndexed(String[] lines, int start, int end,
            Map<String, Object> values,
            Map<String, Boolean> inits,
            EvalResult res) {
        int i = start;
        while (i < end) {
            String raw = lines[i].replace("\r", "");
            String trimmed = raw.trim();

            // Skip blanks, comments, class/method scaffolding
            if (trimmed.isEmpty()
                    || trimmed.startsWith("//")
                    || trimmed.startsWith("class ")
                    || trimmed.contains("void main")) {
                i++;
                continue;
            }

            // ── if statement ─────────────────────────────────────────────
            if (trimmed.startsWith("if") && trimmed.length() > 2
                    && !Character.isLetterOrDigit(trimmed.charAt(2))) {
                i = handleIfElse(lines, i, end, values, inits, res);
                continue;
            }

            // ── dangling else: consumed by handleIfElse; skip if seen here
            if (trimmed.startsWith("else")) {
                i++;
                continue;
            }

            int lineNum = i + 1;

            // ── 1. Variable Declarations ──────────────────────────────────
            Matcher decl = VAR_DECL_PATTERN.matcher(raw);
            if (decl.find()) {
                String varName = decl.group(2);
                String rhs = decl.group(3);

                if (rhs != null && !rhs.trim().isEmpty()) {
                    boolean rhsError = checkRhsForUndeclared(
                            rhs.trim(), inits, lineNum, raw, res, false);
                    if (!rhsError) {
                        Object val = parseExpression(rhs.trim(), values, inits, lineNum, raw, res.errors);
                        values.put(varName, val);
                        inits.put(varName, true);
                    } else {
                        values.put(varName, null);
                        inits.put(varName, false);
                    }
                } else {
                    values.put(varName, null);
                    inits.put(varName, false);
                }
                i++;
                continue;
            }

            // ── 2. Re-Assignments (e.g. age = a; or age = 5;) ────────
            Matcher assign = Pattern.compile(
                    "^\\s*([A-Za-z_$][\\w$]*)\\s*=\\s*(.+?)\\s*;\\s*$").matcher(raw);
            if (assign.find()) {
                String varName = assign.group(1);
                String rhs = assign.group(2).trim();

                if (inits.containsKey(varName)) {
                    // ── Check every identifier on the RHS ────────────────
                    boolean rhsError = checkRhsForUndeclared(
                            rhs, inits, lineNum, raw, res, true); // true = isAssignment

                    if (!rhsError) {
                        Object val = parseExpression(rhs, values, inits, lineNum, raw, res.errors);
                        values.put(varName, val);
                        inits.put(varName, true);
                    } else {
                        // Poison LHS: mark uninitialized so downstream use is also flagged
                        inits.put(varName, false);
                        values.put(varName, null);
                    }
                } else {
                    addError(res.errors, lineNum, raw, varName,
                            "Runtime Error: variable '" + varName + "' is not declared");
                    res.fatalError = true;
                }
                i++;
                continue;
            }

            // ── 3. Compound assignment (+=, -=, *=, /=, %=) ─────────────
            Matcher compound = Pattern.compile(
                    "^\\s*([A-Za-z_$][\\w$]*)\\s*(\\+=|-=|\\*=|\\/=|%=)\\s*(.+?)\\s*;\\s*$").matcher(raw);
            if (compound.find()) {
                String varName = compound.group(1);
                String op = compound.group(2);
                String rhs = compound.group(3);
                if (inits.containsKey(varName) && inits.get(varName)) {
                    Object cur = values.get(varName);
                    Object rhsVal = parseExpression(rhs, values, inits, lineNum, raw, res.errors);
                    values.put(varName, applyCompound(cur, op, rhsVal));
                }
                i++;
                continue;
            }

            // ── 4. Println / print ────────────────────────────────────────
            Matcher print = Pattern.compile(
                    "System\\.out\\.print(ln)?\\s*\\((.*)\\)\\s*;").matcher(raw);
            if (print.find()) {
                if (!res.fatalError) { // ← suppress output on fatal error
                    boolean isLn = print.group(1) != null;
                    String expr = print.group(2);
                    try {
                        Object out = parseExpression(expr, values, inits, lineNum, raw, res.errors);
                        if (out != null) {
                            String formatted = formatValue(out);
                            res.output.add(isLn ? formatted + "\n" : formatted);
                        }
                    } catch (Exception e) {
                        res.errors.add("Main.java:" + lineNum
                                + ": error: Runtime Error: " + e.getMessage() + "\n");
                        res.fatalError = true;
                    }
                }
                i++;
                continue;
            }

            // ── 5. Standalone uninitialized-use check ─────────────────────
            for (String var : inits.keySet()) {
                Pattern use = Pattern.compile(
                        "(?<![\\w$])" + Pattern.quote(var) + "(?![\\w$])");
                if (use.matcher(raw).find() && !inits.get(var)) {
                    addError(res.errors, lineNum, raw, var,
                            "Semantic Error: variable '" + var + "' might not have been initialized");
                    inits.put(var, true);
                }
            }

            i++;
        }
        return i;
    }

    /**
     * Scans every identifier token in {@code rhs} and reports any that are
     * neither a literal, a declared variable, nor a known Math/class call.
     *
     * @param isAssignment true when called from a re-assignment (not a decl),
     *                     so we emit "Runtime Error: Type mismatch" instead of
     *                     the generic "undeclared" semantic message.
     * @return true if at least one undeclared / uninitialized identifier was found
     */
    private boolean checkRhsForUndeclared(String rhs,
            Map<String, Boolean> inits,
            int lineNum, String raw,
            EvalResult res,
            boolean isAssignment) {

        Matcher rhsIdents = Pattern.compile("[A-Za-z_$][\\w$]*").matcher(rhs);
        boolean hadError = false;

        while (rhsIdents.find()) {
            String tok = rhsIdents.group();

            boolean isLiteral = tok.equals("true") || tok.equals("false") || tok.equals("null");
            boolean isDeclared = inits.containsKey(tok);
            boolean isCall = rhs.contains(tok + "(") || tok.equals("Math");

            if (!isLiteral && !isDeclared && !isCall) {
                // ── THIS is the line that was missing the Runtime / Type-mismatch path
                String msg = isAssignment
                        ? "Runtime Error: Type mismatch — '" + tok
                                + "' is undeclared or not a valid value for this variable"
                        : "Semantic Error: variable '" + tok + "' is undeclared";

                addError(res.errors, lineNum, raw, tok, msg);
                res.fatalError = true; // ← stop execution / output
                hadError = true;
            }
        }
        return hadError;
    }

    // ══════════════════════════════════════════════════════
    // IF / ELSE HANDLER
    // ══════════════════════════════════════════════════════

    /**
     * Handles a full if / else-if* / else chain starting at lines[ifLineIdx].
     * If res.fatalError is set, branches are *detected* but not *executed*.
     */
    private int handleIfElse(String[] lines, int ifLineIdx, int end,
            Map<String, Object> values,
            Map<String, Boolean> inits,
            EvalResult res) {

        int i = ifLineIdx;
        boolean anyBranchTaken = false;

        while (i < end) {
            String raw = lines[i].replace("\r", "");
            String trimmed = raw.trim();

            boolean isIf = trimmed.startsWith("if") && trimmed.length() > 2
                    && !Character.isLetterOrDigit(trimmed.charAt(2));
            boolean isElseIf = trimmed.startsWith("else")
                    && trimmed.replaceFirst("else\\s*", "").startsWith("if");
            boolean isElse = trimmed.equals("else") || trimmed.startsWith("else ")
                    || trimmed.startsWith("else{");

            if (!isIf && !isElseIf && !isElse)
                break;

            boolean condResult;
            String bodyStart;

            if (isIf || isElseIf) {
                String condLine = isElseIf ? trimmed.replaceFirst("else\\s*", "") : trimmed;
                int parenOpen = condLine.indexOf('(');
                if (parenOpen < 0) {
                    i++;
                    continue;
                }

                int depth = 0, parenClose = -1;
                for (int k = parenOpen; k < condLine.length(); k++) {
                    char c = condLine.charAt(k);
                    if (c == '(')
                        depth++;
                    else if (c == ')') {
                        if (--depth == 0) {
                            parenClose = k;
                            break;
                        }
                    }
                }
                if (parenClose < 0) {
                    i++;
                    continue;
                }

                String condExpr = condLine.substring(parenOpen + 1, parenClose).trim();

                // If a fatal error already occurred, don't evaluate the condition
                // (the variables it references may be poisoned / null).
                if (res.fatalError) {
                    condResult = false;
                } else {
                    condResult = evaluateCondition(condExpr, values, inits, i + 1, raw, res.errors);
                }
                bodyStart = condLine.substring(parenClose + 1).trim();
            } else {
                bodyStart = trimmed.replaceFirst("else", "").trim();
                condResult = true;
            }

            // Only actually execute the branch if there is no fatal error
            boolean execute = condResult && !anyBranchTaken && !res.fatalError;

            // ── Brace-delimited block ─────────────────────────────────────
            if (bodyStart.startsWith("{")) {
                i++;
                String afterBrace = bodyStart.substring(1).trim();
                if (afterBrace.endsWith("}")) {
                    if (execute) {
                        String inner = afterBrace.substring(0, afterBrace.length() - 1).trim();
                        if (!inner.isEmpty())
                            evaluateLinesIndexed(new String[] { inner }, 0, 1, values, inits, res);
                    }
                } else {
                    int braceDepth = 1;
                    List<Integer> blockLineIndices = new ArrayList<>();
                    while (i < end && braceDepth > 0) {
                        String bLine = lines[i].replace("\r", "").trim();
                        for (char c : bLine.toCharArray()) {
                            if (c == '{')
                                braceDepth++;
                            else if (c == '}')
                                braceDepth--;
                        }
                        if (braceDepth > 0)
                            blockLineIndices.add(i);
                        i++;
                    }
                    if (execute) {
                        String[] blockLines = blockLineIndices.stream()
                                .map(idx -> lines[idx]).toArray(String[]::new);
                        evaluateLinesIndexed(blockLines, 0, blockLines.length, values, inits, res);
                    }
                }
                if (execute)
                    anyBranchTaken = true;

            } else if (!bodyStart.isEmpty() && !bodyStart.equals("{")) {
                if (execute) {
                    evaluateLinesIndexed(new String[] { bodyStart }, 0, 1, values, inits, res);
                    anyBranchTaken = true;
                }
                i++;
            } else {
                i++;
                if (i < end) {
                    String nextTrimmed = lines[i].replace("\r", "").trim();
                    if (!nextTrimmed.startsWith("else") && !nextTrimmed.startsWith("if")) {
                        if (execute) {
                            evaluateLinesIndexed(new String[] { nextTrimmed }, 0, 1, values, inits, res);
                            anyBranchTaken = true;
                        }
                        i++;
                    }
                }
            }

            // Peek ahead for else / else-if
            int peek = i;
            while (peek < end && lines[peek].replace("\r", "").trim().isEmpty())
                peek++;
            if (peek < end && lines[peek].replace("\r", "").trim().startsWith("else")) {
                i = peek;
                continue;
            }
            break;
        }
        return i;
    }

    // ══════════════════════════════════════════════════════
    // CONDITION EVALUATOR
    // ══════════════════════════════════════════════════════

    private boolean evaluateCondition(String condExpr,
            Map<String, Object> values,
            Map<String, Boolean> inits,
            int lineNum, String raw,
            List<String> errors) {
        condExpr = condExpr.trim();

        String[][] ops = { { ">=", "<=" }, { "!=", "==" }, { ">", "<" } };
        for (String[] group : ops) {
            for (String op : group) {
                int opIdx = condExpr.indexOf(op);
                if (opIdx < 0)
                    continue;

                String lhsStr = condExpr.substring(0, opIdx).trim();
                String rhsStr = condExpr.substring(opIdx + op.length()).trim();

                Object lhsVal = parseExpression(lhsStr, values, inits, lineNum, raw, errors);
                Object rhsVal = parseExpression(rhsStr, values, inits, lineNum, raw, errors);

                // Either side null means a poisoned / undeclared variable — don't evaluate
                if (lhsVal == null || rhsVal == null)
                    return false;

                return compareValues(lhsVal, rhsVal, op);
            }
        }

        Object val = parseExpression(condExpr, values, inits, lineNum, raw, errors);
        if (val instanceof Boolean)
            return (Boolean) val;
        if (val instanceof Number)
            return ((Number) val).doubleValue() != 0;
        return false;
    }

    private boolean compareValues(Object a, Object b, String op) {
        if (a instanceof String || b instanceof String) {
            String sa = formatValue(a), sb = formatValue(b);
            return switch (op) {
                case "==" -> sa.equals(sb);
                case "!=" -> !sa.equals(sb);
                default -> false;
            };
        }
        double da = toDouble(a), db = toDouble(b);
        return switch (op) {
            case "==" -> da == db;
            case "!=" -> da != db;
            case ">=" -> da >= db;
            case "<=" -> da <= db;
            case ">" -> da > db;
            case "<" -> da < db;
            default -> false;
        };
    }

    // ══════════════════════════════════════════════════════
    // EXPRESSION EVALUATOR
    // ══════════════════════════════════════════════════════

    private Object parseExpression(String expr, Map<String, Object> values,
            Map<String, Boolean> inits, int lineNum,
            String raw, List<String> errors) {
        if (expr == null)
            return null;
        expr = expr.trim();
        if (expr.isEmpty())
            return "";
        try {
            ExprEval eval = new ExprEval(expr, values, inits, lineNum, raw, errors);
            return eval.parseExpr();
        } catch (UninitializedVarException e) {
            boolean alreadyReported = errors.stream()
                    .anyMatch(err -> err.contains("'" + e.varName + "'"));
            if (!alreadyReported) {
                addError(errors, lineNum, raw, e.varName,
                        "Semantic Error: variable '" + e.varName + "' might not have been initialized");
            }
            return null;
        } catch (Exception e) {
            errors.add("Runtime evaluation error: " + expr + "\n");
            return null;
        }
    }

    private static class UninitializedVarException extends RuntimeException {
        final String varName;

        UninitializedVarException(String name) {
            super(name);
            this.varName = name;
        }
    }

    private class ExprEval {
        private final String src;
        private final Map<String, Object> vars;
        private final Map<String, Boolean> inits;
        private final int lineNum;
        private final String raw;
        private final List<String> errors;
        private int pos;

        ExprEval(String src, Map<String, Object> vars, Map<String, Boolean> inits,
                int lineNum, String raw, List<String> errors) {
            this.src = src;
            this.vars = vars;
            this.inits = inits;
            this.lineNum = lineNum;
            this.raw = raw;
            this.errors = errors;
            this.pos = 0;
        }

        Object parseExpr() {
            Object left = parseMul();
            while (pos < src.length()) {
                skipWS();
                if (pos >= src.length())
                    break;
                char c = src.charAt(pos);
                if (c == '+') {
                    pos++;
                    Object right = parseMul();
                    if (isString(left) || isString(right))
                        left = formatValue(left) + formatValue(right);
                    else
                        left = addNum(left, right);
                } else if (c == '-') {
                    pos++;
                    left = subNum(left, parseMul());
                } else
                    break;
            }
            return left;
        }

        private Object parseMul() {
            Object left = parseUnary();
            while (pos < src.length()) {
                skipWS();
                if (pos >= src.length())
                    break;
                char c = src.charAt(pos);
                if (c == '*') {
                    pos++;
                    left = mulNum(left, parseUnary());
                } else if (c == '/') {
                    pos++;
                    left = divNum(left, parseUnary());
                } else if (c == '%') {
                    pos++;
                    left = modNum(left, parseUnary());
                } else
                    break;
            }
            return left;
        }

        private Object parseUnary() {
            skipWS();
            if (pos < src.length() && src.charAt(pos) == '-') {
                pos++;
                return negNum(parseAtom());
            }
            return parseAtom();
        }

        private Object parseAtom() {
            skipWS();
            if (pos >= src.length())
                return "";
            char c = src.charAt(pos);

            if (c == '(') {
                pos++;
                Object val = parseExpr();
                skipWS();
                if (pos < src.length() && src.charAt(pos) == ')')
                    pos++;
                return val;
            }

            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                pos++;
                while (pos < src.length() && src.charAt(pos) != '"') {
                    if (src.charAt(pos) == '\\' && pos + 1 < src.length()) {
                        char esc = src.charAt(pos + 1);
                        switch (esc) {
                            case 'n' -> sb.append('\n');
                            case 't' -> sb.append('\t');
                            case '"' -> sb.append('"');
                            default -> sb.append(esc);
                        }
                        pos += 2;
                    } else {
                        sb.append(src.charAt(pos++));
                    }
                }
                if (pos < src.length())
                    pos++;
                return sb.toString();
            }

            if (c == '\'') {
                pos++;
                char ch = pos < src.length() ? src.charAt(pos++) : ' ';
                if (pos < src.length() && src.charAt(pos) == '\'')
                    pos++;
                return String.valueOf(ch);
            }

            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = pos;
                while (pos < src.length() &&
                        (Character.isLetterOrDigit(src.charAt(pos))
                                || src.charAt(pos) == '_'
                                || src.charAt(pos) == '$'
                                || src.charAt(pos) == '.')) {
                    pos++;
                }
                String ident = src.substring(start, pos);

                if (ident.equals("true"))
                    return Boolean.TRUE;
                if (ident.equals("false"))
                    return Boolean.FALSE;
                if (ident.equals("null"))
                    return null;

                skipWS();
                if (pos < src.length() && src.charAt(pos) == '(') {
                    pos++;
                    List<Object> args = new ArrayList<>();
                    while (pos < src.length() && src.charAt(pos) != ')') {
                        skipWS();
                        if (pos < src.length() && src.charAt(pos) == ')')
                            break;
                        args.add(parseExpr());
                        skipWS();
                        if (pos < src.length() && src.charAt(pos) == ',')
                            pos++;
                    }
                    if (pos < src.length())
                        pos++;
                    return callMath(ident, args);
                }

                if (inits.containsKey(ident)) {
                    if (!inits.get(ident))
                        throw new UninitializedVarException(ident);
                    return vars.get(ident);
                }

                addError(errors, lineNum, raw, ident,
                        "Semantic Error: variable '" + ident + "' is undeclared");
                return null;
            }

            if (Character.isDigit(c)) {
                int start = pos;
                while (pos < src.length() &&
                        (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.'))
                    pos++;
                String numStr = src.substring(start, pos);
                if (pos < src.length() && (src.charAt(pos) == 'L' || src.charAt(pos) == 'l')) {
                    pos++;
                    return Long.parseLong(numStr);
                }
                if (numStr.contains("."))
                    return Double.parseDouble(numStr);
                return Long.parseLong(numStr);
            }

            pos++;
            return "";
        }

        private void skipWS() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos)))
                pos++;
        }

        private Object callMath(String name, List<Object> args) {
            double a = args.isEmpty() ? 0 : toDouble(args.get(0));
            double b = args.size() > 1 ? toDouble(args.get(1)) : 0;
            return switch (name) {
                case "Math.sqrt" -> Math.sqrt(a);
                case "Math.pow" -> Math.pow(a, b);
                case "Math.abs" -> Math.abs(a);
                case "Math.max" -> Math.max(a, b);
                case "Math.min" -> Math.min(a, b);
                case "Math.floor" -> Math.floor(a);
                case "Math.ceil" -> Math.ceil(a);
                case "Math.round" -> (double) Math.round(a);
                case "Math.log" -> Math.log(a);
                case "Math.log10" -> Math.log10(a);
                case "Math.sin" -> Math.sin(a);
                case "Math.cos" -> Math.cos(a);
                case "Math.tan" -> Math.tan(a);
                default -> throw new RuntimeException("Unknown function: " + name);
            };
        }
    }

    // ── Arithmetic helpers ───────────────────────────────────────────────────

    private boolean isString(Object v) {
        return v instanceof String;
    }

    private boolean isIntegral(Object v) {
        return v instanceof Long || v instanceof Integer;
    }

    private double toDouble(Object v) {
        if (v instanceof Number)
            return ((Number) v).doubleValue();
        if (v instanceof Boolean)
            return (Boolean) v ? 1 : 0;
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private Object addNum(Object a, Object b) {
        if (isIntegral(a) && isIntegral(b))
            return ((Number) a).longValue() + ((Number) b).longValue();
        return toDouble(a) + toDouble(b);
    }

    private Object subNum(Object a, Object b) {
        if (isIntegral(a) && isIntegral(b))
            return ((Number) a).longValue() - ((Number) b).longValue();
        return toDouble(a) - toDouble(b);
    }

    private Object mulNum(Object a, Object b) {
        if (isIntegral(a) && isIntegral(b))
            return ((Number) a).longValue() * ((Number) b).longValue();
        return toDouble(a) * toDouble(b);
    }

    private Object divNum(Object a, Object b) {
        double dv = toDouble(b);
        if (dv == 0)
            throw new ArithmeticException("/ by zero");
        if (isIntegral(a) && isIntegral(b))
            return ((Number) a).longValue() / ((Number) b).longValue();
        return toDouble(a) / dv;
    }

    private Object modNum(Object a, Object b) {
        if (isIntegral(a) && isIntegral(b))
            return ((Number) a).longValue() % ((Number) b).longValue();
        return toDouble(a) % toDouble(b);
    }

    private Object negNum(Object a) {
        if (isIntegral(a))
            return -((Number) a).longValue();
        return -toDouble(a);
    }

    private Object applyCompound(Object cur, String op, Object rhs) {
        return switch (op) {
            case "+=" -> isString(cur) ? formatValue(cur) + formatValue(rhs) : addNum(cur, rhs);
            case "-=" -> subNum(cur, rhs);
            case "*=" -> mulNum(cur, rhs);
            case "/=" -> divNum(cur, rhs);
            case "%=" -> modNum(cur, rhs);
            default -> rhs;
        };
    }

    private String formatValue(Object v) {
        if (v == null)
            return "null";
        if (v instanceof Long)
            return Long.toString((Long) v);
        if (v instanceof Integer)
            return Integer.toString((Integer) v);
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15)
                return Long.toString((long) d);
            String s = Double.toString(d);
            return s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        if (v instanceof Boolean)
            return v.toString();
        return v.toString();
    }

    private void addError(List<String> errors, int lineNum, String raw, String var, String msg) {
        int col = Math.max(0, raw.indexOf(var));
        StringBuilder err = new StringBuilder();
        err.append("Main.java:").append(lineNum).append(": error: ").append(msg).append("\n");
        err.append(raw).append("\n");
        err.append(" ".repeat(col)).append("^\n");
        errors.add(err.toString());
    }

    // ══════════════════════════════════════════════════════
    // COMPILE LOGIC
    // ══════════════════════════════════════════════════════

    private void compile() {
        SwingUtilities.invokeLater(this::clearOutput);

        String source = codeEditor.getText();
        if (source.trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> t("(no input)\n", TERM_DIM, false));
            return;
        }

        String[] sourceLines = source.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        List<String> customErrors = new ArrayList<>();

        // 1a. Class-present check
        if (!CLASS_PATTERN.matcher(source).find()) {
            customErrors.add("Main.java:1: error: Syntax Error: class, interface, or enum expected\n");
        }

        // 1b. main() signature check
        for (int i = 0; i < sourceLines.length; i++) {
            String line = sourceLines[i];
            if (line.contains("void main(")) {
                boolean hasArgs = line.contains("String[] args")
                        || line.contains("String args[]")
                        || line.contains("String... args");
                if (!hasArgs) {
                    int caretPos = Math.max(0, line.indexOf("main"));
                    StringBuilder err = new StringBuilder();
                    err.append("Main.java:").append(i + 1)
                            .append(": error: Semantic Error: main method must be defined as"
                                    + " 'public static void main(String[] args)'\n");
                    err.append(line).append("\n");
                    err.append(" ".repeat(caretPos)).append("^\n");
                    customErrors.add(err.toString());
                }
            }
        }

        // 1c. Semantic / runtime evaluator
        EvalResult semanticRes = evaluateSemantics(sourceLines);
        customErrors.addAll(semanticRes.errors);

        // 2. Backend syntax checker
        JavaCompiler.CompileResult result = compiler.compile(source);

        // 3. If the semantic evaluator handled things, ignore backend false-positives
        boolean semanticHandled = !semanticRes.output.isEmpty() || !semanticRes.errors.isEmpty();

        // Remove any blank entries that might have crept in
        customErrors.removeIf(String::isBlank);

        int totalErrors = customErrors.size() + (semanticHandled ? 0 : result.errors.size());

        if (totalErrors > 0) {
            SwingUtilities.invokeLater(() -> {
                codeEditor.setForeground(TERM_RED);
                t("ERROR!\n", TERM_RED, true);

                for (String customErr : customErrors) {
                    t(customErr, TERM_RED, false);
                }

                if (!semanticHandled && customErrors.isEmpty()) {
                    for (CompilerError err : result.errors) {
                        int lineIdx = err.getLine() - 1;
                        String sourceLine = (lineIdx >= 0 && lineIdx < sourceLines.length)
                                ? sourceLines[lineIdx]
                                : "";
                        t("Main.java:" + err.getLine() + ": error: ", TERM_RED, false);
                        t(err.getMessage() + "\n", TERM_RED, false);
                        t(sourceLine + "\n", TERM_RED, false);
                        int caretPos = (err.getColumn() > 0)
                                ? err.getColumn() - 1
                                : Math.max(0, sourceLine.length());
                        t(" ".repeat(caretPos) + "^\n", TERM_RED, false);
                    }
                    t("\n" + result.errors.size() + " error"
                            + (result.errors.size() != 1 ? "s" : "") + "\n", TERM_RED, false);
                    t("\n=== Code Exited With Errors ===\n", TERM_GREEN, false);
                    outputPane.setCaretPosition(0);
                } else if (!customErrors.isEmpty()) {
                    t("\n" + customErrors.size() + " error"
                            + (customErrors.size() != 1 ? "s" : "") + "\n", TERM_RED, false);
                    t("\n=== Code Exited With Errors ===\n", TERM_GREEN, false);
                    outputPane.setCaretPosition(0);
                }
            });
        } else {
            SwingUtilities.invokeLater(() -> {
                codeEditor.setForeground(TERM_FG);
                t("──────────────────────────────────────\n", new Color(0x3C3C3C), false);

                List<String> finalOutput = semanticRes.output.isEmpty()
                        ? result.output
                        : semanticRes.output;
                for (String line : finalOutput)
                    t(line, TERM_FG, false);

                t("\n──────────────────────────────────────\n", new Color(0x3C3C3C), false);
                t("Process finished with exit code 0\n", TERM_DIM, false);
                outputPane.setCaretPosition(0);
            });
        }
    }

    private void t(String text, Color color, boolean bold) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, color);
        StyleConstants.setFontFamily(a, "Monospaced");
        StyleConstants.setFontSize(a, 14);
        StyleConstants.setBold(a, bold);
        try {
            outDoc.insertString(outDoc.getLength(), text, a);
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    private void clearOutput() {
        try {
            outDoc.remove(0, outDoc.getLength());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }
}