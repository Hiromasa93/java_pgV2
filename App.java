import com.sun.net.httpserver.HttpServer; // Java標準の簡易Webサーバーを使います
import java.net.InetSocketAddress; // サーバーの待ち受け先を指定するために使います

public class App { // Appという名前のクラスです
    public static void main(String[] args) throws Exception { // プログラムの実行開始点です
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番ポートでサーバーを用意します
        server.createContext("/", exchange -> { // "/"へのアクセスに対する処理をここに書きます
            System.out.println("ハンドラが動いた");
            String message = "やあ!"; // ブラウザに返す文字を用意します
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 返す文字の種類とUTF-8を指定します
            byte[] body = message.getBytes("UTF-8"); // 文字をUTF-8のデータに変換します
            exchange.sendResponseHeaders(200, body.length); // 成功を示す番号とデータの長さを送ります
            exchange.getResponseBody().write(body); // データをブラウザへ送ります
            exchange.getResponseBody().close(); // 送信を終わります
        }); // "/"への処理を登録します
        server.start(); // サーバーの待ち受けを始めます
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動メッセージを表示します
    } // mainメソッドを終わります
} // Appクラスを終わります