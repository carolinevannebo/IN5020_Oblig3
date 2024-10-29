package fingertable;

public class Interval {
    private final int start;
    private final int end;

    public Interval(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public boolean contains(int id) {
        if (start < end) {
            return id >= start && id < end;
        } else {
            return id >= start || id < end;
        }
    }
}
