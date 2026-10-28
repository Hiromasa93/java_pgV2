import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App {
    static Connection connection;

    public static void main(String[] args) throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite:todos.db");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY, title TEXT, done INTEGER)");
        }
        addColumnIfMissing("progress", "INTEGER");
        addColumnIfMissing("next_at", "TEXT");
        addColumnIfMissing("details", "TEXT");

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");

            if (path.equals("/add") && method.equals("POST")) {
                Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String title = form.getOrDefault("todo", "").trim();
                String details = emptyToNull(form.get("details"));
                Integer progress = parseProgress(form.get("progress"));
                String nextAt = emptyToNull(form.get("nextAt"));
                if (!title.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO todos (title, done, progress, next_at, details) VALUES (?, 0, ?, ?, ?)")) {
                        statement.setString(1, title);
                        setNullableInt(statement, 2, progress);
                        statement.setString(3, nextAt);
                        statement.setString(4, details);
                        statement.executeUpdate();
                    } catch (SQLException e) {
                        throw new IOException(e);
                    }
                }
                redirectHome(exchange);
                return;
            } else if (path.equals("/update") && method.equals("POST")) {
                Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String title = form.getOrDefault("todo", "").trim();
                String details = emptyToNull(form.get("details"));
                Integer progress = parseProgress(form.get("progress"));
                String nextAt = emptyToNull(form.get("nextAt"));
                try {
                    int id = Integer.parseInt(form.getOrDefault("id", ""));
                    if (id > 0 && !title.isEmpty() && (form.get("progress") == null || form.get("progress").isBlank()
                            || progress != null)) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE todos SET title = ?, details = ?, progress = ?, next_at = ? WHERE id = ?")) {
                            statement.setString(1, title);
                            statement.setString(2, details);
                            setNullableInt(statement, 3, progress);
                            statement.setString(4, nextAt);
                            statement.setInt(5, id);
                            statement.executeUpdate();
                        }
                    }
                } catch (NumberFormatException e) {
                    // 不正なidは変更しません。
                } catch (SQLException e) {
                    throw new IOException(e);
                }
                redirectHome(exchange);
                return;
            } else if (path.equals("/done") && method.equals("GET")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("id=") && query.length() > 3) {
                    try {
                        int id = Integer.parseInt(query.substring(3));
                        try (PreparedStatement statement = connection.prepareStatement("UPDATE todos SET done = 1 WHERE id = ?")) {
                            statement.setInt(1, id);
                            statement.executeUpdate();
                        }
                    } catch (NumberFormatException e) {
                        // 数字でないidは無視します。
                    } catch (SQLException e) {
                        throw new IOException(e);
                    }
                }
                redirectHome(exchange);
                return;
            } else if (path.equals("/delete") && method.equals("GET")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("id=") && query.length() > 3) {
                    try {
                        int id = Integer.parseInt(query.substring(3));
                        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM todos WHERE id = ?")) {
                            statement.setInt(1, id);
                            statement.executeUpdate();
                        }
                    } catch (NumberFormatException e) {
                        // 数字でないidは無視します。
                    } catch (SQLException e) {
                        throw new IOException(e);
                    }
                }
                redirectHome(exchange);
                return;
            } else if (path.equals("/")) {
                StringBuilder html = new StringBuilder("<html><head><meta charset='UTF-8'><style>"
                        + "* { box-sizing: border-box; } body { max-width: 760px; margin: 32px auto; padding: 0 18px; font: 16px/1.5 system-ui, sans-serif; color: #243044; background: #f6f8fb; }"
                        + "h1 { color: #1e3a5f; } form.add-form, .todo-card { padding: 16px; border: 1px solid #d8e0eb; border-radius: 12px; background: #fff; box-shadow: 0 2px 8px #1f3b5710; }"
                        + "form.add-form { margin-bottom: 22px; } label { display: block; margin: 10px 0; font-weight: 600; color: #46566d; }"
                        + "input, textarea { margin: 4px 0; padding: 8px; border: 1px solid #cbd5e1; border-radius: 6px; font: inherit; } textarea { display: block; width: 100%; max-width: 480px; }"
                        + "ul { list-style: none; padding: 0; } .todo-card { margin: 14px 0; border-left: 6px solid #4f8fc0; }"
                        + "li.todo-card:nth-child(4n+2) { border-left-color: #55a887; } li.todo-card:nth-child(4n+3) { border-left-color: #e4a243; } li.todo-card:nth-child(4n+4) { border-left-color: #9a78c8; }"
                        + ".todo-title { margin-bottom: 8px; font-size: 1.2rem; font-weight: 700; color: #20344d; overflow-wrap: anywhere; } .todo-card.done { background: #f0f8f3; }"
                        + ".todo-meta { margin: 8px 0; padding: 8px 10px; border-radius: 7px; background: #f2f6fa; color: #52647a; }"
                        + ".details { margin: 8px 0; padding: 8px 12px; background: #fff8e8; border-left: 3px solid #e4b64d; border-radius: 4px; white-space: pre-wrap; overflow-wrap: anywhere; }"
                        + "button, a { display: inline-block; margin: 5px 8px 2px 0; padding: 7px 11px; border: 0; border-radius: 6px; background: #e7eef7; color: #254b70; text-decoration: none; cursor: pointer; font: inherit; } button { background: #286a9f; color: #fff; }"
                        + "@media (max-width: 520px) { body { margin-top: 18px; } input { max-width: 100%; } }</style></head><body>"
                        + "<h1>わたしのTodo</h1><form class='add-form' method='post' action='/add'>"
                        + "<label>Todo <input name='todo' required></label> "
                        + "<label>詳細 <textarea name='details' rows='3' placeholder='タスクに必要なこと'></textarea></label> "
                        + "<label>進捗（%） <input name='progress' type='number' min='0' max='100' placeholder='未設定'></label> "
                        + "<label>次に行う日時 <input name='nextAt' type='datetime-local'></label> "
                        + "<button>追加</button></form>");
                try {
                    List<Todo> todos = selectTodos();
                    if (todos.isEmpty()) {
                        html.append("<p>やることは、いまゼロです</p>");
                    } else {
                        html.append("<ul>");
                        for (Todo todo : todos) {
                            html.append("<li class='todo-card")
                                    .append(todo.isDone() ? " done" : "")
                                    .append("'><div class='todo-title'>").append(escapeHtml(todo.getTitle()));
                            if (todo.isDone()) {
                                html.append(" <span aria-label='完了'>✔ 完了</span>");
                            }
                            html.append("</div>");
                            if (todo.getDetails() != null) {
                                html.append("<div class='details'><strong>詳細</strong><br>")
                                        .append(escapeHtml(todo.getDetails()))
                                        .append("</div>");
                            }
                            html.append("<div class='todo-meta'>進捗: ")
                                    .append(todo.getProgress() == null ? "なし" : todo.getProgress() + "%")
                                    .append(" / 次回: ")
                                    .append(todo.getNextAt() == null ? "期限なし" : escapeHtml(todo.getNextAt().replace('T', ' ')))
                                    .append("</div>")
                                    .append("<form method='post' action='/update'>")
                                    .append("<input type='hidden' name='id' value='").append(todo.getId()).append("'>")
                                    .append("<label>Todo <input name='todo' required value='")
                                    .append(escapeHtml(todo.getTitle())).append("'></label> ")
                                    .append("<label>詳細 <textarea name='details' rows='3'>")
                                    .append(todo.getDetails() == null ? "" : escapeHtml(todo.getDetails()))
                                    .append("</textarea></label> ")
                                    .append("<label>進捗（%） <input name='progress' type='number' min='0' max='100' placeholder='未設定' value='")
                                    .append(todo.getProgress() == null ? "" : todo.getProgress()).append("'></label> ")
                                    .append("<label>次に行う日時 <input name='nextAt' type='datetime-local' value='")
                                    .append(todo.getNextAt() == null ? "" : escapeHtml(todo.getNextAt())).append("'></label> ")
                                    .append("<button>変更を保存</button></form>")
                                    .append("<a href='/done?id=").append(todo.getId()).append("'>完了</a> ")
                                    .append("<a href='/delete?id=").append(todo.getId()).append("'>削除</a></li>");
                        }
                        html.append("</ul>");
                    }
                } catch (SQLException e) {
                    throw new IOException(e);
                }
                html.append("</body></html>");
                message = html.toString();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            } else {
                message = "ページが見つかりません";
            }
            byte[] responseBody = message.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.getResponseBody().close();
        });

        server.createContext("/api/todos", exchange -> {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            try {
                byte[] responseBody = todosToJson().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBody.length);
                exchange.getResponseBody().write(responseBody);
                exchange.getResponseBody().close();
            } catch (SQLException e) {
                throw new IOException(e);
            }
        });
        server.start();
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）");
    }

    static void addColumnIfMissing(String column, String type) throws SQLException {
        boolean found = false;
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("PRAGMA table_info(todos)")) {
            while (columns.next()) {
                if (column.equals(columns.getString("name"))) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE todos ADD COLUMN " + column + " " + type);
            }
        }
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    static Integer parseProgress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int progress = Integer.parseInt(value);
            return progress >= 0 && progress <= 100 ? progress : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    static void redirectHome(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Location", "/");
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    static String todosToJson() throws SQLException {
        List<Todo> todos = selectTodos();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < todos.size(); i++) {
            Todo todo = todos.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"title\":\"").append(escapeJson(todo.getTitle()))
                    .append("\",\"done\":").append(todo.isDone())
                    .append(",\"progress\":").append(todo.getProgress() == null ? "null" : todo.getProgress())
                    .append(",\"nextAt\":");
            if (todo.getNextAt() == null) {
                json.append("null");
            } else {
                json.append('"').append(escapeJson(todo.getNextAt())).append('"');
            }
            json.append('}');
        }
        return json.append(']').toString();
    }

    static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"': escaped.append("\\\""); break;
                case '\\': escaped.append("\\\\"); break;
                case '\b': escaped.append("\\b"); break;
                case '\f': escaped.append("\\f"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
            }
        }
        return escaped.toString();
    }

    static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    static List<Todo> selectTodos() throws SQLException {
        List<Todo> todos = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, title, done, progress, next_at, details FROM todos "
                        + "ORDER BY done ASC, CASE WHEN next_at IS NULL OR next_at = '' THEN 1 ELSE 0 END ASC, next_at ASC, id ASC");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Todo todo = new Todo(result.getInt("id"), result.getString("title"));
                todo.setDone(result.getInt("done") != 0);
                int progress = result.getInt("progress");
                todo.setProgress(result.wasNull() ? null : progress);
                todo.setNextAt(result.getString("next_at"));
                todo.setDetails(result.getString("details"));
                todos.add(todo);
            }
        }
        return todos;
    }
}

class Todo {
    private final int id;
    private final String title;
    private boolean done;
    private Integer progress;
    private String nextAt;
    private String details;

    Todo(int id, String title) {
        this.id = id;
        this.title = title;
        this.done = false;
    }

    int getId() { return id; }
    String getTitle() { return title; }
    boolean isDone() { return done; }
    void setDone(boolean done) { this.done = done; }
    Integer getProgress() { return progress; }
    void setProgress(Integer progress) { this.progress = progress; }
    String getNextAt() { return nextAt; }
    void setNextAt(String nextAt) { this.nextAt = nextAt; }
    String getDetails() { return details; }
    void setDetails(String details) { this.details = details; }
}
