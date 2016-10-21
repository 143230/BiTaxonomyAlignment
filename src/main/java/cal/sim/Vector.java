package cal.sim;

import java.util.List;

/**
 * Created by root on 9/1/16.
 */
public class Vector {
    private List<Double> vectors;
    public Vector(List<Double> vectors){
        this.vectors = vectors;
    }
    public void setVector(List<Double> vectors){
        this.vectors = vectors;
    }
    public List<Double> getVector(){
        return vectors;
    }
    public double get(int index){
        return vectors.get(index);
    }
}
