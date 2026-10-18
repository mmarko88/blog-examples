package pro.professionaldev.zipstream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;

public class ApacheExtractZip {

    private double thresholdRatio = 10;
    private double thresholdSizeInKB = 10;
    private double thresholdEntries = 2;

    public List<FileFolderContainer> extract(byte[] data) throws IOException {
        ArrayList<FileFolderContainer> files = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(new SeekableInMemoryByteChannel(data))) {
            processZipInputStream(zipFile, e -> addToArrayListWithSizeCheck(files, e));
            return files;
        }
    }

    private void addToArrayListWithSizeCheck(ArrayList<FileFolderContainer> files, FileFolderContainer e) {
        files.add(e);
        if (files.size() > thresholdEntries) {
            throw new IllegalArgumentException("Too many entries in zip archive.");
            // too many entries in this archive, can lead to inodes exhaustion of the system
        }
    }

    private void processZipInputStream(ZipFile zipFile, Consumer<FileFolderContainer> add) throws IOException {
        int totalBytesRead = 0;
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry currentEntry = entries.nextElement();
            FileFolderContainer fileContainer = decompressFile(currentEntry, zipFile, totalBytesRead);
            add.accept(fileContainer);
        }
    }

    private FileFolderContainer decompressFile(ZipArchiveEntry entry, ZipFile zipFile, int totalBytesRead) throws IOException {
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                copyInToOutStream(inputStream, byteArrayOutputStream, totalBytesRead, entry.getCompressedSize());
                return new FileFolderContainer(byteArrayOutputStream.toByteArray(), entry.getName());
            }
        }
    }

    private int copyInToOutStream(InputStream zipInputStream,
                                  OutputStream out,
                                  int totalBytesRead,
                                  long entryCompressedSize) throws IOException {
        int newTotalBytesRead = totalBytesRead;
        int totalSizeEntry = 0;
        int nBytes;
        byte[] buffer = new byte[1000];
        while ((nBytes = zipInputStream.read(buffer)) > 0) {
            out.write(buffer, 0, nBytes);
            newTotalBytesRead += nBytes;
            if (newTotalBytesRead > thresholdSizeInKB) {
                // the uncompressed data size is too much for the application resource capacity
                throw new IllegalArgumentException("Zip archive is too big.");
            }
            totalSizeEntry += nBytes;
            double compressionRatio = (double) totalSizeEntry / entryCompressedSize;
            if (compressionRatio > thresholdRatio) {
                // ratio between compressed and uncompressed data is highly suspicious, looks like a Zip Bomb Attack
                throw new IllegalArgumentException("Highly compressed file.");
            }
        }

        return newTotalBytesRead;
    }
}
