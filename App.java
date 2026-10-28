import com.sun.net.httpserver.HttpServer; // Java標準の簡易Webサーバーを使います
import java.io.IOException; // HTTP処理で発生する入出力エラーを扱います
import java.net.InetSocketAddress; // サーバーの待ち受け先を指定するために使います
import java.net.URLDecoder; // URLエンコードされた文字を元に戻します
import java.nio.charset.StandardCharsets; // 文字の読み書きにUTF-8を指定します
import java.sql.Connection; // ★ SQLiteデータベースへの接続を扱います
import java.sql.DriverManager; // ★ SQLiteデータベースに接続します
import java.sql.PreparedStatement; // ★ SQL文を安全に実行します
import java.sql.ResultSet; // ★ SELECT結果を読み取ります
import java.sql.SQLException; // ★ SQL処理のエラーを扱います
import java.sql.Statement; // ★ テーブル作成SQLを実行します
import java.util.ArrayList; // Todoの一覧を作ります
import java.util.List; // Todoの一覧を扱います

public class App { // Appという名前のクラスです
    static Connection connection; // ★ SQLiteへの接続を共有します

    public static void main(String[] args) throws Exception { // プログラムの実行開始点です
        connection = DriverManager.getConnection("jdbc:sqlite:todos.db"); // ★ todos.dbへ接続します
        try (Statement statement = connection.createStatement()) { // ★ テーブル作成用のSQL文を用意します
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY, title TEXT, done INTEGER)"); // ★ todos表が無ければ作成します
        } // ★ テーブル作成用SQL文を閉じます
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番ポートでサーバーを用意します
        server.createContext("/", exchange -> { // "/"へのアクセスに対する処理をここに書きます
            String path = exchange.getRequestURI().getPath(); // アクセスされたパスを取り出します
            String method = exchange.getRequestMethod(); // GETやPOSTなどの方法を取り出します
            String message; // ★ HTTPで返す内容を格納します
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 返す文字の種類とUTF-8を指定します
            if (path.equals("/add") && method.equals("POST")) { // POSTで/addに届いたTodoを追加します
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // 送られた内容をUTF-8で読み取ります
                String value = body.substring(5); // todo= の5文字を取り除きます
                String title = URLDecoder.decode(value, StandardCharsets.UTF_8); // 受け取った文字をTodoのタイトルにします
                if (!title.isEmpty()) { // 入力が空でないときだけ追加します
                    try (PreparedStatement statement = connection.prepareStatement("INSERT INTO todos (title, done) VALUES (?, 0)")) { // ★ Todo追加用のSQL文を用意します
                        statement.setString(1, title); // ★ 追加するタイトルをSQLに設定します
                        statement.executeUpdate(); // ★ Todoをデータベースへ追加します
                    } catch (SQLException e) { // ★ SQLエラーをHTTP入出力エラーに変換します
                        throw new IOException(e); // ★ HTTPハンドラーへエラーを伝えます
                    } // ★ Todo追加用SQL文の処理を終えます
                } // 空でないときの処理を終わります
                exchange.getResponseHeaders().set("Location", "/"); // 戻り先をトップページに指定します
                exchange.sendResponseHeaders(303, -1); // トップページへ戻る応答を送ります
                exchange.close(); // この通信を閉じます
                return; // この分岐の処理を終わります
            } else if (path.equals("/done") && method.equals("GET")) { // GET /doneで指定Todoを完了にします
                String query = exchange.getRequestURI().getQuery(); // URLのクエリ（?以降）を受け取ります
                if (query != null && query.startsWith("id=") && query.length() > 3) { // idがあることを確認します
                    try { // idを数値に変換します
                        int id = Integer.parseInt(query.substring(3)); // id=の後ろを数値にします
                        try (PreparedStatement statement = connection.prepareStatement("UPDATE todos SET done = 1 WHERE id = ?")) { // ★ 完了更新用のSQL文を用意します
                            statement.setInt(1, id); // ★ 更新対象のidをSQLに設定します
                            statement.executeUpdate(); // ★ Todoを完了状態に更新します
                        } // ★ 完了更新用SQL文を閉じます
                    } catch (NumberFormatException e) { // 数字でないidを受け止めます
                    } catch (SQLException e) { // ★ SQLエラーをHTTP入出力エラーに変換します
                        throw new IOException(e); // ★ HTTPハンドラーへエラーを伝えます
                    } // id変換と更新を終わります
                } // idがない場合も何も変更しません
                exchange.getResponseHeaders().set("Location", "/"); // 戻り先をトップページにします
                exchange.sendResponseHeaders(303, -1); // 303でトップページへ戻します
                exchange.close(); // 通信を閉じます
                return; // この分岐の処理を終わります
            } else if (path.equals("/delete") && method.equals("GET")) { // GET /deleteで指定Todoを削除します
                String query = exchange.getRequestURI().getQuery(); // URLのクエリ（?以降）を受け取ります
                if (query != null && query.startsWith("id=") && query.length() > 3) { // idがあることを確認します
                    try { // idを数値に変換します
                        int id = Integer.parseInt(query.substring(3)); // id=の後ろを数値にします
                        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM todos WHERE id = ?")) { // ★ Todo削除用のSQL文を用意します
                            statement.setInt(1, id); // ★ 削除対象のidをSQLに設定します
                            statement.executeUpdate(); // ★ Todoをデータベースから削除します
                        } // ★ Todo削除用SQL文を閉じます
                    } catch (NumberFormatException e) { // 数字でないidを受け止めます
                    } catch (SQLException e) { // ★ SQLエラーをHTTP入出力エラーに変換します
                        throw new IOException(e); // ★ HTTPハンドラーへエラーを伝えます
                    } // id変換と削除を終わります
                } // idがない場合も何も変更しません
                exchange.getResponseHeaders().set("Location", "/"); // 戻り先をトップページにします
                exchange.sendResponseHeaders(303, -1); // 303でトップページへ戻します
                exchange.close(); // 通信を閉じます
                return; // この分岐の処理を終わります
            } else if (path.equals("/")) { // トップページにフォームとTodo一覧を表示します
                String html = "<html><head><meta charset='UTF-8'><style>body { max-width: 640px; margin: 24px auto; padding: 0 16px; font-size: 16px; }</style></head><body><h1>わたしのTodo</h1><form method='post' action='/add'><input name='todo'><button>追加</button></form>"; // ページの見出しと最小限の余白・幅・文字サイズを設定します
                try { // ★ データベースから一覧を読み込みます
                    List<Todo> todos = selectTodos(); // ★ SELECT結果をTodo一覧として取得します
                    if (todos.isEmpty()) { // Todoが0件か確認します
                        html += "<p>やることは、いまゼロです</p>"; // 0件のときにメッセージを表示します
                    } else { // Todoがあるときの表示を作ります
                        html += "<ul>"; // Todoの一覧を始めます
                        for (Todo todo : todos) { // ★ SELECTで取得したTodoを順に表示します
                            String mark = ""; // 未完了のTodoには印を付けません
                            if (todo.isDone()) { // Todoが完了しているか調べます
                                mark = " ✔"; // 完了したTodoに印を付けます
                            } // 完了状態の確認を終わります
                            html += "<li>" + todo.getTitle() + mark + " <a href='/done?id=" + todo.getId() + "'>完了</a> <a href='/delete?id=" + todo.getId() + "'>削除</a></li>"; // タイトルと完了・削除リンクを一覧に加えます
                        } // Todoの項目を追加し終わります
                        html += "</ul>"; // HTMLのリストを閉じます
                    } // Todo一覧の表示を終わります
                } catch (SQLException e) { // ★ 一覧取得時のSQLエラーを処理します
                    throw new IOException(e); // ★ HTTPハンドラーへエラーを伝えます
                } // ★ Todo一覧の取得を終わります
                html += "</body></html>"; // HTMLページを閉じます
                message = html; // 組み立てたHTMLを返す内容にします
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8"); // トップページをHTMLとして返します
            } else { // 存在しないパスのときの処理です
                message = "ページが見つかりません"; // それ以外のパスには見つからない応答を返します
            } // リクエストの処理を終わります
            byte[] responseBody = message.getBytes(StandardCharsets.UTF_8); // 文字をUTF-8のデータに変換します
            exchange.sendResponseHeaders(200, responseBody.length); // 成功を示す番号とデータの長さを送ります
            exchange.getResponseBody().write(responseBody); // データをブラウザへ送ります
            exchange.getResponseBody().close(); // 送信を終わります
        }); // "/"への処理を登録します
        server.createContext("/api/todos", exchange -> { // ★ JSON一覧APIの入口を登録します
            if (!exchange.getRequestMethod().equals("GET")) { // ★ GET以外のリクエストか確認します
                exchange.sendResponseHeaders(405, -1); // ★ GET以外にはMethod Not Allowedを返します
                exchange.close(); // ★ APIへの通信を閉じます
                return; // ★ このリクエストの処理を終えます
            } // ★ HTTPメソッドの確認を終えます
            try { // ★ DBから取得してJSONを返します
                byte[] responseBody = todosToJson().getBytes(StandardCharsets.UTF_8); // ★ Todo一覧をUTF-8のJSONデータにします
                exchange.getResponseHeaders().set("Content-Type", "application/json"); // ★ charsetなしのJSON形式を指定します
                exchange.sendResponseHeaders(200, responseBody.length); // ★ 成功応答とJSONの長さを送ります
                exchange.getResponseBody().write(responseBody); // ★ JSONをリクエスト元へ返します
                exchange.getResponseBody().close(); // ★ レスポンスの送信を終えます
            } catch (SQLException e) { // ★ DB読み込み時のSQLエラーを受け取ります
                throw new IOException(e); // ★ HTTPハンドラーへエラーを伝えます
            } // ★ JSON応答の処理を終えます
        }); // ★ JSON一覧APIの入口を登録します
        server.start(); // サーバーの待ち受けを始めます
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動メッセージを表示します
    } // mainメソッドを終わります

    static String todosToJson() throws SQLException { // ★ DBの全TodoをJSON配列に変換します
        List<Todo> todos = selectTodos(); // ★ SELECTで全Todoを読み出します
        StringBuilder json = new StringBuilder("["); // ★ JSON配列の先頭を作ります
        for (int i = 0; i < todos.size(); i++) { // ★ Todoを順番にJSONへ追加します
            Todo todo = todos.get(i); // ★ 現在のTodoを取り出します
            if (i > 0) { // ★ 2件目以降か確認します
                json.append(','); // ★ JSON要素の間にカンマを入れます
            } // ★ 要素区切りの処理を終えます
            json.append("{\"title\":\"").append(escapeJson(todo.getTitle())).append("\",\"done\":").append(todo.isDone()).append('}'); // ★ タイトルと完了状態をJSONオブジェクトにします
        } // ★ TodoをすべてJSONへ追加します
        return json.append(']').toString(); // ★ JSON配列を閉じて返します
    } // ★ todosToJsonメソッドを終わります

    static String escapeJson(String value) { // ★ JSON文字列内で特別な文字をエスケープします
        StringBuilder escaped = new StringBuilder(); // ★ エスケープ後の文字列を組み立てます
        for (int i = 0; i < value.length(); i++) { // ★ タイトルを1文字ずつ確認します
            char ch = value.charAt(i); // ★ 現在の文字を取り出します
            switch (ch) { // ★ JSONで特別な扱いが必要な文字を判定します
                case '"': escaped.append("\\\""); break; // ★ 二重引用符をエスケープして次へ進みます
                case '\\': escaped.append("\\\\"); break; // ★ バックスラッシュをエスケープして次へ進みます
                case '\b': escaped.append("\\b"); break; // ★ バックスペースをエスケープして次へ進みます
                case '\f': escaped.append("\\f"); break; // ★ フォームフィードをエスケープして次へ進みます
                case '\n': escaped.append("\\n"); break; // ★ 改行をエスケープして次へ進みます
                case '\r': escaped.append("\\r"); break; // ★ 復帰をエスケープして次へ進みます
                case '\t': escaped.append("\\t"); break; // ★ タブをエスケープして次へ進みます
                default: { // ★ その他の文字を処理します
                    if (ch < 0x20) { // ★ JSONで直接使えない制御文字か確認します
                        escaped.append(String.format("\\u%04x", (int) ch)); // ★ 制御文字をUnicode形式にします
                    } else { // ★ 通常の文字の場合です
                        escaped.append(ch); // ★ 文字をそのまま追加します
                    } // ★ 制御文字の処理を終えます
                } // ★ その他の文字の処理を終えます
            } // ★ 現在の文字のエスケープを終えます
        } // ★ タイトルの全文字を処理します
        return escaped.toString(); // ★ エスケープ済み文字列を返します
    } // ★ escapeJsonメソッドを終わります

    static List<Todo> selectTodos() throws SQLException { // ★ データベースからTodo一覧を読み出します
        List<Todo> todos = new ArrayList<>(); // ★ 読み込んだTodoを格納します
        try (PreparedStatement statement = connection.prepareStatement("SELECT id, title, done FROM todos ORDER BY id"); // ★ 一覧取得用のSELECT文を用意します
             ResultSet result = statement.executeQuery()) { // ★ SELECT文を実行します
            while (result.next()) { // ★ 結果を1件ずつ読み込みます
                Todo todo = new Todo(result.getInt("id"), result.getString("title")); // ★ idとタイトルからTodoを作ります
                todo.setDone(result.getInt("done") != 0); // ★ DBの完了状態をTodoへ反映します
                todos.add(todo); // ★ Todoを一覧に加えます
            } // ★ SELECT結果をすべて読み込みます
        } // ★ SELECT文と結果を閉じます
        return todos; // ★ データベースから読んだ一覧を返します
    } // ★ selectTodosメソッドを終わります
} // Appクラスを終わります

class Todo { // Todo1件の情報をまとめるクラスです
    private final int id; // Todoの番号を保存します
    private final String title; // やることを保存します
    private boolean done; // 完了したかを保存します

    Todo(int id, String title) { // Todoの番号とタイトルを受け取ります
        this.id = id; // 受け取った番号を保存します
        this.title = title; // 受け取ったタイトルを保存します
        this.done = false; // 最初は未完了にします
    } // コンストラクタを終わります

    int getId() { // Todoの番号を読み出します
        return id; // 番号を返します
    } // getIdを終わります

    String getTitle() { // やることを読み出します
        return title; // タイトルを返します
    } // getTitleを終わります

    boolean isDone() { // 完了したかを読み出します
        return done; // 完了状態を返します
    } // isDoneを終わります

    void setDone(boolean done) { // 完了状態を書き換えます
        this.done = done; // 新しい完了状態を保存します
    } // setDoneを終わります
} // Todoクラスを終わります
