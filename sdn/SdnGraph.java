package net.floodlightcontroller.sdn;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.openflow.util.HexString;

import net.floodlightcontroller.routing.Link;

public class SdnGraph {
	private int numSwitches;
	private String[][] hostIP; //contains the host IP with the switches and port number
	private Link[] allNetworkLinks; // array of all links in the network
	private ArrayList<ArrayList<SrcPortPair>> shortPaths;
	
	public int getNumSwitches() { return numSwitches; }	
	public ArrayList<ArrayList<SrcPortPair>> getShortPaths(){ return shortPaths; }
	public String getHostIP(int index) { return hostIP[index][0]; }
	
	public HashSet<Long> countSwitches(){
		HashSet<Long> ans = new HashSet<Long>();
		for (int i = 0; i < allNetworkLinks.length; i++){
			ans.add(allNetworkLinks[i].getSrc());
			ans.add(allNetworkLinks[i].getDst());
		}
		return ans;
	}
	
	public SdnGraph(int numS){
		numSwitches = numS;
	}
	
	public void setNumLinks(int numL){
		allNetworkLinks = new Link[numL];
	}
	
	public void addLink(int index, Link toAdd){
		allNetworkLinks[index] = toAdd;
	}
		
	public void buildHostIP(BufferedReader br) throws IOException{
		hostIP = new String[numSwitches][];
		String inputStr;
		for(int i = 0; (inputStr = br.readLine()) != null; i++){
			hostIP[i] = inputStr.split(", ");
		}
	}

	private long getClosestSwitchFromIP(String strInput){
		for (int i = 0; i < hostIP.length; i++){
			if (strInput.compareTo(hostIP[i][0]) == 0){
				return HexString.toLong(hostIP[i][1]);
			}
		}
		return -1; // should not get here
	}
	
	public void calculatePaths(String src, String dst){
		long srcSwitch = getClosestSwitchFromIP(src);
		long dstSwitch = getClosestSwitchFromIP(dst);
		
		// calculate the shortest paths
		shortPaths = calculateShortestPaths(srcSwitch, dstSwitch);	
		if (shortPaths.isEmpty() == true)
			return;
		
		// add the last hop to each path
		for (int i = 0; i < shortPaths.size(); i++){
			for (int j = 0; j < numSwitches; j++){
				long lDst  = HexString.toLong(hostIP[j][1]);
				if (dst.compareTo(hostIP[j][0]) == 0 && lDst == dstSwitch){
					shortPaths.get(i).add(new SrcPortPair(dstSwitch, -1, Short.parseShort(hostIP[j][2])));
				}
			}
		}
	}
	
	public ArrayList<ArrayList<SrcPortPair>> calculateShortestPaths(long srcSwitch, long dstSwitch){
		ArrayList<ArrayList<SrcPortPair>> ans = new ArrayList<ArrayList<SrcPortPair>>();
		ConcurrentLinkedQueue<ArrayList<SrcPortPair>> queue = new ConcurrentLinkedQueue<ArrayList<SrcPortPair>>();
		boolean firstPath = true;
		ArrayList<SrcPortPair> pulledList = new ArrayList<SrcPortPair>();
		while (true){
			if (firstPath == false){
				pulledList = queue.poll(); // pull next element in queue
				if (pulledList == null){
					break; // nothing more to check
				}
				srcSwitch = pulledList.get(pulledList.size()-1).dst; //get value from last element of list
			}
			for (int i = 0; i < allNetworkLinks.length; i++){				
				if (dstSwitch == srcSwitch){
					// no solution or solution of the same size
					if (ans.isEmpty() == true || ans.get(0).size() == pulledList.size()){ 
						ans.add(pulledList);
					}
					// better solution
					else if (ans.get(0).size() > pulledList.size()){
						ans = new ArrayList<ArrayList<SrcPortPair>>();
						ans.add(pulledList);
					}
					break;
				}
				else if (srcSwitch == allNetworkLinks[i].getSrc()){
					SrcPortPair toAdd = new SrcPortPair(srcSwitch, allNetworkLinks[i].getDst(), allNetworkLinks[i].getSrcPort());
					if (firstPath == true){ //nothing exists in the first path but could be more than one choice from first node
						pulledList = new ArrayList<SrcPortPair>();
					}
					boolean bContinue = false;
					for (int j = 0; j < pulledList.size(); j++){ // don't go backwards
						if (pulledList.get(j).src == toAdd.dst){
							bContinue = true;
						}
					}
					if (bContinue == true) 
						continue;
					// don't add if the path is longer than the longest possible path
					if (pulledList.size() + 1 < numSwitches && (ans.isEmpty() == true || pulledList.size() > ans.get(0).size())){
						ArrayList<SrcPortPair> pulledListPlusOne = new ArrayList<SrcPortPair>(pulledList);
						pulledListPlusOne.add(toAdd);
						queue.add(pulledListPlusOne);
					}
				}
			}
			firstPath = false;
		}
		return ans;
	}
}
