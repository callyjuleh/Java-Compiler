import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

public class CompilerError {

    public enum ErrorType {
        SYNTAX,
        SEMANTIC,
        RUNTIME,
        WARNING,
        INTERNAL
    }

    private static final int MIN_LINE = 1;
    private static final int MIN_COLUMN = 1;
    private static final int MAX_MSG_LEN = 1000;

    private final ErrorType type;
    private final String fileName;
    private final int line;
    private final int column;
    private final String message;

    public CompilerError(ErrorType type, String fileName, int line, int column, String message) {
        List<String> violations = new ArrayList<>();

        if (type == null) {
            violations.add("ErrorType must not be null");
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            violations.add("File name must not be null or blank");
        }

        if (line < MIN_LINE && line != -1) {
            violations.add("Line number must be >= " + MIN_LINE + " or -1 for global errors, got: " + line);
        }

        if (column < MIN_COLUMN && column != -1) {
            violations.add("Column number must be >= " + MIN_COLUMN + " or -1, got: " + column);
        }

        if (message == null) {
            violations.add("Error message must not be null");
        } else {
            String trimmed = message.trim();
            if (trimmed.isEmpty()) {
                violations.add("Error message must not be blank");
            } else if (trimmed.length() > MAX_MSG_LEN) {
                violations.add("Error message exceeds max length of " + MAX_MSG_LEN
                        + " characters, got: " + trimmed.length());
            }
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("CompilerError created with invalid arguments:\n");
            for (int i = 0; i < violations.size(); i++) {
                sb.append("  [").append(i + 1).append("] ").append(violations.get(i)).append("\n");
            }
            throw new IllegalArgumentException(sb.toString().trim());
        }

        this.type = type;
        this.fileName = fileName.trim();
        this.line = line;
        this.column = column;
        this.message = message.trim();
    }

    public CompilerError(ErrorType type, int line, String message) {
        this(type, "Main.java", line, -1, message);
    }

    public ErrorType getType() {
        return type;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        String location = (line == -1) ? "Global" : "Line " + line + (column != -1 ? ":" + column : "");
        return String.format("[%s] %s (%s): %s", type, fileName, location, message);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof CompilerError))
            return false;
        CompilerError that = (CompilerError) o;
        return line == that.line &&
                column == that.column &&
                type == that.type &&
                fileName.equals(that.fileName) &&
                message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, fileName, line, column, message);
    }
}