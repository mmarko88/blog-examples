package pro.professionaldev.zipstream;

public class ArchiveSizeMeter {
    private long totalSizeArchive = 0;
    private long totalItemsArchive = 0;

    public void addArchiveSize(long size) {
        totalSizeArchive += size;
    }
    public void incItem() {
        totalItemsArchive++;
    }

    public long getTotalItemsArchive() {
        return totalItemsArchive;
    }

    public long getTotalSizeArchive() {
        return totalSizeArchive;
    }
}
