import java.util.ArrayList; // Todoを入れるリスト（複数の値を並べて入れる入れ物）を使います
import java.util.List; // Todoのリストの型を使います

public class Main { // 実行の入口になるMainクラスを作ります
    public static void main(String[] args) { // プログラムを実行すると最初に呼ばれるメソッドです
        List<Todo> todos = new ArrayList<>(); // Todoを入れる空のリストを作ります
        todos.add(new Todo("牛乳を買う", false)); // 未完了のTodoを1件追加します
        todos.add(new Todo("ゴミを出す", false)); // 済んだTodoを1件追加します
        todos.add(new Todo("勉強をする", true));
        for (Todo todo : todos) { // リストのTodoを1件ずつ取り出します
            System.out.println(todo.toItem()); // HTML風の1行にしてターミナルへ出力します
        } // for文（繰り返し）の終わりです
    } // mainメソッドの終わりです
} // Mainクラスの終わりです

class Todo { // Todoのデータと処理をまとめるクラスです
    String title; // Todoのタイトル（文字列）を保存します
    boolean done; // 済んだかどうか（trueかfalse）を保存します

    Todo(String title, boolean done) { // タイトルと済んだかどうかを受け取る作成用メソッドです
        this.title = title; // 受け取ったタイトルをこのTodoに保存します
        this.done = done; // 受け取った状態をこのTodoに保存します
    } // Todoの作成用メソッドの終わりです

    String toItem() { // Todoをliタグの1行に変えるメソッドです
        if (done) { // 済んでいるかを確認します
            return "<li>[finish] " + title + "</li>"; // 済んでいれば[済]を付けた行を返します
        } // if文（条件分岐）の終わりです
        return "<li>" + title + "</li>"; // 済んでいなければ通常の行を返します
    } // toItemメソッドの終わりです
} // Todoクラスの終わりです