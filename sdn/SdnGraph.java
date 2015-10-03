package net.floodlightcontroller.sdn;

import java.io.BufferedReader;
import java.io.IOException;

public class SdnGraph {
	int numSwitches;
	
	public SdnGraph(int numS){
		numSwitches = numS;
	}
	
	public int buildGraph(BufferedReader br) throws IOException{
		String inputStr;
		while ((inputStr = br.readLine()) != null){
			System.out.println(inputStr);
		}
		return numSwitches;
	}
}
