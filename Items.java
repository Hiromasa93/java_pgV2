public class Items { // Itemsという名前のクラス（コードをまとめる枠）を作ります
    public static void main(String[] args) { // プログラムを実行したとき最初に動く場所です
        String[] todos = { "牛乳を買う", "", "パンを買う", "掃除をする" }; // Todoの文字列を配列に入れます
        boolean[] done = { true, false, false, false }; // Todoごとの完了状態を配列に入れます
        for (int i = 0; i < todos.length; i++) { // iで数え、配列の件数分だけ繰り返します
            if (!todos[i].isEmpty()) { // i番目のTodoが空文字列でない場合だけ処理します
                System.out.println("<li>" + todos[i] + (done[i] ? " [済]" : "") + "</li>"); // 完了済みなら[済]を付けて出力します
            } // if文（条件による分岐）をここで終えます
        } // for文の繰り返しをここで終えます
    } // mainメソッド（実行の入口）をここで終えます
} // Itemsクラスをここで終えます
