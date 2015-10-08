package net.floodlightcontroller.sdn;

public class IPportPair {
	public String ipSrc;
	public String ipDes;
	public SrcPortPair spp;
	IPportPair(String ips, String ipd, SrcPortPair p){
		ipSrc = ips;
		ipDes = ipd;
		spp = new SrcPortPair(p.src, p.dst, p.port);
	}
}
