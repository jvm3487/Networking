package net.floodlightcontroller.sdn;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.Link;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.Wildcards.Flag;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.U16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Sdn implements IFloodlightModule, ILinkDiscoveryListener {
    protected static Logger log = LoggerFactory.getLogger(Sdn.class);

    protected IFloodlightProviderService floodlightProvider;
    protected ILinkDiscoveryService linkDiscoverer;
    private SdnGraph graph;
    
    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }
    
    private void assignSwitchRules(ArrayList<SrcPortPair> oneSolution, String dstIP){
    	// assign rule to all the switches
		for (int i = 0; i < oneSolution.size(); i++){
			OFMatch match = new OFMatch();
			match.setWildcards(Wildcards.FULL.matchOn(Flag.DL_TYPE).matchOn(Flag.NW_DST).withNwDstMask(32));
			match.setDataLayerType(Ethernet.TYPE_IPv4);
			match.setNetworkDestination(IPv4.toIPv4Address(dstIP));

			ArrayList<OFAction> actions = new ArrayList<OFAction>();
			OFActionOutput action = new OFActionOutput().setPort(oneSolution.get(i).port);
			actions.add(action);

			OFFlowMod flowMod = new OFFlowMod();
			flowMod.setHardTimeout((short)0);
			flowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
			flowMod.setCommand(OFFlowMod.OFPFC_ADD);
			flowMod.setMatch(match);
			flowMod.setActions(actions);
			flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH));
			try{
				System.out.println("Switch adding rule to " + oneSolution.get(i).src);
				IOFSwitch currentSwitch = floodlightProvider.getSwitch(oneSolution.get(i).src);
				currentSwitch.write(flowMod, null);
				currentSwitch.flush();
			}
			catch(IOException e){
				log.error("Failed to write the flowMod" + e);
			}
		}
    }
    
    private ArrayList<ArrayList<SrcPortPair>> loadBalance(ArrayList<ArrayList<SrcPortPair>> shortPaths, HashMap<Long, ArrayList<String>> switchUtil, String dstIP){
    	ArrayList<ArrayList<SrcPortPair>> bestSolns;
		int numberOfNodes = shortPaths.get(0).size();
		
		for (int i = 0; i < numberOfNodes; i++){ //all paths are the same length
				int minimumUtilization = Integer.MAX_VALUE;
				bestSolns = new ArrayList<ArrayList<SrcPortPair>>();
				for (int j = 0; j < shortPaths.size(); j++){
					if (switchUtil.get(shortPaths.get(j).get(i).src) == null){
						if (minimumUtilization > 0){
							// toss old solutions
							minimumUtilization = 0;
							bestSolns = new ArrayList<ArrayList<SrcPortPair>>();
						}
						if (minimumUtilization == 0){
							bestSolns.add(shortPaths.get(j));
						}
					}
					else if (switchUtil.get(shortPaths.get(j).get(i).src).contains(dstIP) == true){
					if (minimumUtilization != -1){
						// toss old solutions
						bestSolns = new ArrayList<ArrayList<SrcPortPair>>();
					}
					minimumUtilization = -1;
					bestSolns.add(shortPaths.get(j));
				}
				else if (switchUtil.get(shortPaths.get(j).get(i).src).size() < minimumUtilization){
					// toss old solutions
					bestSolns = new ArrayList<ArrayList<SrcPortPair>>();
					minimumUtilization = switchUtil.get(shortPaths.get(j).get(i).src).size();
					bestSolns.add(shortPaths.get(j));
				}
				else if (switchUtil.get(shortPaths.get(j).get(i).src).size() == minimumUtilization){
					bestSolns.add(shortPaths.get(j));
				}
			}     					
			shortPaths = bestSolns;
		}
		return shortPaths;
    }
    
    @Override public void linkDiscoveryUpdate(LDUpdate update) {}

    @Override public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
    	System.out.println("=====Here now!2=====");
    	Map<Link, LinkInfo> linkMap = linkDiscoverer.getLinks();
    	
    	// no known links
    	if (linkMap == null)
    		return;
    	
    	// add all links to graph
    	graph.setNumLinks(linkMap.size());
    	int index = 0;
    	for (Map.Entry<Link, LinkInfo> linkEntry : linkMap.entrySet()){
    		graph.addLink(index, linkEntry.getKey());
    		index++;
    	}
    	
    	// clear all the old rules and map switch utilization to 0
    	HashSet<Long> hashSwitches = graph.countSwitches();
    	if (hashSwitches.isEmpty() == true)
    		return;
    	
    	// map each switch to the IP address rule on it for load balancing calculations
    	HashMap<Long, ArrayList<String>> switchUtil = new HashMap<Long, ArrayList<String>>();
    	for (Long lSwitch : hashSwitches){
    		IOFSwitch currentSwitch = floodlightProvider.getSwitch(lSwitch);
    		currentSwitch.clearAllFlowMods();
    		switchUtil.put(lSwitch, null);
    	}
    	    	
    	// calculate the shortest path for each IP address
    	for (int ipOne = 0; ipOne < graph.getNumSwitches(); ipOne++){
    		for (int ipTwo = ipOne + 1; ipTwo < graph.getNumSwitches(); ipTwo++){
    			   				
    			String srcIP = graph.getHostIP(ipOne);
    			String dstIP = graph.getHostIP(ipTwo);
    			System.out.println("srcIP " + srcIP + " dstIP " + dstIP);
    			
    			// calculate forward and backward 
    			for (int pathDirection = 0; pathDirection < 2; pathDirection++){ 
    				if (pathDirection == 1){ // and the reverse
    					String temp = new String(dstIP);
    					dstIP = srcIP;
    					srcIP = temp;
    					System.out.println("srcIP " + srcIP + " dstIP " + dstIP);
    				}
    					
   					graph.calculatePaths(srcIP, dstIP);
    				
    				ArrayList<ArrayList<SrcPortPair>> shortPaths = graph.getShortPaths();
    	
    				// no solution for these nodes
    				if (shortPaths.isEmpty() == true)
    					continue;
    	
    				//print solution temporarily
    				for (int i = 0; i < shortPaths.size(); i++){
    					System.out.println("Path " + i);
    					for (int j = 0; j < shortPaths.get(i).size(); j++){
    						System.out.println("Src " + shortPaths.get(i).get(j).src + " Dst " + shortPaths.get(i).get(j).dst + " DstPort " + shortPaths.get(i).get(j).port);
    					}
    				}
    	        	    				
    				// check to see if path exists already and use the same path if it does
    				// if not choose the least occupied path
    				shortPaths = loadBalance(shortPaths, switchUtil, dstIP);
    				    				
    				// if more than two correct then just choose the first one
    				ArrayList<SrcPortPair> oneSolution = new ArrayList<SrcPortPair>(shortPaths.get(0));
    				
    				// update map with new usage
    				for (SrcPortPair eachNode : oneSolution){
    					ArrayList<String> currentValue = switchUtil.get(eachNode.src);
    					if (currentValue == null){
    						currentValue = new ArrayList<String>();
    					}
    					if (currentValue.contains(dstIP) == false)
    						currentValue.add(dstIP);
    					switchUtil.put(eachNode.src, currentValue);
    				}
    				
    				// assign rule to all the switches
    				assignSwitchRules(oneSolution, dstIP);    				
    			}
    		}
    	}
    }
    
    // IFloodlightModule
    
    @Override public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // We don't provide any services, return null
        return null;
    }

    @Override public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // We don't provide any services, return null
        return null;
    }

    @Override public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        linkDiscoverer = context.getServiceImpl(ILinkDiscoveryService.class);

        Map<String, String> configParams = context.getConfigParams(this);
        String NumSwitches = configParams.get("numSwitches");
        String TopologyInfo = configParams.get("topologyFile");
        
        try{
        	graph = new SdnGraph(Integer.parseInt(NumSwitches));
        	BufferedReader br = new BufferedReader(new FileReader(TopologyInfo));
        	graph.buildHostIP(br);
        }
        catch (NumberFormatException e){
        	System.err.println("Expected Integer for Number of Switches!");
        	return;
        }
        catch (IOException e){
        	System.err.println("File IO error " + e);
        	return;
        }
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        linkDiscoverer.addListener(this);
    }
}


