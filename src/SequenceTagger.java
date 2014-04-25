import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;


public class SequenceTagger {
	private HMM hmm;
	
	private HashMap<HMM.State, HashMap<HMM.State, Float>> TPmap;
	private int numEntries = 0;
		
	public SequenceTagger() {
		hmm = new HMM();
		TPmap = new HashMap<HMM.State, HashMap<HMM.State, Float>>();
	}
	
	/*
	 * 
	 */
	public void parseSentimentLexicon() {
		
	}
	
	/*
	 * Train the tagger on the training data
	 * Calculates TP and EP values
	 */
	public void train(String filename) {
		File file = new File(filename);
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(file));
			String line;
			HMM.State prevState = null;
			
			while((line = reader.readLine()) != null) {
				HMM.State state = null;
				if(line.startsWith("pos")) {
					state = HMM.State.POS;
				} else if(line.startsWith("neg")) {
					state = HMM.State.NEG;
				} else if(line.startsWith("neu")) {
					state = HMM.State.NEUT;
				}
				
				if(state != null) {
					numEntries++;
					updateTPMap(state, prevState);
					prevState = state;
					
					ArrayList<String> featureVector = new ArrayList<>();
					for(String word: line.split(" ")) {
						
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Normalize counts to probabilities
		for(HashMap<HMM.State, Float> map: TPmap.values()) {
			for(HMM.State state: map.keySet()) {
				map.put(state, map.get(state)/(numEntries-1));
			}
		}
		
		// Test that they sum to 1
		float sum = 0;
		for(HMM.State state1: TPmap.keySet()) {
			System.out.println(state1 + ":");
			
			HashMap<HMM.State, Float> map = TPmap.get(state1);
			for(HMM.State state2: map.keySet()) {
				System.out.print(state2 + ": " + map.get(state2) + ", ");
				sum += map.get(state2);
			}
			System.out.println();
		}
		System.out.println(sum);
	}
	
	/*
	 * Increments the count for each TP entry according to the transition
	 */
	private void updateTPMap(HMM.State state, HMM.State prevState) {
		if(state == null || prevState == null) return;
		
		if(!TPmap.containsKey(prevState)) {
			TPmap.put(prevState, new HashMap<HMM.State, Float>());
		}
		
		if(!TPmap.get(prevState).containsKey(state)) {
			TPmap.get(prevState).put(state, 1f);
		} else {
			float prob = TPmap.get(prevState).get(state);
			TPmap.get(prevState).put(state, prob+1);
		}
	}
	
	public static void main(String[] args) {
		SequenceTagger tagger = new SequenceTagger();
		tagger.train("src/training_data.txt");
		
		System.out.println("Done");
	}
}
