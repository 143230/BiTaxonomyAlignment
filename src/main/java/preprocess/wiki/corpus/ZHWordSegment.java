package preprocess.wiki.corpus;

import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.NlpAnalysis;
import org.ansj.util.MyStaticValue;
import sun.rmi.runtime.Log;

import java.io.*;
import java.util.List;

/**
 * Created by root on 8/26/16.
 */
public class ZHWordSegment {
    public static void main(String[] args) throws IOException {
        args = new String[2];
        MyStaticValue.userLibrary = "library/userLibrary.dic";
        args[0]="/home/cuixuan/experiment/wiki_corpus/wiki.zh.text.jian";
        args[1]="/home/cuixuan/experiment/wiki_corpus/wiki.zh.text.jian.in";
        if(args.length!=2){
            System.err.println("Argument must contains the src file and dst file.");
            System.exit(-1);
        }
        String srcPath = args[0];
        String dstPath = args[1];
        File src = new File(srcPath);
        if(!src.exists() || !src.isFile()){
            System.err.println("src file"+srcPath+" must be a file and exists.");
            System.exit(-1);
        }
        File dst = new File(dstPath);
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(src),"UTF-8"));
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dst),"UTF-8"));
        String line ;
        int index=0;
        while((line = in.readLine())!=null){
            List<Term> parse = NlpAnalysis.parse(line);
            StringBuilder builder = new StringBuilder("");
            for(Term term:parse){
                if(term.getNatrue().toString().startsWith("w"))continue;
                if(term.getName().trim().length()==0)continue;
                builder.append(term.getName()+" ");
            }
            index++;
            if(index%10000 == 0){
                System.out.println("Total line Finished:"+index);
            }
            bw.write(builder.toString()+"\r\n");
        }
        System.out.println("Finished Saved "+index+" articles.");
        in.close();
        bw.close();
        System.out.println("All data Finished.");

    }
}
