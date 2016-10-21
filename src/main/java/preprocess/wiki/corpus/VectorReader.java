package preprocess.wiki.corpus;

import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.NlpAnalysis;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by root on 9/2/16.
 */
public class VectorReader {
    private static final String enMapFile = "/home/cuixuan/experiment/wiki_corpus/wiki.en.text.vector";
    private static final String zhMapFile = "/home/cuixuan/experiment/wiki_corpus/wiki.zh.text.vector";
    private static final String articleFile = "/home/cuixuan/experiment/articles/TextPairs_product.txt";
    private static final String artitleEnTitleOut = "/home/cuixuan/experiment/articles/article.title.en.vector";
    private static final String artitleZhTitleOut = "/home/cuixuan/experiment/articles/article.title.zh.vector";
    private static Map<String,String> enVectorMap;
    private static Map<String,String> zhVectorMap;

    public static void main(String[] args) throws IOException {
        enVectorMap = readVector(enMapFile);
        zhVectorMap = readVector(zhMapFile);
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(articleFile),"utf-8"));
        BufferedWriter enbw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(artitleEnTitleOut),"UTF-8"));
        BufferedWriter zhbw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(artitleZhTitleOut),"UTF-8"));
        String line = null;
        while((line = br.readLine())!=null){
            String[] splits = line.split("@#@#@");
            String title = splits[1];
            List<Term> parse = NlpAnalysis.parse(title.toLowerCase());
            Map<String,String> vectorMap = "en".equals(splits[2]) ? enVectorMap : zhVectorMap;
            BufferedWriter bw = "en".equals(splits[2]) ? enbw : zhbw;
            for(Term term:parse){
                String name = term.getName();
                if(!term.getNatrue().toString().startsWith("w") && vectorMap.containsKey(name)){
                    bw.write(name+vectorMap.get(name)+"\n");
                }else{
                    if(!term.getNatrue().toString().startsWith("w") && !name.equals(""))System.out.println(name);
                }
            }
        }
        br.close();
        enbw.close();
        zhbw.close();

    }

    public static Map<String,String> readVector(final String path) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path),"utf-8"));
        Map<String,String> vectorMap  = new HashMap<String, String>();
        String line = br.readLine();
        String[] splits = line.split(" ");
        int number_line = Integer.valueOf(splits[0]);
        System.out.println("Starting reading vector from: "+path);
        for(int i=0;i<number_line;i++){
            line = br.readLine();
            splits = line.split(" ");
            String key = splits[0];
            String value = line.substring(key.length());
            vectorMap.put(key,value);
            if(i%10000==0){
                System.out.println("vector read "+i+" lines.");
            }
        }
        br.close();
        return vectorMap;
    }
}
