package net.floodlightcontroller.sdn;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

public class Sdn implements IFloodlightModule, IOFSwitchListener, ILinkDiscoveryListener {
    protected static Logger log = LoggerFactory.getLogger(Sdn.class);

    protected IFloodlightProviderService floodlightProvider;
    protected ILinkDiscoveryService linkDiscoverer;
    private SdnGraph graph;
    
    public void setFloodlightProvider(IFloodlightProviderService floodlightProvider) {
        this.floodlightProvider = floodlightProvider;
    }
    
    @Override public void linkDiscoveryUpdate(LDUpdate update) {
    	System.out.println("=====Here now!=====");
    }

    @Override public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
    	System.out.println("=====Here now!2=====");
    	Map<Link, LinkInfo> linkMap = linkDiscoverer.getLinks();
    	graph.setNumLinks(linkMap.size());
    	int index = 0;
    	for (Map.Entry<Link, LinkInfo> linkEntry : linkMap.entrySet()){
    		graph.addLink(index, linkEntry.getKey());
    		index++;
    	}
    	
    	String srcIP = "10.0.0.1";
    	String dstIP = "10.0.0.5";
    	graph.calculatePaths(srcIP, dstIP);
    	ArrayList<ArrayList<SrcPortPair>> shortPaths = graph.shortPaths;
    	
    	//print solution temporarily
    	for (int i = 0; i < shortPaths.size(); i++){
    		System.out.println("Path " + i);
    		for (int j = 0; j < shortPaths.get(i).size(); j++){
    			System.out.println("Src " + shortPaths.get(i).get(j).src + " Dst " + shortPaths.get(i).get(j).dst + " DstPort " + shortPaths.get(i).get(j).port);
    		}
    	}
    	
    	// choose only one solution for now get(0)
    	ArrayList<SrcPortPair> oneSolution = shortPaths.get(0);
    	
    	// assign rule to all the switches
    	for (int i = 0; i < oneSolution.size(); i++){
    		OFMatch match = new OFMatch();
    		match.setWildcards(Wildcards.FULL.matchOn(Flag.DL_TYPE).matchOn(Flag.NW_DST).withNwDstMask(32));
    		match.setDataLayerType(Ethernet.TYPE_IPv4);
    		match.setNetworkSource(IPv4.toIPv4Address(dstIP));
    		
    		ArrayList<OFAction> actions = new ArrayList<OFAction>();
    		OFActionOutput action = new OFActionOutput().setPort(oneSolution.get(i).port);
    		actions.add(action);
    		
    		OFFlowMod flowMod = new OFFlowMod();
    		flowMod.setHardTimeout((short)0);
    		flowMod.setMatch(match);
    		flowMod.setActions(actions);
    		flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + OFActionOutput.MINIMUM_LENGTH /*+ OFActionNetworkLayerSource.MINIMUM_LENGTH*/));
    		try{
    			System.out.println("Switch adding rule to " + oneSolution.get(i).src);
    			IOFSwitch currentSwitch = floodlightProvider.getSwitch(oneSolution.get(i).src);
    			currentSwitch.write(flowMod, null);
    		}
    		catch(IOException e){
    			log.error("Failed to write the flowMod" + e);
    		}
    	}
    }
    
    @Override public void switchAdded(long switchId){}

    @Override public void switchRemoved(long switchId) {}

    @Override public void switchActivated(long switchId) {
    	System.out.println("===== I've been added3! ======" + switchId);
    }

    @Override public void switchPortChanged(long switchId,
                                  ImmutablePort port,
                                  IOFSwitch.PortChangeType type) {
    	System.out.println("===== I've been added4! ======" + switchId);
    }
    @Override public void switchChanged(long switchId) {}
    
    /*@Override
    public String getName() {
        return Sdn.class.getPackage().getName();
    }

    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        
    	System.out.println("I am here " + sw.toString());
    	
    	OFPacketIn pi = (OFPacketIn) msg;
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory()
                .getMessage(OFType.PACKET_OUT);
        po.setBufferId(pi.getBufferId())
            .setInPort(pi.getInPort());

        // set actions
        OFActionOutput action = new OFActionOutput()
            .setPort(OFPort.OFPP_FLOOD.getValue());
        po.setActions(Collections.singletonList((OFAction)action));
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // set data if is is included in the packetin
        if (pi.getBufferId() == OFPacketOut.BUFFER_ID_NONE) {
            byte[] packetData = pi.getPacketData();
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength() + packetData.length));
            po.setPacketData(packetData);
        } else {
            po.setLength(U16.t(OFPacketOut.MINIMUM_LENGTH
                    + po.getActionsLength()));
        }
        try {
            sw.write(po, cntx);
        } catch (IOException e) {
            log.error("Failure writing PacketOut", e);
        }

        return Command.CONTINUE;
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }*/

    // IFloodlightModule
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // We don't provide any services, return null
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        // We don't provide any services, return null
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
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
        floodlightProvider.addOFSwitchListener(this);
        linkDiscoverer.addListener(this);
    }
}


