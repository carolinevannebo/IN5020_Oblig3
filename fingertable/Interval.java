package fingertable;

public record Interval(int start, int end) {

    public boolean contains(int id) {
        if (start <= end) {
            return id >= start && id <= end;
        } else {
            return id >= start || id <= end;
        }
    }
}
