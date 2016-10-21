package test;

import cal.sim.BiCategorySim;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Created by root on 9/8/16.
 */
public class Test {
    public static final String vectorFile = "/root/shunyang/BiCategorySim_2/data/articles/TextPairs_product.txt_result50_50_50";
    public static final int num_vector_k=50;
    public static List<Article> selected_articles = new ArrayList<Article>();
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(vectorFile),"utf-8"));
        String line = null;
        while((line = br.readLine())!=null){
            String[] splits = line.split("@#@#@");
            String title = splits[1];
            String lang = splits[2].substring(0,2);
            String[] vecSplits = splits[2].substring(3).split(" ");
            double[] vector = new double[vecSplits.length];
            for(int i=0;i<vecSplits.length;i++){
                vector[i] = Double.valueOf(vecSplits[i]);
            }
            Article article = new Article(vector,title,lang);
            selected_articles.add(article);
        }
        br.close();
        fullPredict();
    }
    private static void fullPredict() {
        for(int i=0;i<selected_articles.size();i++){
            final Article article1 = selected_articles.get(i);
            PriorityQueue<Article> queue = new PriorityQueue<Article>(100, new Comparator<Article>() {
                public int compare(Article o1, Article o2) {
                    double distance1 = BiCategorySim.getDotDistance(article1.genTopicDistribution(),o1.genTopicDistribution(),num_vector_k);
                    double distance2 = BiCategorySim.getDotDistance(article1.genTopicDistribution(),o2.genTopicDistribution(),num_vector_k);
                    return distance1 > distance2 ? -1 :1;
                }
            });
            for(int j=0;j<selected_articles.size();j++){
                Article article = selected_articles.get(j);
                if(i==j || article.getLang().equals(article1.getLang()))continue;
                queue.add(article);
            }
            System.out.print(article1.getTitle());
            for(int top=0; !queue.isEmpty() && top<10;top++){
                Article article = queue.poll();
                double distance = BiCategorySim.getDotDistance(article1.genTopicDistribution(),article.genTopicDistribution(),num_vector_k);
                System.out.print(String.format("%20s:%.4f",article.getTitle(),distance));
            }
            System.out.println();
        }
        System.out.println("predict done.");
    }
    static class Article{
        double[] dis;
        String title;
        String lang;
        public Article(double[] dis,String tit,String lang){this.lang = lang;this.dis=dis;this.title=tit;}
        public double[] genTopicDistribution(){return dis;}
        public String getLang(){return lang;}
        public String getTitle(){return title;}
    }
}
