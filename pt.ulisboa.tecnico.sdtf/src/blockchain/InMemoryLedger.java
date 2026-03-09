package blockchain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryLedger implements BlockchainService {
    private final List<String> entries;

    public InMemoryLedger() {
        this.entries = new CopyOnWriteArrayList<>();
    }

    @Override
    public int append(String value) {
        entries.add(value);
        return entries.size() - 1;
    }

    @Override
    public List<String> readAll() {
        return new ArrayList<>(entries);
    }

    @Override
    public String readAt(int index) {
        if (index > entries.size() - 1 || index < 0) {
            throw new IndexOutOfBoundsException("invalid index: " + index);
        }
        return entries.get(index);
    }

    @Override
    public int size() {
        return entries.size();
    }
}
