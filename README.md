# Java Mini-Compiler

A mini Java compiler with a graphical UI that analyzes Java source code
and reports **syntax errors**, **semantic errors**, or shows the **simulated program output**.

## How to Run

### Windows
Double-click `run.bat`, or in a terminal:
```
run.bat
```

### Linux / macOS
```bash
chmod +x run.sh
./run.sh
```

### Manual (any OS)
```bash
java -cp "bin:lib/*" Main       # Linux/macOS
java -cp "bin;lib/*" Main       # Windows
```

> Requires **Java 21** or later.

---

## Features

| Feature | Details |
|---|---|
| **Syntax errors** | Bracket/brace mismatch, missing semicolons, unterminated strings/chars, malformed declarations, bad control-flow syntax |
| **Semantic errors** | Undeclared variables, duplicate declarations, type mismatches (int/String/boolean), void misuse, missing return values |
| **Output simulation** | Extracts `System.out.println` / `System.out.print` calls and shows their output |
| **Line numbers** | Every error card shows the exact line that caused the problem |
| **Error type badge** | Each card is tagged **SYNTAX** (red) or **SEMANTIC** (orange) |

---

## UI Layout

```
┌──────────────────── Java Mini-Compiler ────────────────────┐
│  HEADER                                                     │
├────────────────────┬────────────────────────────────────────┤
│  LEFT              │  RIGHT                                 │
│  Code editor       │  ✓ Output block  (if no errors)        │
│  (line numbers)    │  ✗ Error cards   (if errors found)     │
│  [Clear] [Compile] │                                        │
└────────────────────┴────────────────────────────────────────┘
```

---

## Project Structure

```
Java_Compiler_Project/
├── src/
│   ├── Main.java           – entry point
│   ├── CompilerUI.java     – Swing GUI
│   ├── JavaCompiler.java   – 3-pass compiler engine
│   └── CompilerError.java  – error model (type, line, message)
├── bin/                    – compiled .class files
├── lib/                    – Jackson JSON jars (inherited)
├── util/fonts/             – Inter & Montserrat fonts
├── run.bat                 – Windows launcher
└── run.sh                  – Linux/macOS launcher
```
