package fingertable;

import java.util.ArrayList;
import java.util.List;

public class FingerTable {
    private final List<FingerTableEntry> entries; // todo: sjekk ut 2D array

    public FingerTable(int size) {
        this.entries = new ArrayList<>(size);
    }

    public void addEntry(FingerTableEntry entry) {
        entries.add(entry);
    }

    public List<FingerTableEntry> getEntries() {
        return entries;
    }

    @Override
    public String toString() {
        return entries.toString();
    }
}
