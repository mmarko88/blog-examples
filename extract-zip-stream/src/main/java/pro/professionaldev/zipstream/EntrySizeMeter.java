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

    /**
     * Returns true when the compressed size is known and reliable for a ratio check.
     * ZIP's local file header is allowed to omit the compressed size (reported as -1);
     * when it does, the ratio check must be skipped in favour of the global entry-size cap.
     */
    public boolean hasKnownCompressedSize() {
        return compressedSize > 0;
    }

    /**
     * @return the compression ratio, or {@link Double#NaN} when the compressed size is unknown.
     * Callers must gate on {@link #hasKnownCompressedSize()} before trusting the result.
     */
    public double getCompressionRatio() {
        if (compressedSize <= 0) {
            return Double.NaN;
        }
        return entrySize / (double) compressedSize;
    }
}
