import com.sun.net.httpserver.HttpServer; // Java標準の簡易Webサーバーを使います
import java.net.InetSocketAddress; // サーバーの待ち受け先を指定するために使います
import java.net.URLDecoder; // URLエンコードされた文字を元に戻します
import java.nio.charset.StandardCharsets; // 文字の読み書きにUTF-8を指定します
import java.util.ArrayList; // Todoの一覧を作るために追加します
import java.util.List; // Todoの一覧を扱うために追加します

public class App { // Appという名前のクラスです
    static List<Todo> todos = new ArrayList<>(); // ★変更 Todoを保存する一覧をクラスで共有します
    static int nextId = 1; // ★変更 次に使う番号を1から始めます

    public static void main(String[] args) throws Exception { // プログラムの実行開始点です
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番ポートでサーバーを用意します
        todos.add(new Todo(nextId++, "牛乳を買う")); // ★変更 番号1のサンプルTodoを追加します
        Todo egg = new Todo(nextId++, "卵を買う"); // ★変更 番号2のサンプルTodoを用意します
        egg.setDone(true); // ★変更 卵を買うTodoを完了状態にします
        todos.add(egg); // ★変更 卵を買うTodoを一覧に追加します
        server.createContext("/", exchange -> { // "/"へのアクセスに対する処理をここに書きます
            String path = exchange.getRequestURI().getPath(); // アクセスされたパスを取り出します
            String method = exchange.getRequestMethod(); // GETやPOSTなどの方法を取り出します
            String message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 返す文字の種類とUTF-8を指定します
            if (path.equals("/add") && method.equals("POST")) { // POSTで/addに届いたTodoを追加します
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // 送られた内容をUTF-8で読み取ります
                String value = body.substring(5); // todo= の5文字を取り除きます
                String title = URLDecoder.decode(value, StandardCharsets.UTF_8); // ★変更 受け取った文字をTodoのタイトルにします
                if (!title.isEmpty()) { // ★変更 入力が空でないときだけ追加します
                    todos.add(new Todo(nextId, title)); // ★変更 番号を付けたTodoを一覧に追加します
                    nextId++; // ★変更 次に使う番号を進めます
                } // 空でないときの処理を終わります
                exchange.getResponseHeaders().set("Location", "/"); // 戻り先をトップページに指定します
                exchange.sendResponseHeaders(303, -1); // トップページへ戻る応答を送ります
                exchange.close(); // この通信を閉じます
                return; // この分岐の処理を終わります
            } else if (path.equals("/done") && method.equals("GET")) { // ★追加 GET /done で指定Todoを完了にします
                String query = exchange.getRequestURI().getQuery(); // ★追加 URLのクエリ（?以降）を受け取ります
                if (query != null && query.startsWith("id=") && query.length() > 3) { // ★追加 idがあることを確認します
                    try { // ★追加 idを数値に変換します
                        int id = Integer.parseInt(query.substring(3)); // ★追加 id=の後ろを数値にします
                        for (Todo todo : todos) { // ★追加 Todoを1件ずつ確認します
                            if (todo.getId() == id) { // ★追加 idが一致するTodoを探します
                                todo.setDone(true); // ★追加 一致したTodoを完了にします
                                break; // ★追加 一致したTodoの確認を終えます
                            } // ★追加 idの一致確認を終えます
                        } // ★追加 Todoの確認を終えます
                    } catch (NumberFormatException e) { // ★追加 数字でないidを受け止めます
                    } // ★追加 数字でない場合は何も変更しません
                } // ★追加 idがない場合も何も変更しません
                exchange.getResponseHeaders().set("Location", "/"); // ★追加 戻り先をトップページにします
                exchange.sendResponseHeaders(303, -1); // ★追加 303でトップページへ戻します
                exchange.close(); // ★追加 通信を閉じます
                return; // ★追加 この分岐の処理を終えます
            } else if (path.equals("/delete") && method.equals("GET")) { // ★追加 GET /delete で指定Todoを削除します
                String query = exchange.getRequestURI().getQuery(); // ★追加 URLのクエリ（?以降）を受け取ります
                if (query != null && query.startsWith("id=") && query.length() > 3) { // ★追加 idがあることを確認します
                    try { // ★追加 idを数値に変換します
                        int id = Integer.parseInt(query.substring(3)); // ★追加 id=の後ろを数値にします
                        todos.removeIf(todo -> todo.getId() == id); // ★追加 idが一致するTodoを削除します
                    } catch (NumberFormatException e) { // ★追加 数字でないidを受け止めます
                    } // ★追加 数字でない場合は何も変更しません
                } // ★追加 idがない場合も何も変更しません
                exchange.getResponseHeaders().set("Location", "/"); // ★追加 戻り先をトップページにします
                exchange.sendResponseHeaders(303, -1); // ★追加 303でトップページへ戻します
                exchange.close(); // ★追加 通信を閉じます
                return; // ★追加 この分岐の処理を終えます
            } else if (path.equals("/")) { // トップページにフォームとTodo一覧を表示します
                String html = "<html><head><meta charset='UTF-8'><style>body { max-width: 640px; margin: 24px auto; padding: 0 16px; font-size: 16px; }</style></head><body><h1>わたしのTodo</h1><form method='post' action='/add'><input name='todo'><button>追加</button></form>"; // ページの見出しと最小限の余白・幅・文字サイズを設定します
                if (todos.isEmpty()) { // Todoが0件か確認します
                    html += "<p>やることは、いまゼロです</p>"; // 0件のときにメッセージを表示します
                } else {
                    html += "<ul>"; // Todoの一覧を始めます
                    for (Todo todo : todos) { // ★変更 Todoを1件ずつ取り出します
                        String mark = ""; // ★変更 未完了のTodoには印を付けません
                        if (todo.isDone()) { // ★変更 Todoが完了しているか調べます
                            mark = " ✔"; // ★変更 完了したTodoに印を付けます
                        } // ★変更 完了状態の確認を終わります
                        html += "<li>" + todo.getTitle() + mark + " <a href='/done?id=" + todo.getId() + "'>完了</a> <a href='/delete?id=" + todo.getId() + "'>削除</a></li>"; // ★追加 タイトルと完了・削除リンクを一覧に加えます
                    } // Todoの項目を追加し終わります
                    html += "</ul>"; // HTMLのリストを閉じます
                }
                html += "</body></html>"; // HTMLページを閉じます
                message = html; // 組み立てたHTMLを返す内容にします
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8"); // トップページをHTMLとして返します
            } else {
                message = "ページが見つかりません"; // ★変更 それ以外のパスには見つからない応答を返します
            }
            byte[] body = message.getBytes("UTF-8"); // 文字をUTF-8のデータに変換します
            exchange.sendResponseHeaders(200, body.length); // 成功を示す番号とデータの長さを送ります
            exchange.getResponseBody().write(body); // データをブラウザへ送ります
            exchange.getResponseBody().close(); // 送信を終わります
        }); // "/"への処理を登録します
        server.start(); // サーバーの待ち受けを始めます
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動メッセージを表示します
    } // mainメソッドを終わります
} // Appクラスを終わります

class Todo { // ★変更 Todo1件の情報をまとめるクラスです
    private final int id; // ★変更 Todoの番号を保存します
    private final String title; // ★変更 やることを保存します
    private boolean done; // ★変更 完了したかを保存します

    Todo(int id, String title) { // ★変更 Todoの番号とタイトルを受け取ります
        this.id = id; // ★変更 受け取った番号を保存します
        this.title = title; // ★変更 受け取ったタイトルを保存します
        this.done = false; // ★変更 最初は未完了にします
    } // ★変更 コンストラクタを終わります

    int getId() { // ★変更 Todoの番号を読み出します
        return id; // ★変更 番号を返します
    } // ★変更 getIdを終わります

    String getTitle() { // ★変更 やることを読み出します
        return title; // ★変更 タイトルを返します
    } // ★変更 getTitleを終わります

    boolean isDone() { // ★変更 完了したかを読み出します
        return done; // ★変更 完了状態を返します
    } // ★変更 isDoneを終わります

    void setDone(boolean done) { // ★変更 完了状態を書き換えます
        this.done = done; // ★変更 新しい完了状態を保存します
    } // ★変更 setDoneを終わります
} // ★変更 Todoクラスを終わります
