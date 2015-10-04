package net.floodlightcontroller.sdn;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.routing.Link;

public class SdnGraph {
	private int numSwitches;
	private String[][] hostIP; //contains the host IP with the switches and port number
	private Link[] allNetworkLinks; // array of all links in the network
	private class SrcPortPair{
		public long src;
		public long dst;
		public short port;
		SrcPortPair(long srcI, long dstI, short portI){
			src = srcI;
			dst = dstI;
			port = portI;
		}
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

	public void print(long src, long dest){
		ArrayList<ArrayList<SrcPortPair>> shortPaths = calculateShortestPaths(src, dest);	
		for (int i = 0; i < shortPaths.size(); i++){
			System.out.println("Path " + i);
			for (int j = 0; j < shortPaths.get(i).size(); j++){
				System.out.println("Src " + shortPaths.get(i).get(j).src + " Dst " + shortPaths.get(i).get(j).dst);
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
					if (firstPath == true){ //nothing exists in the first path
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
					if (pulledList.size() + 1 < numSwitches){
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
