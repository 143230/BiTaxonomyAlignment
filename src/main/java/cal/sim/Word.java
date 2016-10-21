package cal.sim;

/**
 * Created by root on 9/1/16.
 */
public class Word {
    public static int K;
    private int frequency;
    private BiWords biWords;
    private Vector vector1;
    private Vector vector2;
    private int topic;
    private int semantic;
    public Word(int k,BiWords biWords,Vector vector1,Vector vector2,int frequency){
        K = k;
        this.biWords = biWords;
        this.vector1 = vector1;
        this.vector2 = vector2;
        this.frequency = frequency;
    }
    public BiWords getWords(){return biWords;}
    public void setTopic(int topic){this.topic = topic;}
    public void setSemantic(int semantic){this.semantic = semantic;}
    public int getTopicIndex(){
        return topic;
    }
    public int getSemanticIndex(){
        return semantic;
    }
    public double getVector1ByIndex(int index){
        return vector1.get(index);
    }
    public double getVector2ByIndex(int index){
        return vector2.get(index);
    }

    @Override
    public String toString() {
        return biWords.toString();
    }
}
