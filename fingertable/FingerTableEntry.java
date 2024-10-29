package fingertable;

import p2p.NodeInterface;

public record FingerTableEntry(int start, Interval interval, NodeInterface node) {

    @Override
    public String toString() {
        return "FingerTableEntry: {" + "start:" + start + ", interval:" + interval.getStart() + "-" + interval.getEnd() + ", node:" + node.getName() + '}';
    }
}
