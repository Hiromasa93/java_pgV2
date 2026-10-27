import com.sun.net.httpserver.HttpServer; // Java標準の簡易Webサーバーを使います
import java.net.InetSocketAddress; // サーバーの待ち受け先を指定するために使います
import java.net.URLDecoder; // URLエンコードされた文字を元に戻します
import java.util.ArrayList; // Todoの一覧を作るために追加します
import java.util.List; // Todoの一覧を扱うために追加します

public class App { // Appという名前のクラスです
    public static void main(String[] args) throws Exception { // プログラムの実行開始点です
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番ポートでサーバーを用意します
        server.createContext("/", exchange -> { // "/"へのアクセスに対する処理をここに書きます
            String path = exchange.getRequestURI().getPath(); // アクセスされたパスを取り出します
            String message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 返す文字の種類とUTF-8を指定します
            if (path.equals("/hello")) { // パスが /hello か比べます
                String query = exchange.getRequestURI().getRawQuery(); // URLのクエリを取り出します
                String name = query == null ? "ゲスト" : query.substring(5); // クエリがないときはゲスト、それ以外は name= の後ろを切り出します
                name = URLDecoder.decode(name, "UTF-8"); // URLエンコードされた名前をUTF-8で元に戻します
                message = "こんにちは、" + name + "さん！"; // 名前を応答に加えます
            } else if (path.equals("/todos")) { // Todo一覧を返すパスを追加します
                List<String> todos = new ArrayList<>(); // Todoの文字列を入れる一覧を用意します
                todos.add("牛乳を買う"); // Todoを1件追加します
                todos.add("卵を買う"); // Todoを1件追加します
                todos.add("パンを買う"); // Todoを1件追加します
                todos.add("テストをする");
                String html = "<ul>"; // HTMLのリストを作り始めます
                for (String todo : todos) { // Todoを1件ずつ取り出します
                    html += "<li>" + todo + "</li>"; // TodoをHTMLの項目として追加します
                } // Todoの項目を追加し終わります
                html += "</ul>"; // HTMLのリストを閉じます
                message = html; // 組み立てたHTMLを返す内容にします
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8"); // このパスだけHTMLとして返します
            } else if (path.equals("/bye")) { // パスが /bye か調べます
                message = "さようなら！";
            } else if (path.equals("/menu")) {
                message = "今日はマック";
            } else {
                message = "ページが見つかりません";
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
