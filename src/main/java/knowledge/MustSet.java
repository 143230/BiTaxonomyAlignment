package knowledge;

import cal.sim.BiWords;

import java.util.ArrayList;



/**
 * This class implements the must-set used in GK-LDA.
 */

public class MustSet  {

	public ArrayList<String> wordstrsList = null;

	public MustSet() {
		wordstrsList = new ArrayList<String>();
	}
	/**
	 * Construct a singleton must-set.
	 */
	public MustSet(BiWords wordstr) {
		wordstrsList = new ArrayList<String>();
		wordstrsList.add(wordstr.word1);
		wordstrsList.add(wordstr.word2);
	}

	public String getWordstr(int index) {
		return wordstrsList.get(index);
	}

	public int size() {
		return wordstrsList.size();
	}


	/**
	 * @param wordstr
	 * @return the index of wordstr in mustset
	 */
	public int getWordIndex(String wordstr) {
		for (int i = 0; i < wordstrsList.size(); i++) {
			if(wordstrsList.get(i).equals(wordstr))
				return i;
		}
		return -1;
	}

	@Override
	public String toString() {
		return wordstrsList.toString();
	}


}
