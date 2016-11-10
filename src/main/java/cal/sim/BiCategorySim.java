package cal.sim;

import knowledge.MustSet;
import knowledge.MustSets;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.NlpAnalysis;
import org.apache.commons.math.special.Gamma;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class BiCategorySim {
    private static final double LOG_2_PI = Math.log(2* Math.PI);
    private static final double alpha_0=1e3,beta_0=1.0, kappa_0=1e-5, mu_0=0.0;
    public static final double alpa_t = 1,beta_t=0.1;
    private static final double lr_r=1e-3, lr_k = 1e-6;
    private static final int topic_num = 50;
    private static final int sample_round = 200;
    private static final int num_vector_r = 107;
    private static final int num_vector_k = 50;

    private static final int filter_word_frequency = 10;

    private static int num_articles;
    private static int number_of_burnin=0;
    private static int burin_period = 20;
    private static int S;
    private static Map<String,Vector> enMap;
    private static Map<String,Vector> zhMap;
    private static Map<String,Vector> enTitleMap;
    private static Map<String,Vector> zhTitleMap;
    private static Map<String,Article> articleMap = new HashMap<String, Article>();
    private static Set<String> articleStandardResult = new HashSet<String>();
    private static List<Article> articles;
    private static Set<BiWords> dictionary = new HashSet<BiWords>();
    private static MustSets mustSets = new MustSets();
    private static List<Article> selected_articles;

    private static int[] number_per_topic;//
    private static int[][] number_topic_set;//topic_num * S

    private static int[] number_per_title_topic;//
    private static int[][] number_title_topic_set;//topic_num * S

    private static int[][] number_topic_semantic;
    private static int[][][] number_topic_semantic_word;


    private static int[][] number_title_topic_semantic;
    private static int[][][] number_title_topic_semantic_word;

    private static double MRR=0;

    private static double[][][] square_sum_x_r;
    private static double[][][] square_sum_x_k;
    private static double[][][] average_x_r;
    private static double[][][] average_x_k;
    private static final String articleFile = "data/articles/TextPairs_product.txt";
    public static final String knowledgeFile = "data/knowledge.dat";
    private static final String envectorFile = "data/vector/article.projected.en.text.vector_50";
    private static final String zhvectorFile = "data/vector/article.projected.zh.text.vector_50";
    private static final String entitlevectorFile = "data/vector/article.title.projected.en.vector";
    private static final String zhtitlevectorFile = "data/vector/article.title.projected.zh.vector";
    private static final String predictMapFile = "data/articles/labelData_product.txt";
    private static final String enstopwordFile = "data/stopwords_en.dat";
    private static final String zhstopwordFile = "data/stopwords_zh.dat";
    private static final String synSetSetFile = "data/synSetMap_product.map";
    private static Set<String> stopwords;

    private static final String topic_distribution_prefix = articleFile+"_result";

    private static Map<Article,Article> predictMap;
    private static Map<String,Set<String>> synMap = null;

    private static Future<Double>[][] futures = new Future[topic_num][];
    private static TopicProbCalculator[][] calculator = new TopicProbCalculator[topic_num][];
    private static ExecutorService service = Executors.newFixedThreadPool(topic_num>400?400:topic_num);

    static class TopicProbCalculator implements Callable<Double>{
        private Article article;
        private int topic_index;
        private int semantic_index;
        private boolean is_article_words;
        private Word word;
        public TopicProbCalculator(){}
        public void setValues(Article article,int topic_index,int semantic_index,Word word,boolean is_article_words){
            this.article = article;
            this.topic_index = topic_index;
            this.semantic_index = semantic_index;
            this.is_article_words = is_article_words;
            this.word = word;
        }

        public Double call() throws Exception {
            if(is_article_words){
                return calculateTopicProb(article,topic_index,semantic_index,word);//O(number of words);
            }else{
                return calculateTitleTopicProb(article,topic_index,semantic_index,word);
            }
        }
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        System.out.println("reading article words vector.");
        enMap = readMap(envectorFile,num_vector_k);
        zhMap = readMap(zhvectorFile,num_vector_k);
        List<String> stopwordslist = new ArrayList<String>();
        stopwordslist.add(enstopwordFile);
        stopwordslist.add(zhstopwordFile);
        stopwords = readStopWord(stopwordslist);
        System.out.println("reading article title words vector.");
        enTitleMap = readMap(entitlevectorFile,num_vector_r);
        zhTitleMap = readMap(zhtitlevectorFile,num_vector_r);
        System.out.println("reading article content.");
        readStandardPair(predictMapFile);
        articles = readArticles(articleFile);
        predictMap = readPredictMap(predictMapFile);
        System.out.println("select Sample article from all articles.");
        selectArticles();
        mustSets.getMustSets(dictionary);
        System.out.println("Init.");
        init();
        System.out.println("Start to sample data.");
        startTrain();
        System.out.println("Save Results");
        saveArticleDistribution();
        System.out.println("predict for results.");
        fullPredict();
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++");
        predict();
        System.out.println("Done");
        service.shutdown();
    }

    private static void readStandardPair(String path) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path),"utf-8"));
        String line;
        while((line = br.readLine())!=null){
            line = line.toLowerCase();
            String[] splits = line.split("\t");
            String[] article_splits1 = splits[0].split("@#@#@");
            articleStandardResult.add(article_splits1[0]);
            String[] article_splits2 = splits[1].split("@#@#@");
            articleStandardResult.add(article_splits2[0]);
        }
        br.close();
        System.out.println("Total Article For Standard: "+articleStandardResult.size());
    }

    private static void saveArticleDistribution() throws IOException {
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(topic_distribution_prefix+topic_num+"_"+sample_round+"_"+num_vector_k),"utf-8"));
        StringBuilder outline;
        for(int i=0;i<selected_articles.size();i++){
            Article article = selected_articles.get(i);
            outline = new StringBuilder(article.getURL());
            outline.append("@#@#@"+article.getTitle());
            outline.append("@#@#@"+article.getLang()+"@#@#@");
            double[] topic_dis = article.genTopicDistribution();
            for(double dis:topic_dis){
                outline.append(" "+dis);
            }
            /*double[] title_topic_dis = article.genTitleTopicDistribution();
            outline.append("@#@#@");
            for(double dis:title_topic_dis){
                outline.append(" "+dis);
            }*/
            outline.append("\n");
            bw.write(outline.toString());
        }
        bw.close();
    }

    private static void selectArticles() {
        selected_articles = new ArrayList<Article>();
        Set<Article> selected_set = new HashSet<Article>();
        for(Article article:predictMap.keySet()){
            Article value = predictMap.get(article);
            if(article==null || value==null)continue;
            if(!selected_set.contains(article)){
                selected_articles.add(article);
                selected_set.add(article);
            }
            if(!selected_set.contains(value)) {
                selected_articles.add(value);
                selected_set.add(value);
            }
        }
        int index = 0;
        while(selected_articles.size()<20000){
            if(index>=articles.size())break;
            Article article = articles.get(index++);
            if(selected_set.contains(article))continue;
            selected_set.add(article);
            selected_articles.add(article);
        }
        num_articles = selected_articles.size();
        System.out.println("Article has been selected out : "+num_articles);
        for(int i=0;i<num_articles;i++){
            Article article = selected_articles.get(i);
            List<Word> words = article.getWords();
            for(int j=0;j<words.size();j++){
                BiWords bis = words.get(j).getWords();
                dictionary.add(bis);
            }
            dictionary.add(selected_articles.get(i).getTitleWords().getWords());
        }
        System.out.println("Dictionary Size: "+dictionary.size());
    }

    private static double predict() throws IOException {
        int total_right = 0;
        MRR=0;
        if(synMap==null){
            synMap = readSynSets(synSetSetFile);
        }
        Set<Article> articles_compare = new HashSet<Article>();
        for(Article article:predictMap.keySet()){
            if(article==null)continue;
            articles_compare.add(article);
            articles_compare.add(predictMap.get(article));
        }
        for(final Article article1:predictMap.keySet()){
            Article article2 = predictMap.get(article1);
            if(article1==null || article2==null)continue;
            Set<String> synset1 = getSynSets(article1,synMap);
            Comparator<Article> cmp = new Comparator<Article>() {
                public int compare(Article o1, Article o2) {
                    double distance1 = getDotDistance(article1.genTopicDistribution(),o1.genTopicDistribution(),num_vector_k);
                    double distance2 = getDotDistance(article1.genTopicDistribution(),o2.genTopicDistribution(),num_vector_k);
                    return distance1 > distance2 ? -1 :1;
                }
            };
            PriorityQueue<Article> queue_css = new PriorityQueue<Article>(100, cmp);
            PriorityQueue<Article> queue_nocss = new PriorityQueue<Article>(100, cmp);
            for(Article article:articles_compare){
                if(article==article1 || article.getLang().equals(article1.getLang()))continue;
                Set<String> synset = getSynSets(article,synMap);
                if(!Collections.disjoint(synset,synset1)){
                    queue_css.add(article);
                }else {
                    queue_nocss.add(article);
                }
            }
            System.out.print(article1.getTitle()+"Expected Article: ");
            double distance = getDotDistance(article1.genTopicDistribution(),article2.genTopicDistribution(),num_vector_k);
            System.out.println(String.format("\t%s:%.4f",article2.getTitle(),distance));
            int number_index = 1;
            for(int top=0; !queue_css.isEmpty();top++){
                Article article = queue_css.poll();
                distance = getDotDistance(article1.genTopicDistribution(),article.genTopicDistribution(),num_vector_k);
                if(article2 == article)MRR+=1.0/number_index;
                number_index++;
                System.out.print(String.format("\t\t\t%s:%.4f",article.getTitle(),distance));
            }
            for(int top=0; !queue_nocss.isEmpty();top++){
                Article article = queue_nocss.poll();
                distance = getDotDistance(article1.genTopicDistribution(),article.genTopicDistribution(),num_vector_k);
                if(article2 == article)MRR+=1.0/number_index;
                number_index++;
                if(top<10){
                    System.out.print(String.format("\t\t\t%s:%.4f",article.getTitle(),distance));
                }
            }
            System.out.println();
        }
        System.out.println("The whole MRR value is: "+(MRR/predictMap.size()));
        return total_right*1.0/predictMap.size();
    }

    private static Set<String> getSynSets(Article article, Map<String, Set<String>> synMap) {
        Set<String> set = new HashSet<String>();
        for(SingleWord word:article.getTitleList()){
            String key = word.name;
            Set<String> synset = synMap.get(key);
            for(String id:synset)set.add(id);
        }
        return set;
    }

    private static void fullPredict() {
        for(int i=0;i<selected_articles.size();i++){
            final Article article1 = selected_articles.get(i);
            PriorityQueue<Article> queue = new PriorityQueue<Article>(100, new Comparator<Article>() {
                public int compare(Article o1, Article o2) {
                    double distance1 = getDotDistance(article1.genTopicDistribution(),o1.genTopicDistribution(),num_vector_k);
                    double distance2 = getDotDistance(article1.genTopicDistribution(),o2.genTopicDistribution(),num_vector_k);
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
                double distance = getDotDistance(article1.genTopicDistribution(),article.genTopicDistribution(),num_vector_k);
                System.out.print(String.format("%20s:%.4f",article.getTitle(),distance));
            }
            System.out.println();
        }
        System.out.println("predict done.");
    }
    public static boolean isCSS(Article article1,Article article2){
        List<String> list1 = getSplitList(article1.getTitle(),article1.getLang());
        List<String> list2 = getSplitList(article2.getTitle(),article2.getLang());
        for(String word1:list1){
            for(String word2:list2){
                BiWords bis = new BiWords(word1,word2);
                List<MustSet> mustSetList = mustSets.getMustSetListGivenWordstr(bis);
                if(mustSetList.size()>1 || mustSetList.get(0).size()>1)return true;
            }
        }
        return false;
    }
    public static List<String> getSplitList(String sentence,String lang){
        List<Term> parse = NlpAnalysis.parse(sentence.toLowerCase());
        Map<String,Vector> vectorMap = "en".equals(lang) ? enTitleMap : zhTitleMap;
        List<String> title_words = new ArrayList<String>();
        for(Term term:parse){
            String name = term.getName();
            if(!term.getNatrue().toString().startsWith("w") && vectorMap.containsKey(name)){
                if(stopwords.contains(name))continue;
                title_words.add(name);
            }
        }
        return title_words;
    }

    private static double getDistance(double[] topic_distribution1, double[] topic_distribution2, int length) {
        double dis = 0;
        for(int i=0;i<length;i++){
            dis+=(topic_distribution1[i]-topic_distribution2[i])*(topic_distribution1[i]-topic_distribution2[i]);
        }
        return Math.sqrt(dis);
    }
    public static double getDotDistance(double[] topic_distribution1, double[] topic_distribution2, int length) {
        List<Double> vector1 = new ArrayList<Double>(length);
        for(double vec:topic_distribution1)vector1.add(vec);
        List<Double> vector2 = new ArrayList<Double>(length);
        for(double vec:topic_distribution2)vector2.add(vec);
        return getDotDistance(vector1,vector2,length);
    }
    public static double getDotDistance(List<Double> topic_distribution1, List<Double> topic_distribution2, int length) {
        double dis = 0;
        List<Double> vector1 = unitvec(topic_distribution1,length);
        List<Double> vector2 = unitvec(topic_distribution2,length);
        for(int i=0;i<length;i++){
            ASSERT(vector1.get(i));
            ASSERT(vector2.get(i));
            dis+=vector1.get(i)*vector2.get(i);
        }
        return dis;
    }

    private static List<Double> unitvec(List<Double> topic_distribution1, int length) {
        double unit_length = 0;
        for(double vec:topic_distribution1){
            unit_length+=vec*vec;
        }
        unit_length = Math.sqrt(unit_length);
        List<Double> vector = new ArrayList<Double>(length);
        for(double vec:topic_distribution1){
            vector.add(vec/unit_length);
        }
        return vector;
    }

    private static Map<Article,Article> readPredictMap(String path) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path),"utf-8"));
        Map<Article,Article> map = new HashMap<Article, Article>();
        String line;
        while((line = br.readLine())!=null){
            line = line.toLowerCase();
            String[] splits = line.split("\t");
            String[] article_splits1 = splits[0].split("@#@#@");
            Article article1 = articleMap.get(article_splits1[0]);
            String[] article_splits2 = splits[1].split("@#@#@");
            Article article2 = articleMap.get(article_splits2[0]);
            if(article1 == null || article2==null)continue;
            map.put(article1,article2);
        }
        br.close();
        return map;
    }

    public static void init(){
        S = mustSets.size();
        number_per_topic = new int[topic_num];
        number_per_title_topic = new int[topic_num];
        number_topic_set = new int[topic_num][S];
        number_title_topic_set = new int[topic_num][S];
        number_topic_semantic = new int[topic_num][S];
        number_title_topic_semantic = new int[topic_num][S];
        number_topic_semantic_word = new int[topic_num][S][];
        number_title_topic_semantic_word = new int[topic_num][S][];
        for(int i=0;i<topic_num;i++) {
            for(int j=0;j<S;j++) {
                MustSet mustSet = mustSets.getMustSet(j);
                int mustSetSize = mustSet.size();
                number_topic_semantic_word[i][j] = new int[mustSetSize];
                number_title_topic_semantic_word[i][j] = new int[mustSetSize];
            }
        }
        square_sum_x_k = new double[topic_num][S][num_vector_k];
        square_sum_x_r = new double[topic_num][S][num_vector_r];
        average_x_k = new double[topic_num][S][num_vector_k];
        average_x_r = new double[topic_num][S][num_vector_r];
        int maxSetSize = 0;

        for(int i=0;i<num_articles;i++){
            Article article = selected_articles.get(i);
            //init article words.
            List<Word> words = article.getWords();
            for(int j=0;j<words.size();j++){
                Word word = words.get(j);
                int topic_index = (int)(Math.random()*topic_num);
                word.setTopic(topic_index);
                ArrayList<MustSet> mustsetList = mustSets.getMustSetListGivenWordstr(word.getWords());
                maxSetSize = maxSetSize > mustsetList.size() ? maxSetSize : mustsetList.size();
                int semantic_index=(int) (Math.random()*(mustsetList.size()));	//只随机分配跟当前word关联的mustset
                MustSet mustSet=mustsetList.get(semantic_index);
                int smn = mustSets.getMustSetIndex(mustSet);	//返回mustset在整个S中的索引
                word.setSemantic(smn);
                for(int e=0;e<num_vector_k;e++){
                    double vector_k_e1 = word.getVector1ByIndex(e);
                    double vector_k_e2 = word.getVector2ByIndex(e);
                    square_sum_x_k[topic_index][smn][e]+=vector_k_e2*vector_k_e2 + vector_k_e1*vector_k_e1;
                    average_x_k[topic_index][smn][e]+=vector_k_e1 + vector_k_e2;
                }
                BiWords biwords = word.getWords();
                int word1Index = mustSet.getWordIndex(biwords.word1);
                int word2Index = mustSet.getWordIndex(biwords.word2);


                article.initDistribution(-1,topic_index);
                updateUrn(smn,topic_index,word.getWords(),1);
                number_topic_semantic[topic_index][smn]+=2;
                number_topic_semantic_word[topic_index][smn][word1Index]++;
                number_topic_semantic_word[topic_index][smn][word2Index]++;
            }
            //init article titles;
            Word title_word = article.getTitleWords();
            int topic_index = (int)(Math.random()*topic_num);
            title_word.setTopic(topic_index);
            ArrayList<MustSet> mustsetList = mustSets.getMustSetListGivenWordstr(title_word.getWords());
            maxSetSize = maxSetSize > mustsetList.size() ? maxSetSize : mustsetList.size();
            int semantic_index=(int) (Math.random()*(mustsetList.size()));	//只随机分配跟当前word关联的mustset
            MustSet mustSet=mustsetList.get(semantic_index);
            int smn = mustSets.getMustSetIndex(mustSet);	//返回mustset在整个S中的索引
            title_word.setSemantic(smn);
            for(int e=0;e<num_vector_k;e++){
                double vector_k_e1 = title_word.getVector1ByIndex(e);
                double vector_k_e2 = title_word.getVector2ByIndex(e);
                square_sum_x_r[topic_index][smn][e]+=vector_k_e2*vector_k_e2 + vector_k_e1*vector_k_e1;
                average_x_r[topic_index][smn][e]+=vector_k_e1 + vector_k_e2;
            }
            BiWords biwords = title_word.getWords();
            int word1Index = mustSet.getWordIndex(biwords.word1);
            int word2Index = mustSet.getWordIndex(biwords.word2);

            article.initTitleDistribution(-1,topic_index);
            updateTitleUrn(smn,topic_index,title_word.getWords(),-1);
            number_title_topic_semantic[topic_index][smn]+=2;
            number_title_topic_semantic_word[topic_index][smn][word1Index]++;
            number_title_topic_semantic_word[topic_index][smn][word2Index]++;
        }

        for(int i=0;i<topic_num;i++){
            calculator[i] = new TopicProbCalculator[maxSetSize];
            futures[i] = new Future[maxSetSize];
            for(int j=0;j<maxSetSize;j++) {
                calculator[i][j] = new TopicProbCalculator();
            }
        }
    }
    private static void updateUrn(int s,int k,BiWords word,int flag)
    {
        MustSet mustSet=mustSets.getMustSet(s);
        for (int i = 0; i < mustSet.size(); i++) {
            for(int j=i;j<mustSet.size();j++) {
                if ((mustSet.getWordstr(i).equals(word.word1) && mustSet.getWordstr(j).equals(word.word2))
                        || (mustSet.getWordstr(j).equals(word.word1) && mustSet.getWordstr(i).equals(word.word2))) {
                    if (flag == 1) {
                        number_topic_set[k][s]+=2;
                        number_per_topic[k]+=2;
                    } else {
                        number_topic_set[k][s]-=2;
                        number_per_topic[k]-=2;
                    }
                } else {
                    if (flag == 1) {
                        number_topic_set[k][s] += 0.2;
                        number_per_topic[k] += 0.2;
                    } else {
                        number_topic_set[k][s] -= 0.2;
                        number_per_topic[k] -= 0.2;
                    }
                }
            }
        }

    }
    private static void updateTitleUrn(int s,int k,BiWords word,int flag)
    {
        MustSet mustSet=mustSets.getMustSet(s);
        for (int i = 0; i < mustSet.size(); i++) {
            if(mustSet.getWordstr(i).equals(word)){
                if(flag==1){
                    number_title_topic_set[k][s]++;
                    number_per_title_topic[k]++;
                }
                else{
                    number_title_topic_set[k][s]--;
                    number_per_title_topic[k]--;
                }
            }
            else{
                if(flag==1){
                    number_title_topic_set[k][s]+=0.2;
                    number_per_title_topic[k]+=0.2;
                }
                else{
                    number_title_topic_set[k][s]-=0.2;
                    number_per_title_topic[k]-=0.2;
                }
            }
        }

    }

    public static void sampleOneRound(int round) throws ExecutionException, InterruptedException {
        long start_time = System.currentTimeMillis();
        for(int i=0;i<num_articles;i++){
            Article article = selected_articles.get(i);
            //sample one round for each article words
            List<Word> words = article.getWords();
            for(int j=0;j<words.size();j++){
                Word word = words.get(j);
                int old_topic = word.getTopicIndex();
                int old_set = word.getSemanticIndex();
                updateUrn(old_set,old_topic,word.getWords(),-1);
                MustSet mustset = mustSets.getMustSet(old_set);
                int word1Index = mustset.getWordIndex(word.getWords().word1);
                int word2Index = mustset.getWordIndex(word.getWords().word2);

                ArrayList<MustSet> mustsetList = mustSets.getMustSetListGivenWordstr(word.getWords());
                int setSize = mustsetList.size();
                double[] prob_distribution = new double[topic_num*setSize];
                for(int k=0;k<topic_num;k++){
                    for(int s=0;s<setSize;s++) {
                        MustSet ms = mustsetList.get(s);
                        int mustSetIndex = mustSets.getMustSetIndex(ms);
                        calculator[k][s].setValues(article, k,mustSetIndex, word, true);
                        futures[k][s] = service.submit(calculator[k][s]);
                    }
                }
                for(int k=0;k<topic_num;k++){
                    //word_distribution[k] = calculateTopicProb(article,k);//O(number of words);
                    for(int s=0;s<setSize;s++) {
                        double log_val = futures[k][s].get();
                        prob_distribution[k*setSize + s] = Math.exp(log_val);
                        ASSERT(prob_distribution[k*setSize + s]);
                    }
                }

                int cur_index = sample(prob_distribution,setSize);
                int topic_index = cur_index/setSize;
                int selected_index = cur_index%setSize;
                MustSet selectedMustset = mustsetList.get(selected_index);
                int semantic_index = mustSets.getMustSetIndex(selectedMustset);
                word.setTopic(topic_index);
                word.setSemantic(semantic_index);
                for(int e=0;e<num_vector_k;e++){
                    double vector_k_e1 = word.getVector1ByIndex(e);
                    double vector_k_e2 = word.getVector2ByIndex(e);
                    square_sum_x_k[old_topic][old_set][e] -= (vector_k_e2*vector_k_e2 + vector_k_e1*vector_k_e1);
                    average_x_k[old_topic][old_set][e] -= (vector_k_e1 + vector_k_e2);
                    try{
                        square_sum_x_k[topic_index][semantic_index][e] += vector_k_e2 * vector_k_e2 + vector_k_e1 * vector_k_e1;
                    }catch (Error a){
                        System.out.println(topic_index);
                        System.out.println(semantic_index);
                        System.out.println(selectedMustset);
                        System.out.println(e);
                        throw a;
                    }
                    average_x_k[topic_index][semantic_index][e] += vector_k_e1 + vector_k_e2;
                }
                article.initDistribution(old_topic,topic_index);
                updateUrn(semantic_index,topic_index,word.getWords(),+1);

                number_topic_semantic[old_topic][old_set]-=2;
                number_topic_semantic_word[old_topic][old_set][word1Index]--;
                number_topic_semantic_word[old_topic][old_set][word2Index]--;

                word1Index = selectedMustset.getWordIndex(word.getWords().word1);
                word2Index = selectedMustset.getWordIndex(word.getWords().word2);

                number_topic_semantic[topic_index][semantic_index]+=2;
                number_topic_semantic_word[topic_index][semantic_index][word1Index]++;
                number_topic_semantic_word[topic_index][semantic_index][word2Index]++;
            }
            //sample one round for each title words
            Word title_word = article.getTitleWords();
            int old_topic = title_word.getTopicIndex();
            int old_set = title_word.getSemanticIndex();
            updateTitleUrn(old_set,old_topic,title_word.getWords(),-1);
            MustSet mustset = mustSets.getMustSet(old_set);
            int word1Index = mustset.getWordIndex(title_word.getWords().word1);
            int word2Index = mustset.getWordIndex(title_word.getWords().word2);

            ArrayList<MustSet> mustsetList = mustSets.getMustSetListGivenWordstr(title_word.getWords());
            int setSize = mustsetList.size();
            double[] prob_distribution = new double[topic_num*setSize];
            for(int k=0;k<topic_num;k++){
                for(int s=0;s<setSize;s++) {
                    MustSet ms = mustsetList.get(s);
                    int mustSetIndex = mustSets.getMustSetIndex(ms);
                    calculator[k][s].setValues(article, k,mustSetIndex, title_word, false);
                    futures[k][s] = service.submit(calculator[k][s]);
                }
            }
            for(int k=0;k<topic_num;k++){
                //word_distribution[k] = calculateTopicProb(article,k);//O(number of words);
                for(int s=0;s<setSize;s++) {
                    prob_distribution[k*setSize + s] = Math.exp(futures[k][s].get());
                    ASSERT(prob_distribution[k*setSize + s]);
                }
            }

            int cur_index = sample(prob_distribution,setSize);
            int topic_index = cur_index/setSize;
            int selected_index = cur_index%setSize;
            MustSet selectedMustset = mustsetList.get(selected_index);
            int semantic_index = mustSets.getMustSetIndex(selectedMustset);
            title_word.setTopic(topic_index);
            title_word.setSemantic(semantic_index);
            for(int e=0;e<num_vector_r;e++){
                double vector_k_e1 = title_word.getVector1ByIndex(e);
                double vector_k_e2 = title_word.getVector2ByIndex(e);
                square_sum_x_r[old_topic][old_set][e] -= (vector_k_e2*vector_k_e2 + vector_k_e1*vector_k_e1);
                average_x_r[old_topic][old_set][e] -= (vector_k_e1 + vector_k_e2);
                try{
                    square_sum_x_r[topic_index][semantic_index][e] += vector_k_e2 * vector_k_e2 + vector_k_e1 * vector_k_e1;
                }catch (Error a){
                    System.out.println(topic_index);
                    System.out.println(semantic_index);
                    System.out.println(selectedMustset);
                    System.out.println(e);
                    throw a;
                }
                average_x_r[topic_index][semantic_index][e] += vector_k_e1 + vector_k_e2;
            }
            article.initTitleDistribution(old_topic,topic_index);
            updateTitleUrn(semantic_index,topic_index,title_word.getWords(),+1);

            number_title_topic_semantic[old_topic][old_set]-=2;
            number_title_topic_semantic_word[old_topic][old_set][word1Index]--;
            number_title_topic_semantic_word[old_topic][old_set][word2Index]--;

            word1Index = selectedMustset.getWordIndex(title_word.getWords().word1);
            word2Index = selectedMustset.getWordIndex(title_word.getWords().word2);

            number_title_topic_semantic[topic_index][semantic_index]+=2;
            number_title_topic_semantic_word[topic_index][semantic_index][word1Index]++;
            number_title_topic_semantic_word[topic_index][semantic_index][word2Index]++;
            if(i%1000==999){
                System.out.println("Sample Round: "+round+" has sampled articles: "+i);
            }
        }
        long end_time = System.currentTimeMillis();
        System.out.println("Sample round: "+round+" completed,\t Time Used: "+(end_time-start_time) +" ms");
        /*if(round%number_of_burnin==0){
            calLatentVariables(false);
        }*/
    }
    private static int sample(double[] p,int setSize){
        for (int k= 1; k < topic_num * setSize; k++) {
            p[k]+=p[k-1];
        }
        double u= Math.random()*p[topic_num * setSize-1];
        for (int k = 0; k < topic_num * setSize; k++) {
            if(u<=p[k]){
                return k;
            }
        }
        return -1;
    }

    private static double calculateTitleTopicProb(Article article, int t,int s,Word word) {
        int n_d_yd = number_per_title_topic[t];//
        int n_d_t = article.getTopicByIndex(t);
        double n_t_s = (number_topic_semantic[t][s] + beta_t)/(number_per_topic[t] + S*beta_t);
        double gaussian_quote_part = 0;
        for(int i=0;i<num_vector_r;i++){
            double gaussian_quote = GaussianQote(t,s,i, square_sum_x_r, average_x_r,word,number_title_topic_semantic[t][s]);
            gaussian_quote_part += gaussian_quote;
        }
        return Math.log(number_topic_semantic[t][s]+lr_r)+gaussian_quote_part;
    }

    private static double calculateTopicProb(Article article,int t,int s,Word word) {
        Word title = article.getTitleWords();
        double n_d_yd = number_topic_semantic[title.getTopicIndex()][title.getSemanticIndex()];
        int n_d_t = article.getTopicByIndex(t);
        double n_t_s = (number_topic_semantic[t][s] + beta_t)/(number_per_topic[t] + S*beta_t);
        double gaussian_quote_part = 0;
        for(int i=0;i<num_vector_k;i++){
            double gaussian_quote = GaussianQote(t,s,i,square_sum_x_k,average_x_k,word,number_topic_semantic[t][s]);
            gaussian_quote_part += gaussian_quote;
        }
        ASSERT(gaussian_quote_part);
        return Math.log(n_d_yd) + Math.log(n_d_t+alpa_t)+Math.log(n_t_s)+gaussian_quote_part;
    }

    private static double GaussianQote(int t,int s, int e, double[][][] square_sum_x, double[][][] average_x,Word word,int n) {
        if(t != word.getTopicIndex() || s!=word.getSemanticIndex())return 0;
        double average_of_x = n>0 ? average_x[t][s][e]/n : 0;
        double alpha_n = getAlphaN(n);
        double second_part_of_beta = n>0 ? 0.5*(square_sum_x[t][s][e] - average_of_x*average_x[t][s][e] ) : 0;
        double beta_n = getBetaN(n,average_of_x,second_part_of_beta);
        double kappa_n = kappa_0 + n;

        //calculate n_quote
        double vector_k_e1 = word.getVector1ByIndex(e);
        double vector_k_e2 = word.getVector2ByIndex(e);
        int n_quote;
        double average_x_quote,square_sum_x_quote;
        n_quote = n - 2;
        average_x_quote = average_x[t][s][e] - vector_k_e1 - vector_k_e2;
        square_sum_x_quote = square_sum_x[t][s][e] - vector_k_e1*vector_k_e1 - vector_k_e2*vector_k_e2;
        average_of_x = n_quote>0? average_x_quote / n_quote : 0;
        second_part_of_beta = n_quote>0 ? 0.5*(square_sum_x_quote - average_of_x*average_x_quote) : 0;
        double alpha_n_quote = getAlphaN(n_quote);
        double beta_n_quote = getBetaN(n_quote,average_of_x,second_part_of_beta);
        double kappa_n_quote = kappa_0 + n_quote;

        double alpha_part_of_gaussian_quote = Gamma.logGamma(alpha_n) - Gamma.logGamma(alpha_n_quote);
        ASSERT(alpha_part_of_gaussian_quote);
        double beta_part_of_gaussian_quote = alpha_n_quote * Math.log(beta_n_quote) - alpha_n * Math.log(beta_n);
        ASSERT(beta_part_of_gaussian_quote);
        double kappa_part_of_gaussian_quote = 0.5 * Math.log(kappa_n_quote/kappa_n);
        ASSERT(kappa_part_of_gaussian_quote);
        double gaussian_quote_value = alpha_part_of_gaussian_quote + beta_part_of_gaussian_quote + kappa_part_of_gaussian_quote
                - LOG_2_PI;
        ASSERT(gaussian_quote_value);
        return gaussian_quote_value;
    }

    private final static double getBetaN(int n,double average_x,double second_part) {
        double third_part = ( kappa_0*n*(average_x - mu_0)*(average_x - mu_0) )/ ( 2*(kappa_0 + n) );
        return beta_0 + second_part + third_part;
    }

    private final static double getAlphaN(int n) {
        return alpha_0 + n/2.0;
    }

    public static void startTrain() throws ExecutionException, InterruptedException, IOException {
        for(int i=1;i<=sample_round;i++){
            /*if(i%1==0){
                System.out.println("train sample step total: "+i+" round.");
            }*/
            sampleOneRound(i);
            if(i%burin_period==0){
                calLatentVariables(false);
                number_of_burnin++;
                predict();
            }
        }
        calLatentVariables(true);
    }
    /**
     * 作用：根据计数变量来更新模型变量
     * @param isFinalIteration 是否是最后一次迭代，如果是就要把前面几次保存的结果求平均
     */
    private static void calLatentVariables(boolean isFinalIteration)
    {
        number_of_burnin++;
        for (int m = 0; m < selected_articles.size(); m++) {
            selected_articles.get(m).updateDistribution(isFinalIteration,number_of_burnin);
        }
    }
    public static List<Article> readArticles(String path) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path),"utf-8"));
        List<Article> articles = new ArrayList<Article>();
        String line;
        int line_number=0;
        final int other_max_article = 20000;
        int other_articl = 0;
        while((line = br.readLine())!=null){
            line = line.toLowerCase();
            String[] splits = line.split("@#@#@");
            String url = splits[0];
            if(!articleStandardResult.contains(url)){
                if(other_articl>=other_max_article)continue;
                else other_articl++;
            }
            String title = splits[1];
            String lang = splits[2];
            String[] zhsplits = splits[3].substring(1,splits[3].length()-1).split(", ");

            Map<String,Integer> zhWordFreq = new HashMap<String, Integer>();
            for(String word:zhsplits){
                if(zhWordFreq.containsKey(word)){
                    Integer freq = zhWordFreq.get(word);
                    zhWordFreq.put(word,freq+1);
                }else{
                    zhWordFreq.put(word,1);
                }
            }

            List<SingleWord> words = new ArrayList<SingleWord>();
            for(String key:zhWordFreq.keySet()){
                Integer freq = zhWordFreq.get(key);
                if(freq<filter_word_frequency)continue;
                Vector vector = zhMap.get(key);
                if(vector==null)continue;
                for(int tt=0;tt<freq;tt++) {
                    SingleWord word = new SingleWord(key, 1, vector);
                    words.add(word);
                }
            }
            String[] ensplits = splits[4].substring(1,splits[4].length()-1).split(", ");

            Map<String,Integer> enWordFreq = new HashMap<String, Integer>();
            for(String word:ensplits){
                if(enWordFreq.containsKey(word)){
                    Integer freq = enWordFreq.get(word);
                    enWordFreq.put(word,freq+1);
                }else{
                    enWordFreq.put(word,1);
                }
            }

            for(String key:enWordFreq.keySet()){
                Integer freq = enWordFreq.get(key);
                if(freq<filter_word_frequency)continue;
                Vector vector = enMap.get(key);
                if(vector==null)continue;
                for(int tt=0;tt<freq;tt++) {
                    SingleWord word = new SingleWord(key, 1, vector);
                    words.add(word);
                }
            }
            List<Term> parse = NlpAnalysis.parse(title.toLowerCase());
            Map<String,Vector> vectorMap = "en".equals(splits[2]) ? enTitleMap : zhTitleMap;
            List<SingleWord> title_words = new ArrayList<SingleWord>();
            for(Term term:parse){
                String name = term.getName();
                if(!term.getNatrue().toString().startsWith("w") && vectorMap.containsKey(name)){
                    Vector vector = vectorMap.get(name);
                    if(vector==null || stopwords.contains(name))continue;
                    SingleWord word = new SingleWord(name,1,vector);
                    title_words.add(word);
                }
            }

            if(words.size()<2 || title_words.size()<=0)continue;
//          System.out.println("Article: "+line_number+" has total: "+words.size()+" words.");
            for(Iterator<SingleWord> it = words.iterator();it.hasNext();){
                SingleWord word = it.next();
                if(stopwords.contains(word.name)){
                    it.remove();
                }
            }
//            System.out.println("Article left total: "+words.size()+" words after remove stop words.");
            List<Word> biWords = new ArrayList<Word>();
            for(int i=0;i<words.size();i++){
                final SingleWord word1 = words.get(i);
                PriorityQueue<SingleWord> queue = new PriorityQueue<SingleWord>(100, new Comparator<SingleWord>() {
                    public int compare(SingleWord o1, SingleWord o2) {
                        return o1.similarity > o2.similarity ? -1 :1;
                    }
                });
                for(int j=i;/*j<i+semantic_windows && */j<words.size();j++){
                    SingleWord word2 = words.get(j);
                    word2.similarity = getDotDistance(word1.vector.getVector(),word2.vector.getVector(),num_vector_k);
                    queue.add(word2);
                }
                for(int j=0;!queue.isEmpty();j++){
                    SingleWord word2 = queue.poll();
//                    System.out.println(word2.distance);
                    Word biword = new Word(topic_num,new BiWords(word1.name,word2.name),word1.vector,word2.vector,1);
//                    dictionary.add(biword.getWords());
                    biWords.add(biword);
                }
            }
            Word titleBiWord;
            if(title_words.size()==1){
                SingleWord word = title_words.get(0);
                titleBiWord = new Word(topic_num,new BiWords(word.name,word.name),word.vector,word.vector,1);
            }else{
                SingleWord word1 = title_words.get(0);
                SingleWord word2 = title_words.get(1);
                titleBiWord = new Word(topic_num,new BiWords(word1.name,word2.name),word1.vector,word2.vector,1);
            }
//            dictionary.add(titleBiWord.getWords());
            Article article = new Article(topic_num,url,lang,title,biWords,biWords.size(),titleBiWord,title_words);
            articles.add(article);
            articleMap.put(url,article);
            line_number++;
            if(line_number%1000==0){
                System.out.println("Articles Have Finished Readed: "+line_number);
            }
            if(line_number >= 20000)break;
        }
        br.close();
        num_articles = articles.size();
        System.out.println("Articles Have Readed: "+num_articles);
        return articles;
    }
    public static Map<String, Vector> readMap(String path,int number_dimension) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path),"utf-8"));
        Map<String,Vector> map = new HashMap<String, Vector>();
        String line;
        while((line = br.readLine())!=null){
            String[] splits = line.split(" ");
            String key = splits[0];
            List<Double> vec = new ArrayList<Double>();
            for(int i=1;i<splits.length;i++){
                vec.add(Double.valueOf(splits[i]));
            }
            if(vec.size()!=number_dimension)throw new IOException("vector length is not right:"+key+" :"+vec.size());
            map.put(key,new Vector(vec));

        }
        br.close();
        return map;
    }

    private static Map<String,Set<String>> readSynSets(String path) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path),"utf-8"));
        Map<String,Set<String>> synsetMap = new HashMap<String, Set<String>>();
        String line;
        while((line = br.readLine())!=null){
            String[] splits = line.split("@#@#@");
            JSONArray array = new JSONArray(splits[2]);
            Set<String> sets = new HashSet<String>();
            for(int i=0;i<array.length();i++){
                JSONObject obj = array.getJSONObject(i);
                String id = obj.getString("id");
                sets.add(id);
            }
            if(synsetMap.containsKey(splits[0])){
                Set<String> set = synsetMap.get(splits[0]);
                sets.addAll(set);
            }
            synsetMap.put(splits[0],sets);
        }
        br.close();
        return synsetMap;
    }


    private static Set<String> readStopWord(List<String> stopwordslist) throws IOException {
        Set<String> stopwords = new HashSet<String>();
        for(String file:stopwordslist){
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file),"utf-8"));
            String line ;
            while((line = br.readLine())!=null){
                stopwords.add(line.trim());
            }
            br.close();
        }
        return stopwords;
    }
    private final static void ASSERT(double val){
        try{
        assert !((Double)val).isNaN() :"val can not be a NaN Nmber";
        }catch (Error e){
            throw e;
        }
    }
    static class SingleWord{
        public String name;
        public int freqence;
        public Vector vector;
        public double similarity;
        public SingleWord(String name,int freqence,Vector vector){
            this.name = name;
            this.freqence = freqence;
            this.vector = vector;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
