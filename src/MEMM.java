import java.util.HashMap;


public class MEMM {
	public static enum State {
		POS,
		NEU,
		NEG
	}
	
	private String[] observations; //sentences in review
	private HashMap<MEMM.State, HashMap<MEMM.State, Float>> TPMap;
	private HashMap<MEMM.State, Float> StartProbs;
}
