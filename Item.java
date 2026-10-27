public class Item { // Itemという名前のクラス（プログラムのまとまり）を作ります
    public static void main(String[] args) { // Javaが最初に実行する場所を作ります
        String title = "牛乳を買う"; // titleという文字列（文字の並び）に予定を入れます
        String html = "<li>" + title + "</li>"; // +で文字をつなぎ、HTMLの1行を作ります
        System.out.println(html); // 作った1行を画面に表示します
        boolean done = false;
        System.out.println(done);
        int count = 3;
        System.out.println("いま" + count + "件");
    } // mainの終わりです
} // Itemクラスの終わりです
