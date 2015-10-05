package net.floodlightcontroller.sdn;

// A class that just puts a src, dst, and the dst port together to set rules
public class SrcPortPair{
	public long src;
	public long dst;
	public short port;
	SrcPortPair(long srcI, long dstI, short portI){
		src = srcI;
		dst = dstI;
		port = portI;
	}
}