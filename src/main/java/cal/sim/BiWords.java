package cal.sim;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by root on 9/21/16.
 */
public class BiWords {
    public String word1;
    public String word2;

    public BiWords(String w1,String w2){
        word1 = w1;
        word2 = w2;
    }

    @Override
    public int hashCode() {
        return word1.hashCode()+word2.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof BiWords) {
            BiWords biws = (BiWords)obj;
            return (word1.equals(biws.word1) && word2.equals(biws.word2)) || (word2.equals(biws.word1) && word1.equals(biws.word2));
        }
        return false;
    }

    @Override
    public String toString() {
        return "{ "+word1+"<==>"+word2+ " }";
    }

    public static void main(String[] args) {
        String word1 = "hello";
        String word2 = "world";
        BiWords biw = new BiWords(word1,word2);
        Map<BiWords,Integer> map = new HashMap<BiWords,Integer>();
        map.put(biw,1);
        System.out.println(map.containsKey(biw));
        System.out.println(map.containsKey(new BiWords(word2,word1)));
        System.out.println(map.containsKey(new BiWords(word1,word2)));
        System.out.println(map.containsKey(new BiWords("world","hello")));
        System.out.println(map.containsKey(new BiWords("hello","world")));
    }
}
