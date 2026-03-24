package consensus;

import java.nio.charset.StandardCharsets;

public final class QcSigningData {

    private static final String DOMAIN_SEPARATOR = "HOTSTUFF_QC";

    private QcSigningData() {
    }

    public static String buildSigningString(String blockHash, long view, Phase phase, long configVersion) {
        return DOMAIN_SEPARATOR + "|" + blockHash + "|" + view + "|" + phase + "|" + configVersion;
    }

    public static byte[] buildSigningBytes(String blockHash, long view, Phase phase, long configVersion) {
        return buildSigningString(blockHash, view, phase, configVersion).getBytes(StandardCharsets.UTF_8);
    }
}
