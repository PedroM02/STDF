package blockchain;

import java.util.List;

public interface BlockchainService {
    List<String> readAll();
    String readAt(int index);
    int size();
    int append(String value);
}
