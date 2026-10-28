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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
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
        addColumnIfMissing("repeat_type", "TEXT");
        addColumnIfMissing("repeat_day", "INTEGER");
        addColumnIfMissing("repeat_days", "TEXT");
        addColumnIfMissing("repeat_time", "TEXT");
        addColumnIfMissing("monthly_day", "INTEGER");
        addColumnIfMissing("monthly_time", "TEXT");
        migrateRepeatSettings();

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
                String repeatType = parseRepeatType(form.get("repeatType"));
                String repeatDays = parseRepeatDays(form.get("repeatDays"), repeatType);
                String repeatTime = parseRepeatTime(form.get("repeatTime"));
                Integer monthlyDay = parseMonthlyDay(form.get("monthlyDay"), nextAt);
                String monthlyTime = parseRepeatTime(form.get("monthlyTime"));
                if (!title.isEmpty()) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO todos (title, done, progress, next_at, details, category, repeat_type, repeat_days, repeat_time, monthly_day, monthly_time) VALUES (?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        statement.setString(1, title);
                        setNullableInt(statement, 2, progress);
                        statement.setString(3, nextAt);
                        statement.setString(4, details);
                        statement.setString(5, category);
                        statement.setString(6, repeatType);
                        statement.setString(7, repeatType == null ? null : repeatDays);
                        statement.setString(8, "daily".equals(repeatType) ? repeatTime : null);
                        setNullableInt(statement, 9, "monthly".equals(repeatType) ? monthlyDay : null);
                        statement.setString(10, "monthly".equals(repeatType) ? monthlyTime : null);
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
                String repeatType = parseRepeatType(form.get("repeatType"));
                String repeatDays = parseRepeatDays(form.get("repeatDays"), repeatType);
                String repeatTime = parseRepeatTime(form.get("repeatTime"));
                Integer monthlyDay = parseMonthlyDay(form.get("monthlyDay"), nextAt);
                String monthlyTime = parseRepeatTime(form.get("monthlyTime"));
                try {
                    int id = Integer.parseInt(form.getOrDefault("id", ""));
                    if (id > 0 && !title.isEmpty() && (form.get("progress") == null || form.get("progress").isBlank()
                            || progress != null)) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE todos SET title = ?, details = ?, progress = ?, next_at = ?, category = ?, repeat_type = ?, repeat_days = ?, repeat_time = ?, monthly_day = ?, monthly_time = ? WHERE id = ?")) {
                            statement.setString(1, title);
                            statement.setString(2, details);
                            setNullableInt(statement, 3, progress);
                            statement.setString(4, nextAt);
                            statement.setString(5, category);
                            statement.setString(6, repeatType);
                            statement.setString(7, repeatType == null ? null : repeatDays);
                            statement.setString(8, "daily".equals(repeatType) ? repeatTime : null);
                            setNullableInt(statement, 9, "monthly".equals(repeatType) ? monthlyDay : null);
                            statement.setString(10, "monthly".equals(repeatType) ? monthlyTime : null);
                            statement.setInt(11, id);
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
                        completeTodo(id);
                    } catch (NumberFormatException e) {
                        // 数字でないidは無視します。
                    } catch (SQLException e) {
                        throw new IOException(e);
                    }
                }
                redirectHome(exchange);
                return;
            } else if (path.equals("/undo") && method.equals("GET")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("id=") && query.length() > 3) {
                    try {
                        int id = Integer.parseInt(query.substring(3));
                        try (PreparedStatement statement = connection.prepareStatement("UPDATE todos SET done = 0 WHERE id = ?")) {
                            statement.setInt(1, id);
                            statement.executeUpdate();
                        }
                    } catch (NumberFormatException ignored) {
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
            } else if ((path.equals("/bulk-done") || path.equals("/bulk-delete")) && method.equals("POST")) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                List<Integer> ids = parseIds(body);
                if (!ids.isEmpty()) {
                    try {
                        if (path.equals("/bulk-done")) {
                            for (int id : ids) completeTodo(id);
                        } else {
                            String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
                            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM todos WHERE id IN (" + placeholders + ")")) {
                                for (int i = 0; i < ids.size(); i++) statement.setInt(i + 1, ids.get(i));
                                statement.executeUpdate();
                            }
                        }
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
                        + ".bulk-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin: 14px 0 4px; } .bulk-tools button[hidden] { display: none; }"
                        + ".select-task { display: none; margin-right: 8px; accent-color: #286a9f; transform: scale(1.15); } body.selecting .select-task { display: inline-block; }"
                        + ".todo-card.selected { background: #dce9f5; border-color: #286a9f; box-shadow: 0 3px 12px #1f3b5730; } .todo-card.done.selected { background: #d8ece0; }"
                        + ".deadline-warning { color: #c62828; font-weight: 700; }"
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
                        + repeatControls(null, null, "09:00", null, "09:00")
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
                html.append("<form id='bulk-form' class='bulk-tools' method='post'>")
                        .append("<button id='select-mode' type='button' aria-pressed='false'>複数選択</button>")
                        .append("<button type='submit' formaction='/bulk-done' name='action' value='done' data-confirm='選択したタスクを完了します。よろしいですか？' hidden disabled>選択した項目を完了にする</button>")
                        .append("<button type='submit' formaction='/bulk-delete' name='action' value='delete' data-confirm='選択したタスクを削除します。よろしいですか？' hidden disabled>選択した項目を削除する</button>")
                        .append("<span id='selection-count' aria-live='polite'></span></form>");
                html.append("<dialog id='bulk-confirm' aria-labelledby='bulk-confirm-message'><p id='bulk-confirm-message'></p><button id='bulk-confirm-yes' type='button'>はい</button><button id='bulk-confirm-no' type='button'>いいえ</button></dialog>");
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
                                    .append("'><label class='select-label'><input class='select-task' type='checkbox' form='bulk-form' name='id' value='")
                                    .append(todo.getId()).append("' aria-label='").append(escapeHtml(todo.getTitle())).append("を選択'></label><div class='todo-title'>").append(escapeHtml(todo.getTitle()));
                            if (todo.isDone()) {
                                html.append(" <span aria-label='完了'>✔ 完了</span>");
                            }
                            html.append("</div><div class='todo-meta'>期限: ");
                            if (todo.getNextAt() == null || todo.getNextAt().isBlank()) {
                                html.append("期限なし");
                            } else {
                                String deadlineWarning = todo.isDone() ? null : getDeadlineWarning(todo.getNextAt());
                                html.append(deadlineWarning != null ? "<span class='deadline-warning'>" : "<span>")
                                        .append(escapeHtml(todo.getNextAt().replace('T', ' ')))
                                        .append("</span>");
                                if (deadlineWarning != null) {
                                    html.append(" <span class='deadline-warning'>")
                                            .append(deadlineWarning)
                                            .append("</span>");
                                }
                            }
                            html.append("</div><details class='todo-section'><summary>詳細/進捗</summary>");
                            html.append("<div class='todo-meta'>\u7E70\u308A\u8FD4\u3057: ").append(repeatLabel(todo)).append("</div>");
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
                                    .append(repeatControls(todo.getRepeatType(), todo.getRepeatDays(), todo.getRepeatTime(), todo.getMonthlyDay(), todo.getMonthlyTime()))
                                    .append("<button>変更を保存</button></form></details>")
                                    .append(todoActions(todo));
                        }
                        html.append("</ul>");
                    }
                }
                html.append("<script>(function(){const form=document.getElementById('bulk-form');const toggle=document.getElementById('select-mode');const boxes=Array.from(document.querySelectorAll('.select-task'));const actions=Array.from(document.querySelectorAll('.bulk-tools button[type=submit]'));const count=document.getElementById('selection-count');const dialog=document.getElementById('bulk-confirm');let pendingButton=null;let confirmed=false;function refresh(){const selected=boxes.filter(box=>box.checked);const active=document.body.classList.contains('selecting');selected.forEach(box=>box.closest('.todo-card').classList.add('selected'));boxes.filter(box=>!box.checked).forEach(box=>box.closest('.todo-card').classList.remove('selected'));actions.forEach(button=>{button.disabled=selected.length===0;button.hidden=!active;});count.textContent=selected.length?' '+selected.length+'件選択中':'';}toggle.addEventListener('click',function(){const active=document.body.classList.toggle('selecting');toggle.setAttribute('aria-pressed',String(active));toggle.textContent=active?'選択を解除':'複数選択';if(!active){boxes.forEach(box=>box.checked=false);}refresh();});boxes.forEach(box=>box.addEventListener('change',refresh));form.addEventListener('submit',function(event){if(confirmed){confirmed=false;return;}event.preventDefault();pendingButton=event.submitter;if(pendingButton){document.getElementById('bulk-confirm-message').textContent=pendingButton.dataset.confirm;dialog.showModal();}});document.getElementById('bulk-confirm-yes').addEventListener('click',function(){if(pendingButton){const button=pendingButton;pendingButton=null;confirmed=true;dialog.close();form.requestSubmit(button);}});document.getElementById('bulk-confirm-no').addEventListener('click',function(){pendingButton=null;dialog.close();});refresh();})();</script></body></html>");
                html.append("<script>document.querySelectorAll('.repeat-type').forEach(function(select){const form=select.closest('form');function updateRepeatFields(){form.querySelectorAll('.repeat-fields').forEach(function(field){field.hidden=!field.classList.contains(select.value+'-fields');});}select.addEventListener('change',updateRepeatFields);updateRepeatFields();});</script>");
                html.append("<script>let pendingLink=null;document.querySelectorAll(\"a[data-confirm]\").forEach(function(link){link.addEventListener(\"click\",function(event){event.preventDefault();pendingLink=link;document.getElementById(\"bulk-confirm-message\").textContent=link.dataset.confirm;document.getElementById(\"bulk-confirm\").showModal();});});document.getElementById(\"bulk-confirm-yes\").addEventListener(\"click\",function(){if(pendingLink){const link=pendingLink;pendingLink=null;location.href=link.href;}});document.getElementById(\"bulk-confirm-no\").addEventListener(\"click\",function(){pendingLink=null;});</script>");
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

    static void migrateRepeatSettings() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE todos SET repeat_days = CAST(repeat_day AS TEXT) WHERE repeat_type = 'weekly' AND repeat_days IS NULL AND repeat_day IS NOT NULL");
            statement.executeUpdate("UPDATE todos SET repeat_time = substr(next_at, 12, 5) WHERE repeat_type = 'daily' AND repeat_time IS NULL AND length(next_at) >= 16");
            statement.executeUpdate("UPDATE todos SET monthly_day = CAST(substr(next_at, 9, 2) AS INTEGER) WHERE repeat_type = 'monthly' AND monthly_day IS NULL AND length(next_at) >= 10");
            statement.executeUpdate("UPDATE todos SET monthly_time = substr(next_at, 12, 5) WHERE repeat_type = 'monthly' AND monthly_time IS NULL AND length(next_at) >= 16");
        }
    }

    static Map<String, String> parseForm(String body) {
        Map<String, String> values = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            if (key.equals("repeatDays") && values.containsKey(key)) {
                values.put(key, values.get(key) + "," + value);
            } else {
                values.put(key, value);
            }
        }
        return values;
    }

    static List<Integer> parseIds(String body) {
        List<Integer> ids = new ArrayList<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            if (!URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals("id") || parts.length < 2) {
                continue;
            }
            try {
                int id = Integer.parseInt(URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                if (id > 0 && !ids.contains(id)) {
                    ids.add(id);
                }
            } catch (NumberFormatException e) {
                // 数字でないidは無視します。
            }
        }
        return ids;
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

    static String parseRepeatType(String value) {
        return "daily".equals(value) || "weekly".equals(value) || "monthly".equals(value) ? value : null;
    }

    static String parseRepeatDays(String value, String repeatType) {
        if (!"weekly".equals(repeatType)) return null;
        boolean[] selected = new boolean[8];
        if (value != null) {
            for (String part : value.split(",")) {
                try {
                    int day = Integer.parseInt(part);
                    if (day >= 1 && day <= 7) selected[day] = true;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        StringBuilder result = new StringBuilder();
        for (int day = 1; day <= 7; day++) {
            if (selected[day]) {
                if (result.length() > 0) result.append(',');
                result.append(day);
            }
        }
        return result.length() == 0 ? "1" : result.toString();
    }

    static String parseRepeatTime(String value) {
        return value != null && value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d") ? value : "09:00";
    }

    static Integer parseMonthlyDay(String value, String nextAt) {
        try {
            int day = Integer.parseInt(value == null ? "" : value);
            if (day >= 1 && day <= 31) return day;
        } catch (NumberFormatException ignored) {
        }
        if (nextAt != null && nextAt.length() >= 10) {
            try {
                return LocalDate.parse(nextAt.substring(0, 10)).getDayOfMonth();
            } catch (DateTimeParseException ignored) {
            }
        }
        return 1;
    }

    static String repeatControls(String repeatType, String repeatDays, String repeatTime, Integer monthlyDay, String monthlyTime) {
        StringBuilder html = new StringBuilder("<label>\u7E70\u308A\u8FD4\u3057 <select class='repeat-type' name='repeatType'>");
        html.append("<option value='' ").append(repeatType == null ? "selected" : "").append(">\u306A\u3057</option>")
                .append("<option value='daily' ").append("daily".equals(repeatType) ? "selected" : "").append(">\u6BCE\u65E5</option>")
                .append("<option value='weekly' ").append("weekly".equals(repeatType) ? "selected" : "").append(">\u6BCE\u9031</option>")
                .append("<option value='monthly' ").append("monthly".equals(repeatType) ? "selected" : "").append(">\u6BCE\u6708</option>")
                .append("</select></label>")
                .append("<div class='repeat-fields daily-fields' hidden><label>\u6BCE\u65E5\u884C\u3046\u6642\u523B <input type='time' name='repeatTime' value='")
                .append(escapeHtml(parseRepeatTime(repeatTime))).append("'></label></div>")
                .append("<fieldset class='repeat-fields weekly-fields' hidden><legend>\u6BCE\u9031\u884C\u3046\u66DC\u65E5\uFF08\u8907\u6570\u9078\u629E\u53EF\uFF09</legend>");
        String[] weekdays = {"\u6708", "\u706B", "\u6C34", "\u6728", "\u91D1", "\u571F", "\u65E5"};
        String days = repeatDays == null ? "" : repeatDays;
        for (int day = 1; day <= 7; day++) {
            boolean checked = false;
            for (String selected : days.split(",")) if (Integer.toString(day).equals(selected)) checked = true;
            html.append("<label><input type='checkbox' name='repeatDays' value='").append(day).append("' ")
                    .append(checked ? "checked" : "").append(">").append(weekdays[day - 1]).append("\u66DC\u65E5</label>");
        }
        html.append("</fieldset><div class='repeat-fields monthly-fields' hidden><label>\u6BCE\u6708\u306E\u65E5\u4ED8 <select name='monthlyDay'>");
        int selectedMonthDay = monthlyDay == null || monthlyDay < 1 || monthlyDay > 31 ? 1 : monthlyDay;
        for (int day = 1; day <= 31; day++) {
            html.append("<option value='").append(day).append("' ").append(day == selectedMonthDay ? "selected" : "")
                    .append(">").append(day).append("\u65E5</option>");
        }
        return html.append("</select></label><label>\u6BCE\u6708\u884C\u3046\u6642\u523B <input type='time' name='monthlyTime' value='")
                .append(escapeHtml(parseRepeatTime(monthlyTime))).append("'></label></div>").toString();
    }

    static String repeatLabel(Todo todo) {
        if ("daily".equals(todo.getRepeatType())) return "\u6BCE\u65E5 " + parseRepeatTime(todo.getRepeatTime());
        if ("monthly".equals(todo.getRepeatType())) return "\u6BCE\u6708 " + todo.getMonthlyDay() + "\u65E5 " + parseRepeatTime(todo.getMonthlyTime());
        if ("weekly".equals(todo.getRepeatType())) {
            String[] weekdays = {"\u6708", "\u706B", "\u6C34", "\u6728", "\u91D1", "\u571F", "\u65E5"};
            StringBuilder label = new StringBuilder("\u6BCE\u9031");
            String days = todo.getRepeatDays() == null ? "1" : todo.getRepeatDays();
            for (String value : days.split(",")) {
                try {
                    int day = Integer.parseInt(value);
                    if (day >= 1 && day <= 7) label.append(" ").append(weekdays[day - 1]);
                } catch (NumberFormatException ignored) {
                }
            }
            return label.toString();
        }
        return "\u306A\u3057";
    }

    static String todoActions(Todo todo) {
        String id = Integer.toString(todo.getId());
        String action = todo.isDone()
                ? "<a href='/undo?id=" + id + "' data-confirm='\u672A\u5B8C\u4E86\u306B\u623B\u3057\u307E\u3059\u304B'>\u672A\u5B8C\u4E86\u306B\u623B\u3059</a> "
                : "<a href='/done?id=" + id + "' data-confirm='\u3053\u306E\u30BF\u30B9\u30AF\u3092\u5B8C\u4E86\u3057\u307E\u3059\u304B'>\u5B8C\u4E86</a> ";
        return action + "<a href='/delete?id=" + id + "' data-confirm='\u3053\u306E\u30BF\u30B9\u30AF\u3092\u524A\u9664\u3057\u307E\u3059\u304B'>\u524A\u9664</a></li>";
    }

    static void completeTodo(int id) throws SQLException {
        String type;
        String days;
        String dailyTime;
        Integer monthDay;
        String monthTime;
        String nextAt;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT repeat_type, repeat_days, repeat_day, repeat_time, monthly_day, monthly_time, next_at FROM todos WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return;
                type = result.getString("repeat_type");
                days = result.getString("repeat_days");
                if (days == null && result.getObject("repeat_day") != null) days = result.getString("repeat_day");
                dailyTime = result.getString("repeat_time");
                int value = result.getInt("monthly_day");
                monthDay = result.wasNull() ? null : value;
                monthTime = result.getString("monthly_time");
                nextAt = result.getString("next_at");
            }
        }
        String next = nextRepeatDate(type, days, dailyTime, monthDay, monthTime, nextAt);
        try (PreparedStatement statement = connection.prepareStatement(next == null
                ? "UPDATE todos SET done = 1 WHERE id = ?"
                : "UPDATE todos SET done = 0, next_at = ? WHERE id = ?")) {
            if (next == null) statement.setInt(1, id);
            else { statement.setString(1, next); statement.setInt(2, id); }
            statement.executeUpdate();
        }
    }

    static String nextRepeatDate(String type, String days, String dailyTime, Integer monthDay, String monthTime, String nextAt) {
        if (type == null) return null;
        try {
            LocalDateTime current = nextAt == null || nextAt.isBlank()
                    ? LocalDateTime.now().withSecond(0).withNano(0) : LocalDateTime.parse(nextAt);
            if ("daily".equals(type)) return current.toLocalDate().plusDays(1).atTime(LocalTime.parse(parseRepeatTime(dailyTime))).toString();
            if ("weekly".equals(type)) {
                boolean[] selected = new boolean[8];
                if (days != null) for (String value : days.split(",")) {
                    try { int day = Integer.parseInt(value); if (day >= 1 && day <= 7) selected[day] = true; }
                    catch (NumberFormatException ignored) { }
                }
                boolean any = false;
                for (int day = 1; day <= 7; day++) any |= selected[day];
                if (!any) selected[1] = true;
                for (int offset = 1; offset <= 7; offset++) {
                    LocalDate date = current.toLocalDate().plusDays(offset);
                    if (selected[date.getDayOfWeek().getValue()]) return date.atTime(current.toLocalTime()).toString();
                }
            }
            if ("monthly".equals(type)) {
                int day = monthDay == null || monthDay < 1 || monthDay > 31 ? 1 : monthDay;
                YearMonth month = YearMonth.from(current);
                LocalDate date = month.atDay(Math.min(day, month.lengthOfMonth()));
                if (!date.isAfter(current.toLocalDate())) { month = month.plusMonths(1); date = month.atDay(Math.min(day, month.lengthOfMonth())); }
                return date.atTime(LocalTime.parse(parseRepeatTime(monthTime))).toString();
            }
        } catch (DateTimeParseException e) {
            return null;
        }
        return null;
    }

    static String getDeadlineWarning(String nextAt) {
        if (nextAt == null || nextAt.isBlank()) {
            return null;
        }
        try {
            Duration remaining = Duration.between(LocalDateTime.now(), LocalDateTime.parse(nextAt));
            if (remaining.isNegative()) {
                return "期限が過ぎています！";
            }
            return remaining.compareTo(Duration.ofHours(24)) < 0 ? "期限まで残り僅かです！" : null;
        } catch (DateTimeParseException e) {
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
                "SELECT id, title, done, progress, next_at, details, category, repeat_type, repeat_day, repeat_days, repeat_time, monthly_day, monthly_time FROM todos "
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
                todo.setRepeatType(result.getString("repeat_type"));
                todo.setRepeatDays(result.getString("repeat_days"));
                if (todo.getRepeatDays() == null && result.getObject("repeat_day") != null) todo.setRepeatDays(result.getString("repeat_day"));
                todo.setRepeatTime(result.getString("repeat_time"));
                int monthlyDay = result.getInt("monthly_day");
                todo.setMonthlyDay(result.wasNull() ? null : monthlyDay);
                todo.setMonthlyTime(result.getString("monthly_time"));
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
    private String repeatType;
    private String repeatDays;
    private String repeatTime;
    private Integer monthlyDay;
    private String monthlyTime;

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
    String getRepeatType() { return repeatType; }
    void setRepeatType(String repeatType) { this.repeatType = repeatType; }
    String getRepeatDays() { return repeatDays; }
    void setRepeatDays(String repeatDays) { this.repeatDays = repeatDays; }
    String getRepeatTime() { return repeatTime; }
    void setRepeatTime(String repeatTime) { this.repeatTime = repeatTime; }
    Integer getMonthlyDay() { return monthlyDay; }
    void setMonthlyDay(Integer monthlyDay) { this.monthlyDay = monthlyDay; }
    String getMonthlyTime() { return monthlyTime; }
    void setMonthlyTime(String monthlyTime) { this.monthlyTime = monthlyTime; }
}
