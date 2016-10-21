package preprocess.wiki.corpus;

import java.util.*;

/**
 * Created by root on 9/5/16.
 */
public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        while(in.hasNextLine()){
            String line = in.nextLine();
            List<Character> lists = new ArrayList<Character>();
            for(Character c:line.toCharArray()){
                lists.add(c);
            }
            Collections.sort(lists, new Comparator<Character>() {
                public int compare(Character o1, Character o2) {
                    return Math.abs(o1.charValue()-'U') - Math.abs(o2.charValue() - 'U');
                }
            });
            for(Character c:lists){
                System.out.print(c);
            }
            System.out.println();
        }

    }
}
