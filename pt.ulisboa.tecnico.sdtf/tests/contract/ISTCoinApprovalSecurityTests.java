package contract;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ISTCoinApprovalSecurityTests {

    @Test
    void sourceRequiresZeroResetBeforeReplacingNonZeroAllowance() throws IOException {
        String source = Files.readString(contractRoot().resolve("ISTCoin.sol"));

        assertTrue(source.contains("require(current == 0 || amount == 0, \"must reset allowance to zero first\")"));
        assertTrue(source.contains("allowances[msg.sender][spender] = amount;"));
    }

    @Test
    void sourceProvidesIncrementalAllowanceApisForSaferConcurrentUpdates() throws IOException {
        String source = Files.readString(contractRoot().resolve("ISTCoin.sol"));

        assertTrue(source.contains("function increaseAllowance(address spender, uint256 added)"));
        assertTrue(source.contains("allowances[msg.sender][spender] += added;"));
        assertTrue(source.contains("function decreaseAllowance(address spender, uint256 subtracted)"));
        assertTrue(source.contains("require(current >= subtracted, \"decrease below zero\")"));
        assertTrue(source.contains("allowances[msg.sender][spender] = current - subtracted;"));
    }

    @Test
    void compiledArtifactRetainsApprovalFrontrunningMitigationAndSelectors() throws IOException {
        JsonObject artifact = JsonParser.parseString(Files.readString(contractRoot().resolve("artifact/ISTCoin.json")))
                .getAsJsonObject();
        JsonObject methodIdentifiers = artifact.getAsJsonObject("data").getAsJsonObject("methodIdentifiers");
        String deployedCode = artifact.getAsJsonObject("data")
                .getAsJsonObject("deployedBytecode")
                .get("object")
                .getAsString();
        String metadata = artifact.toString();

        assertTrue(metadata.contains("must reset allowance to zero fir"));
        assertTrue("095ea7b3".equals(methodIdentifiers.get("approve(address,uint256)").getAsString()));
        assertTrue("39509351".equals(methodIdentifiers.get("increaseAllowance(address,uint256)").getAsString()));
        assertTrue("a457c2d7".equals(methodIdentifiers.get("decreaseAllowance(address,uint256)").getAsString()));
        assertTrue("23b872dd".equals(methodIdentifiers.get("transferFrom(address,address,uint256)").getAsString()));
        assertTrue(deployedCode.contains("8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925"));
    }

    private static Path contractRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path sibling = cwd.resolveSibling("contract");
        if (Files.exists(sibling)) {
            return sibling;
        }
        return cwd.resolve("contract");
    }
}
