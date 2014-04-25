import java.util.HashMap;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.*;

public class HMM {
	public static enum State {
		POS,
		NEG,
		NEUT
	}
	
	private State state;
	private HashMap<HMM.State, HashMap<HMM.State, Float>> TPMap;
	private HashMap<HMM.State, Float> StartProbs;
	private ArrayList<double[]> EPs;	//EPs.get(a)[0] gives probability of "Pos"
																	//[1] gives "Neu" and [2] gives "Neg"
	
	private HashMap<String, Float> lexiconPolarities;
	
	private static final float NEUTRAL_BENCHMARK = 2.0f;
	
	public HMM(HashMap<HMM.State, HashMap<HMM.State, Float>> transitions, HashMap<HMM.State, Float> SPs) {	
		TPMap = transitions;
		StartProbs = SPs;
	}
	
	public void addPolarities(HashMap<String, Float> data) {
		lexiconPolarities = data;
	}
	
	public void runHMM(String data) { //Prints output to screen in forms of "Pos", "Neu", "Neg"
		
		Scanner in = new Scanner(data);
		StringBuffer buffer = new StringBuffer("");
		Pattern header = Pattern.compile("[a-z]*_[a-z]*_[0-9]*");	//matches on review headers eg electronics_neg_7
		
		while(in.hasNextLine()) {
			String next = in.nextLine();
			if(next.equals("")) {						//If we've come to one of the empty lines
				
				extractEPs(buffer.toString());			//That's the end of the review so pass it to the EP extractor
				//TODO: Run the Viterbi algorithm since the global EPs was set by the above
				
				buffer.delete(0, buffer.length());		//Clear the buffer
			}
			else if(header.matcher(next).matches()){	//It's the name of a review so ignore it
			}
			else {										//It's a sentence of the review
				buffer.append(next.substring(4));		//Trim the "neu " off the front and add it to the buffer
			}
		}
	}
	
	public void extractEPs(String data) {				//Sets the global variable EPs based on the review
		float score = 0;
		ArrayList<double[]> emissions = new ArrayList<double[]>();
		
		Scanner reader = new Scanner(data);
		while(reader.hasNextLine()){
			String sentence = reader.nextLine();
			String processed = sentence.replaceAll("([(),!.?;:])", " $1 ");	//add padding around punctuation
			String[] words = processed.split("\\s+");						//split on whitespace
			for(int a = 0; a < words.length; a++) {
				if(lexiconPolarities.containsKey(words[a])) {
					score += lexiconPolarities.get(words[a]);
				}
			}
			
			//TODO: Take this score and transform it into the emission probabilities that we want
		}
		
		EPs = emissions;
	}
}
