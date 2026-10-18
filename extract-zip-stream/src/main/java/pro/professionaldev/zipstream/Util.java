package pro.professionaldev.zipstream;

import java.nio.charset.StandardCharsets;

public final class Util {
    private Util() { }
    public static final int BYTES_IN_KB = 1024;
    public static long getBytesInKb(long noKb) {
        return BYTES_IN_KB * noKb;
    }

    public static int getBytesInKb(int noKb) {
        return BYTES_IN_KB * noKb;
    }

    public static byte[] getBytes(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }
}
