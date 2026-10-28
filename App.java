import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
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
        addColumnIfMissing("category", "TEXT");

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
                String category = emptyToNull(form.get("category"));
                Integer progress = parseProgress(form.get("progress"));
                String nextAt = emptyToNull(form.get("nextAt"));
                if (!title.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO todos (title, done, progress, next_at, details, category) VALUES (?, 0, ?, ?, ?, ?)")) {
                        statement.setString(1, title);
                        setNullableInt(statement, 2, progress);
                        statement.setString(3, nextAt);
                        statement.setString(4, details);
                        statement.setString(5, category);
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
                String category = emptyToNull(form.get("category"));
                Integer progress = parseProgress(form.get("progress"));
                String nextAt = emptyToNull(form.get("nextAt"));
                try {
                    int id = Integer.parseInt(form.getOrDefault("id", ""));
                    if (id > 0 && !title.isEmpty() && (form.get("progress") == null || form.get("progress").isBlank()
                            || progress != null)) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE todos SET title = ?, details = ?, progress = ?, next_at = ?, category = ? WHERE id = ?")) {
                            statement.setString(1, title);
                            statement.setString(2, details);
                            setNullableInt(statement, 3, progress);
                            statement.setString(4, nextAt);
                            statement.setString(5, category);
                            statement.setInt(6, id);
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
            } else if (path.equals("/") || path.equals("/search")) {
                Map<String, String> query = parseForm(exchange.getRequestURI().getRawQuery() == null
                        ? "" : exchange.getRequestURI().getRawQuery());
                boolean searchMode = path.equals("/search");
                String searchQuery = query.getOrDefault("q", "").trim();
                String filter = query.getOrDefault("filter", "all");
                if (!filter.equals("pending") && !filter.equals("done") && !filter.equals("all")) {
                    filter = "all";
                }
                String selectedCategory = query.getOrDefault("category", "").trim();
                List<Todo> todos;
                try {
                    todos = selectTodos();
                } catch (SQLException e) {
                    throw new IOException(e);
                }
                List<String> categories = new ArrayList<>();
                for (Todo todo : todos) {
                    if (todo.getCategory() != null && !todo.getCategory().isBlank()
                            && !categories.contains(todo.getCategory())) {
                        categories.add(todo.getCategory());
                    }
                }
                StringBuilder html = new StringBuilder("<html><head><meta charset='UTF-8'><style>"
                        + "* { box-sizing: border-box; } body { max-width: 760px; margin: 32px auto; padding: 0 18px; font: 16px/1.5 system-ui, sans-serif; color: #243044; background: #f6f8fb; }"
                        + "h1 { color: #1e3a5f; } form.add-form, .todo-card { padding: 16px; border: 1px solid #d8e0eb; border-radius: 12px; background: #fff; box-shadow: 0 2px 8px #1f3b5710; }"
                        + "form.add-form { margin-bottom: 22px; } label { display: block; margin: 10px 0; font-weight: 600; color: #46566d; }"
                        + "input, textarea { margin: 4px 0; padding: 8px; border: 1px solid #cbd5e1; border-radius: 6px; font: inherit; } textarea { display: block; width: 100%; max-width: 480px; }"
                        + "ul { list-style: none; padding: 0; } .todo-card { margin: 14px 0; border-left: 6px solid #4f8fc0; }"
                        + "li.todo-card:nth-child(4n+2) { border-left-color: #55a887; } li.todo-card:nth-child(4n+3) { border-left-color: #e4a243; } li.todo-card:nth-child(4n+4) { border-left-color: #9a78c8; }"
                        + ".todo-title { margin-bottom: 8px; font-size: 1.2rem; font-weight: 700; color: #20344d; overflow-wrap: anywhere; } .todo-card.done { background: #f0f8f3; }"
                        + ".filters { margin: 18px 0 10px; } .filters a[aria-current='page'] { background: #286a9f; color: #fff; }"
                        + ".search-tools { display: flex; align-items: flex-end; flex-wrap: wrap; gap: 12px; margin: 12px 0; } .search-form { margin: 0; }"
                        + ".category-tabs { display: flex; align-items: center; flex-wrap: wrap; } .category-tabs a[aria-current='page'] { background: #286a9f; color: #fff; }"
                        + ".todo-section { margin: 10px 0; } .todo-section > summary { cursor: pointer; font-weight: 600; color: #254b70; }"
                        + ".todo-meta { margin: 8px 0; padding: 8px 10px; border-radius: 7px; background: #f2f6fa; color: #52647a; }"
                        + ".details { margin: 8px 0; padding: 8px 12px; background: #fff8e8; border-left: 3px solid #e4b64d; border-radius: 4px; white-space: pre-wrap; overflow-wrap: anywhere; }"
                        + "button, a { display: inline-block; margin: 5px 8px 2px 0; padding: 7px 11px; border: 0; border-radius: 6px; background: #e7eef7; color: #254b70; text-decoration: none; cursor: pointer; font: inherit; } button { background: #286a9f; color: #fff; }"
                        + "@media (max-width: 520px) { body { margin-top: 18px; } input { max-width: 100%; } }</style></head><body>"
                        + "<h1>わたしのTodo</h1><form class='add-form' method='post' action='/add'>"
                        + "<label>Todo <input name='todo' required></label> "
                        + "<label>詳細 <textarea name='details' rows='3' placeholder='タスクに必要なこと'></textarea></label> "
                        + "<label>カテゴリ <input name='category' placeholder='例: 買い物'></label> "
                        + "<label>進捗（%） <input name='progress' type='number' min='0' max='100' placeholder='未設定'></label> "
                        + "<label>次に行う日時 <input name='nextAt' type='datetime-local'></label> "
                        + "<button>追加</button></form>");
                html.append("<div class='search-tools'><form class='search-form' method='get' action='/search'>")
                        .append("<label>キーワード検索 <input name='q' value='")
                        .append(escapeHtml(searchQuery))
                        .append("' placeholder='例: 買う する（AND） / 買う OR する（OR）'></label>")
                        .append("<input type='hidden' name='filter' value='").append(filter).append("'>")
                        .append("<input type='hidden' name='category' value='").append(escapeHtml(selectedCategory)).append("'>")
                        .append("<button>検索</button></form>")
                        .append("<nav class='category-tabs' aria-label='カテゴリ検索'>")
                        .append(categoryLink("", "全カテゴリ", selectedCategory, searchQuery, filter));
                for (String category : categories) {
                    html.append(categoryLink(category, category, selectedCategory, searchQuery, filter));
                }
                html.append("</nav></div>");
                html.append("<nav class='filters' aria-label='Todoの表示切り替え'>")
                        .append(filterLink("pending", "未完了のみ", filter, searchMode ? searchQuery : null, selectedCategory))
                        .append(filterLink("done", "完了のみ", filter, searchMode ? searchQuery : null, selectedCategory))
                        .append(filterLink("all", "全て", filter, searchMode ? searchQuery : null, selectedCategory))
                        .append("</nav>");
                if (todos.isEmpty()) {
                    html.append("<p>やることは、いまゼロです</p>");
                } else {
                    List<Todo> visibleTodos = new ArrayList<>();
                    for (Todo todo : todos) {
                        if ((filter.equals("pending") && todo.isDone())
                                || (filter.equals("done") && !todo.isDone())
                                || (searchMode && !matchesSearch(todo, searchQuery))
                                || (!selectedCategory.isEmpty() && !selectedCategory.equals(todo.getCategory()))) {
                            continue;
                        }
                        visibleTodos.add(todo);
                    }
                    if (visibleTodos.isEmpty()) {
                        html.append("<p>該当するTodoはありません</p>");
                    } else {
                        html.append("<ul>");
                        for (Todo todo : visibleTodos) {
                            html.append("<li class='todo-card")
                                    .append(todo.isDone() ? " done" : "")
                                    .append("'><div class='todo-title'>").append(escapeHtml(todo.getTitle()));
                            if (todo.isDone()) {
                                html.append(" <span aria-label='完了'>✔ 完了</span>");
                            }
                            html.append("</div>")
                                    .append("<div class='todo-meta'>期限: ")
                                    .append(todo.getNextAt() == null ? "期限なし" : escapeHtml(todo.getNextAt().replace('T', ' ')))
                                    .append("</div>")
                                    .append("<details class='todo-section'><summary>詳細/進捗</summary>");
                            html.append("<div class='todo-meta'>カテゴリ: ")
                                    .append(todo.getCategory() == null ? "未設定" : escapeHtml(todo.getCategory()))
                                    .append("</div>");
                            if (todo.getDetails() != null) {
                                html.append("<div class='details'><strong>詳細</strong><br>")
                                        .append(escapeHtml(todo.getDetails()))
                                        .append("</div>");
                            }
                            html.append("<div class='todo-meta'>進捗: ")
                                    .append(todo.getProgress() == null ? "なし" : todo.getProgress() + "%")
                                    .append("</div></details>")
                                    .append("<details class='todo-section'><summary>編集</summary>")
                                    .append("<form method='post' action='/update'>")
                                    .append("<input type='hidden' name='id' value='").append(todo.getId()).append("'>")
                                    .append("<label>Todo <input name='todo' required value='")
                                    .append(escapeHtml(todo.getTitle())).append("'></label> ")
                                    .append("<label>詳細 <textarea name='details' rows='3'>")
                                    .append(todo.getDetails() == null ? "" : escapeHtml(todo.getDetails()))
                                    .append("</textarea></label> ")
                                    .append("<label>カテゴリ <input name='category' value='")
                                    .append(todo.getCategory() == null ? "" : escapeHtml(todo.getCategory()))
                                    .append("' placeholder='例: 買い物'></label> ")
                                    .append("<label>進捗（%） <input name='progress' type='number' min='0' max='100' placeholder='未設定' value='")
                                    .append(todo.getProgress() == null ? "" : todo.getProgress()).append("'></label> ")
                                    .append("<label>次に行う日時 <input name='nextAt' type='datetime-local' value='")
                                    .append(todo.getNextAt() == null ? "" : escapeHtml(todo.getNextAt())).append("'></label> ")
                                    .append("<button>変更を保存</button></form></details>")
                                    .append("<a href='/done?id=").append(todo.getId()).append("'>完了</a> ")
                                    .append("<a href='/delete?id=").append(todo.getId()).append("'>削除</a></li>");
                        }
                        html.append("</ul>");
                    }
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

    static String filterLink(String value, String label, String selectedFilter, String searchQuery, String category) {
        String path = searchQuery == null ? "/?filter=" + value
                : "/search?q=" + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8)
                        + "&category=" + URLEncoder.encode(category, StandardCharsets.UTF_8) + "&filter=" + value;
        return "<a href='" + path + "'"
                + (value.equals(selectedFilter) ? " aria-current='page'" : "")
                + ">" + label + "</a> ";
    }

    static String categoryLink(String category, String label, String selectedCategory, String searchQuery, String filter) {
        String path = "/search?q=" + URLEncoder.encode(searchQuery, StandardCharsets.UTF_8)
                + "&category=" + URLEncoder.encode(category, StandardCharsets.UTF_8)
                + "&filter=" + filter;
        return "<a href='" + path + "'"
                + (category.equals(selectedCategory) ? " aria-current='page'" : "")
                + ">" + escapeHtml(label) + "</a> ";
    }

    static boolean matchesSearch(Todo todo, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String searchableText = (todo.getTitle() + " " + (todo.getDetails() == null ? "" : todo.getDetails()))
                .toLowerCase(java.util.Locale.ROOT);
        List<List<String>> orGroups = new ArrayList<>();
        List<String> andTerms = new ArrayList<>();
        for (String token : query.trim().split("\\s+")) {
            if (token.equalsIgnoreCase("OR")) {
                if (!andTerms.isEmpty()) {
                    orGroups.add(andTerms);
                    andTerms = new ArrayList<>();
                }
            } else if (!token.equalsIgnoreCase("AND")) {
                andTerms.add(token.toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (!andTerms.isEmpty()) {
            orGroups.add(andTerms);
        }
        if (orGroups.isEmpty()) {
            return true;
        }

        for (List<String> group : orGroups) {
            boolean allTermsMatch = true;
            for (String term : group) {
                if (!searchableText.contains(term)) {
                    allTermsMatch = false;
                    break;
                }
            }
            if (allTermsMatch) {
                return true;
            }
        }
        return false;
    }

    static List<Todo> selectTodos() throws SQLException {
        List<Todo> todos = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, title, done, progress, next_at, details, category FROM todos "
                        + "ORDER BY done ASC, CASE WHEN next_at IS NULL OR next_at = '' THEN 1 ELSE 0 END ASC, next_at ASC, id ASC");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Todo todo = new Todo(result.getInt("id"), result.getString("title"));
                todo.setDone(result.getInt("done") != 0);
                int progress = result.getInt("progress");
                todo.setProgress(result.wasNull() ? null : progress);
                todo.setNextAt(result.getString("next_at"));
                todo.setDetails(result.getString("details"));
                todo.setCategory(result.getString("category"));
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
    private String category;

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
    String getCategory() { return category; }
    void setCategory(String category) { this.category = category; }
}
