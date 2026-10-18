package pro.professionaldev.zipstream;

public class SizeChecker {
    private final long thresholdTotalSizeKB;
    private final long thresholdEntrySizeKB;
    private final long thresholdNoEntries;
    private final double thresholdCompressionRatio;

    public SizeChecker(long thresholdTotalSizeKB,
                       long thresholdEntrySizeKB,
                       long thresholdNoEntries,
                       double thresholdCompressionRatio) {
        checkInitialThresholdTotalSize(thresholdTotalSizeKB);
        checkInitialThresholdEntrySize(thresholdEntrySizeKB);
        checkInitialThresholdNoEntries(thresholdNoEntries);
        checkInitialThresholdCompressionRatio(thresholdCompressionRatio);

        this.thresholdTotalSizeKB = thresholdTotalSizeKB;
        this.thresholdEntrySizeKB = thresholdEntrySizeKB;
        this.thresholdNoEntries = thresholdNoEntries;
        this.thresholdCompressionRatio = thresholdCompressionRatio;
    }

    private static void checkInitialThresholdCompressionRatio(double thresholdRatio) {
        if (thresholdRatio <= 0.001D) {
            throw new IllegalArgumentException(
                    "Threshold ratio must be greater than zero."
            );
        }
    }

    private static void checkInitialThresholdNoEntries(long thresholdEntries) {
        if (thresholdEntries <= 0) {
            throw new IllegalArgumentException(
                    "Threshold entries must be greater than zero."
            );
        }
    }

    private static void checkInitialThresholdEntrySize(long extractedSingleFileSizeLimitInKB) {
        if (extractedSingleFileSizeLimitInKB <= 0) {
            throw new IllegalArgumentException(
                    "Extracted single file size limit cannot be zero or negative number"
            );
        }
    }

    private static void checkInitialThresholdTotalSize(long thresholdSizeInKB) {
        if (thresholdSizeInKB <= 0) {
            throw new IllegalArgumentException(
                    "Extracted file limit size cannot be negative number"
            );
        }
    }

    public void checkThresholdEntries(ArchiveSizeMeter archiveSizeMeter) {
        if (archiveSizeMeter.getTotalItemsArchive() > thresholdNoEntries) {
            // too many entries in this archive, can lead to inodes exhaustion of the system
            throw new IllegalArgumentException("Too many entries in zip archive.");
        }
    }

    public void checkThresholdEntrySize(EntrySizeMeter entrySizeMeter, String fileName) {
        if (entrySizeMeter.getEntrySize() > thresholdEntrySizeKB) {
            throw new IllegalArgumentException("Extracted file in zip archive is too big. FileName:" + fileName);
        }
    }

    public void checkThresholdTotalSize(ArchiveSizeMeter archiveSizeMeter) {
        if (archiveSizeMeter.getTotalSizeArchive() > thresholdTotalSizeKB) {
            // the uncompressed data size is too much for the application resource capacity
            throw new IllegalArgumentException("Zip archive is too big.");
        }
    }

    public void checkThresholdCompressionRatio(EntrySizeMeter entrySizeMeter, String fileName) {
        if (entrySizeMeter.getCompressionRatio() > thresholdCompressionRatio) {
            // ratio between compressed and uncompressed data is highly suspicious, looks like a Zip Bomb Attack
            throw new IllegalArgumentException("Highly compressed file. FileName:" + fileName);
        }
    }
}
