package pro.professionaldev.zipstream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ExtractZipStreamImpl implements ExtractZipStream {
    private final SizeChecker sizeChecker;
    private final int bufferSize;

    public ExtractZipStreamImpl(SizeChecker sizeChecker, int bufferSize) {
        this.sizeChecker = sizeChecker;
        this.bufferSize = bufferSize;
    }

    public List<FileContainer> extract(byte[] data) throws IOException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data)) {
            return extract(byteArrayInputStream);
        }
    }

    public void extract(byte[] data, Consumer<FileContainer> extractedFileConsumer) throws IOException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data)) {
            extract(byteArrayInputStream, extractedFileConsumer);
        }
    }

    public List<FileContainer> extract(InputStream zipStream) throws IOException {
        List<FileContainer> files = new ArrayList<>();
        extract(zipStream, files::add);
        return files;
    }

    public void extract(InputStream zipStream, Consumer<FileContainer> extractedFileConsumer) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(zipStream)) {
            processZipInputStream(zipInputStream, extractedFileConsumer);
        }
    }

    private record ExtractContext(
            ZipInputStream zipInputStream,
            ArchiveSizeMeter archiveSizeMeter,
            Buffer buffer,
            ZipEntry entry) {
    }

    private void processZipInputStream(ZipInputStream zipInputStream,
                                       Consumer<FileContainer> extractedFileConsumer) throws IOException {
        Buffer buffer = new Buffer(bufferSize);
        ArchiveSizeMeter archiveSizeMeter = new ArchiveSizeMeter();
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            ExtractContext extractContext =
                    new ExtractContext(zipInputStream, archiveSizeMeter, buffer, entry);
            FileContainer fileContainer = extractZipEntryWithChecks(extractContext);
            zipInputStream.closeEntry();
            extractedFileConsumer.accept(fileContainer);
        }
    }

    private FileContainer extractZipEntryWithChecks(ExtractContext context) throws IOException {
        checkNotDirectory(context.entry());
        ArchiveSizeMeter archiveSizeMeter = context.archiveSizeMeter();
        archiveSizeMeter.incItem();
        sizeChecker.checkThresholdEntries(archiveSizeMeter);
        return extractZipEntry(context);
    }

    private static void checkNotDirectory(ZipEntry entry) {
        if (entry.isDirectory()) {
            throw new IllegalArgumentException("Folders inside zip archive are not supported.");
        }
    }

    private FileContainer extractZipEntry(ExtractContext context) throws IOException {
        ZipEntry entry = context.entry();
        EntrySizeMeter entrySizeMeter = new EntrySizeMeter(entry.getCompressedSize());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copyToOutStreamWithThresholdChecks(context, out, entrySizeMeter);

            byte[] fileContent = out.toByteArray();
            return new FileContainer(fileContent, entry.getName());
        }
    }
    private void copyToOutStreamWithThresholdChecks(ExtractContext context,
                                                    ByteArrayOutputStream out,
                                                    EntrySizeMeter entrySizeMeter) throws IOException {
        Buffer buffer = context.buffer();
        ZipInputStream zipInputStream = context.zipInputStream();

        while (buffer.read(zipInputStream) > 0) {
            buffer.writeTo(out);
            checkThresholds(context, entrySizeMeter);
        }
    }

    private void checkThresholds(ExtractContext context,
                                 EntrySizeMeter entrySizeMeter) {
        Buffer buffer = context.buffer();
        ZipEntry entry = context.entry();
        entrySizeMeter.addEntrySize(buffer.getBufferSize());
        sizeChecker.checkThresholdEntrySize(entrySizeMeter, entry.getName());
        sizeChecker.checkThresholdCompressionRatio(entrySizeMeter, entry.getName());

        ArchiveSizeMeter archiveSizeMeter = context.archiveSizeMeter;
        archiveSizeMeter.addArchiveSize(buffer.getBufferSize());
        sizeChecker.checkThresholdTotalSize(archiveSizeMeter);
    }
}
