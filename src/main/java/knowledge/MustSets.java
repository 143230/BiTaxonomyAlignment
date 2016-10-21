package knowledge;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import cal.sim.BiCategorySim;
import cal.sim.BiWords;


/**
 * This class implements knowledge in the forms of must-sets.
 */
public class MustSets {
	public ArrayList<MustSet> mustsetList = null;
	public Map<MustSet, Integer> mustsetToIndexMap = null;
	public Map<BiWords, ArrayList<MustSet>> wordstrToMustsetListMap = null;

	public MustSets() {
		mustsetList = new ArrayList<MustSet>();
		mustsetToIndexMap = new HashMap<MustSet, Integer>();
		wordstrToMustsetListMap = new HashMap<BiWords, ArrayList<MustSet>>();
	}

	public void getMustSets(Set<BiWords> dictionary)
	{
		ArrayList<String> mustsetLines=readDocument(BiCategorySim.knowledgeFile);
		addMustSetsFromDocument(mustsetLines);
		addSingletonMustSets(dictionary);
	}

	/**
	 * ���ĵ��ж�ȡmustset Get a must-set from a line, e.g., {price, cheap, expensive}.
	 * @param mustsetLines
	 */
	private void addMustSetsFromDocument(ArrayList<String> mustsetLines) {

		for (String line : mustsetLines) {

			line = line.replace("{", "");
			line = line.replace("}", "");
			String[] strSplits = line.split("[\\s,] ");
			if(strSplits.length<2)continue;
			MustSet mustset = new MustSet();
			for (String split : strSplits) {
				String wordstr = split.trim();
				mustset.wordstrsList.add(wordstr);
			}

			mustsetToIndexMap.put(mustset, mustsetList.size());
			mustsetList.add(mustset);
			for (int i=0;i<strSplits.length;i++) {
				for(int j=i;j<strSplits.length;j++) {
					BiWords bws = new BiWords(strSplits[i],strSplits[j]);
					addWordIntoMap(bws, mustset);
				}
			}
		}
	}

	/**
	 * �����������ĵ�mustset�е��ֵ��дʶ���Ϊ����mustset
	 * @param dictionary
	 */
	private void addSingletonMustSets(Set<BiWords> dictionary) {
		for (BiWords v : dictionary) {
			if(!wordstrToMustsetListMap.containsKey(v)){
				MustSet mustset = new MustSet(v);
				mustsetToIndexMap.put(mustset, mustsetList.size());
				mustsetList.add(mustset);

				ArrayList<MustSet> mustsets=new ArrayList<MustSet>();
				mustsets.add(mustset);
				wordstrToMustsetListMap.put(v, mustsets);
			}
		}
	}


	/**
	 *
	 * ���wordstr�����mustset��wordstrToMustsetListMap��
	 * @param wordstr
	 * @param mustset
	 */
	public void addWordIntoMap(BiWords wordstr, MustSet mustset) {
		if (!wordstrToMustsetListMap.containsKey(wordstr)) {
			wordstrToMustsetListMap.put(wordstr, new ArrayList<MustSet>());
		}
		wordstrToMustsetListMap.get(wordstr).add(mustset);
	}

	public MustSet getMustSet(int index) {
		assert (index < this.size() && index >= 0) : "Index is not correct!";
		return mustsetList.get(index);
	}

	/**
	 * ����mustset�ڼ����б�ʾ������
	 * @param mustset
	 * @return
	 */
	public int getMustSetIndex(MustSet mustset) {
		assert (mustsetToIndexMap.containsKey(mustset)) : "This mustset is not in the mustsets!";
		return mustsetToIndexMap.get(mustset);
	}

	/**
	 * Get the list of must-sets that contain this word.
	 *
	 * @return
	 */
	/**
	 * ����wordstr����������MustSet
	 * @param wordstr
	 * @return
	 */
	public ArrayList<MustSet> getMustSetListGivenWordstr(BiWords wordstr) {
		if (!wordstrToMustsetListMap.containsKey(wordstr)) {
			return new ArrayList<MustSet>();
		} else {
			return wordstrToMustsetListMap.get(wordstr);
		}
	}

	public int size() {
		return mustsetList.size();
	}

	/*public void printMustSets(ArrayList<String> dictionary)
	{
		for (String v : dictionary) {
			ArrayList<MustSet> mustset=getMustSetListGivenWordstr(v);
			String line=v+"\t";
			for (MustSet mustSet2 : mustset) {
				line+=mustSet2.toString()+"\t"+mustSet2.getWordIndex(v);
			}
			System.out.println(line);
		}
	}*/

	@Override
	public String toString() {
		StringBuilder sbMustSets = new StringBuilder();
//		System.out.println(mustsetList.size());
		for (MustSet mustset : mustsetList) {
			sbMustSets.append(mustset.toString());
			sbMustSets.append("\n");
		}
		return sbMustSets.toString();
	}

	/**
	 * ���ã���һƪ�ĵ���һ������Ϊ��λ����ArrayList�У�Ŀ��Ϊ�˷ִʡ�
	 * @param documentName:Դ�ļ��ľ���·����
	 */
	public static ArrayList<String> readDocument(String documentName)
	{
		try {
			BufferedReader reader=new BufferedReader(new FileReader(new File(documentName)));
			String line;
			ArrayList<String> documentLines=new ArrayList<String>();
			while((line=reader.readLine())!=null)
			{

				documentLines.add(line.trim());
			}
			return documentLines;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}
