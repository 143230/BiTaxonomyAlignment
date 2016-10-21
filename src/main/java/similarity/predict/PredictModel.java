package similarity.predict;

import cal.sim.Article;

import java.io.*;
import java.util.*;

import static javafx.scene.input.KeyCode.V;

/**
 * Created by root on 9/4/16.
 */
public class PredictModel {
    private static final int num_vector_r = 107;
    private static final int num_vector_k = 50;
    private static final int topic_num = 50;
    private static String modelFile = "/home/cuixuan/experiment/genvector/model.save.txt";
    private static String KEYWORD_INDEX = "keyword.index";
    private static String AUTHOR_INDEX  = "author.index";
    private static String predictMapFile = "/home/cuixuan/experiment/articles/labelData_product.txt";
    private static int[] number_of_keyword;
    private static double[][] title_vector;
    private static double[][] keyword_vector;
    private static Map<String,String> articleMap = new HashMap<String, String>();
    private static int[] topic_index;
    private static int[][] keyword_topic_index;
    private static double[][] topic_dis;
    private static Map<String,Integer> indexMap = new HashMap<String,Integer>();
    private static List<String> articles_line = new ArrayList<String>();
    public static void main(String[] args) throws IOException {
        Scanner in = new Scanner(new FileInputStream(modelFile));
        int D = in.nextInt();
        int W = in.nextInt();
        title_vector = new double[D][num_vector_r];
        for(int i=0;i<D;i++){
            for(int j=0;j<num_vector_r;j++){
                title_vector[i][j]=in.nextDouble();
            }
        }
        keyword_vector = new double[W][num_vector_k];
        for(int i=0;i<W;i++){
            for(int j=0;j<num_vector_k;j++){
                keyword_vector[i][j]=in.nextDouble();
            }
        }
        topic_index = new int[D];
        for(int i=0;i<D;i++){
            topic_index[i] = in.nextInt();
        }
        number_of_keyword = new int[D];
        for(int i=0;i<D;i++){
            number_of_keyword[i] = in.nextInt();
        }
        keyword_topic_index = new int[D][];
        topic_dis = new double[D][topic_num];
        for(int i=0;i<D;i++){
            //keyword_topic_index[i] = new int[number_of_keyword[i]];
            //for(int j=0;j<number_of_keyword[i];j++){
            for(int j=0;j<topic_num;j++){
                topic_dis[i][j]=in.nextDouble();
                //keyword_topic_index[i][j] = in.nextInt();
                //topic_dis[i][keyword_topic_index[i][j]] += in.nextInt();
            }
        }
        assert in.hasNextLine() : "data has not read out.";
        in.close();
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(AUTHOR_INDEX),"utf-8"));
        String line;
        int line_number = 0;
        while((line = br.readLine())!=null){
            String[] splits = line.split("@#@#@");
            articles_line.add(line);
            indexMap.put(splits[0],line_number++);
        }
        br.close();
        articleMap = readPredictMap(predictMapFile);
        int right_number = 0;
        for(String key:articleMap.keySet()){
            Integer index = indexMap.get(key);
            String value = articleMap.get(key);
            Integer index_2 = indexMap.get(value);
            if(index==null || index_2==null)continue;
            double[] vector1 = topic_dis[index];
            double[] vector2 = topic_dis[index_2];
            double min = getDisrance(vector1,vector2);
            int min_index = -1;
            for(int i=0;i<D;i++){
                if(i==index || i==index_2)continue;
                if(articles_line.get(i).contains("jd.com"))continue;
                double dis = getDisrance(vector1,topic_dis[i]);
                if(min > dis){
                    min_index = i;
                    min=dis;
                }
            }
            if(min_index == -1){
                System.out.println("RIGHT:\t"+key+"\t"+value+"\t");
                right_number++;
            }else{
                System.out.println("WRONG:\t"+key+"\t"+value+"\tREAL:"+articles_line.get(min_index));
            }
        }
        System.out.println("accuracy:"+(right_number*1.0/articleMap.size()));
    }

    private static double getDisrance(double[] vector1, double[] vector2) {
        double dis = 0;
        for(int i=0;i<vector1.length;i++){
            dis += (vector1[i]-vector2[i])*(vector1[i]-vector2[i]);
        }
        return Math.sqrt(dis);
    }

    private static Map<String,String> readPredictMap(String path) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path),"utf-8"));
        Map<String, String> map = new HashMap<String, String>();
        String line;
        while((line = br.readLine())!=null){
            line = line.toLowerCase();
            String[] splits = line.split("\t");
            String[] article_splits1 = splits[0].split("@#@#@");
            String[] article_splits2 = splits[1].split("@#@#@");
            map.put(article_splits1[0],article_splits2[0]);
        }
        br.close();
        return map;
    }
}
