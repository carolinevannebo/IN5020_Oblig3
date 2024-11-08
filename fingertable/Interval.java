package fingertable;

public record Interval(int start, int end) {

    public boolean contains(int id) {
        if (start <= end) { // continuous interval (non-wrapping)
            // check if id falls within the range [start, end]
            return id >= start && id <= end;
        } else { // wrapping interval (wraps around the ring boundary)
            // check if id is either in the range [start, max_id] or [0, end]
            return id >= start || id <= end;
        }
    }
}