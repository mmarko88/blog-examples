package pro.professionaldev.zipstream;

public class EntrySizeMeter {
    private long entrySize = 0;
    private final long compressedSize;

    public EntrySizeMeter(long compressedSize) {
        this.compressedSize = compressedSize;
    }

    public void addEntrySize(long length) {
        entrySize += length;
    }

    public long getEntrySize() {
        return entrySize;
    }

    public double getCompressionRatio() {
         return entrySize / (double)compressedSize;
    }
}
