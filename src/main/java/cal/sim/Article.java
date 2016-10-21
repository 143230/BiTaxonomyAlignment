package cal.sim;

import java.util.List;

/**
 * Created by root on 9/1/16.
 */
public class Article {
    private static final double alpha = BiCategorySim.alpa_t;
    private static int K;
    private String title;
    private Word title_word;
    private List<Word> words;
    private List<BiCategorySim.SingleWord> title_words;
    private int total_words;
    private String url;
    private String lang;

    private int total_topic;
    private int total_title_topic;
    private int[] num_topic;
    private int[] num_title_topic;

    private double[] topic_distribution;
    public Article(int k,String url,String lang,String title,List<Word> words,int total_words,Word title_word,List<BiCategorySim.SingleWord> title_words){
        K = k;
        this.url = url;
        this.lang = lang;
        this.title = title;
        this.words = words;
        this.total_words = total_words;
        this.title_word = title_word;
        this.title_words = title_words;
        num_topic = new int[K];
        num_title_topic = new int[K];
        topic_distribution = new double[K];
    }
    public List<Word> getWords(){
        return words;
    }
    public String getURL(){return url;}
    public String getLang(){return lang;}
    public List<BiCategorySim.SingleWord> getTitleList(){
        return title_words;
    }
    public Word getTitleWords(){
        return title_word;
    }
    public String getTitle(){
        return title;
    }
    public int getTopicByIndex(int index){return num_topic[index];}

    public void initDistribution(int old_topic,int topic_index) {
        if(old_topic==-1){
            num_topic[topic_index]++;
            total_topic++;
        }else{
            num_topic[old_topic]--;
            num_topic[topic_index]++;
        }
    }
    public void initTitleDistribution(int old_topic,int topic_index) {
        if(old_topic==-1){
            num_title_topic[topic_index]++;
            total_title_topic++;
        }else{
            num_title_topic[old_topic]--;
            num_title_topic[topic_index]++;
        }
    }
    public void updateDistribution(boolean finalIteration,int burnin){
        for(int i=0;i<K;i++){
            topic_distribution[i] += (num_topic[i] + BiCategorySim.alpa_t) / (total_topic + K*BiCategorySim.alpa_t);
            if(finalIteration){
                topic_distribution[i] = topic_distribution[i]/burnin;
            }
        }
    }

    public double[] genTopicDistribution() {
        return topic_distribution;
    }
}
