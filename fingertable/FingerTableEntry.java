package fingertable;

import p2p.NodeInterface;

public record FingerTableEntry(int start, Interval interval, NodeInterface successor) {

    @Override
    public String toString() {
        return "FingerTableEntry: {" + "start: " + start + ", interval: (" + interval.start() + "," + interval.end() + "), successor: " + successor.getName() + '}';
    }
}
