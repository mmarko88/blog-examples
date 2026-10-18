package pro.professionaldev.zipstream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static pro.professionaldev.zipstream.Util.getBytes;
import static pro.professionaldev.zipstream.Util.getBytesInKb;

class ZipExtractTest {
    private final SizeChecker sizeChecker = new SizeChecker(getBytesInKb(10),
             getBytesInKb(7),
            2,
            10D);
    private final ExtractZipStream extractZipStream = new ExtractZipStreamImpl(sizeChecker, getBytesInKb(1));

    @Test
    void extractZipInputStream() throws IOException {
        byte[] stringBytes = "file content".getBytes(StandardCharsets.UTF_8);
        byte[] zip = CreateZipStream.createZip(List.of(new FileFolderContainer(stringBytes, "test.txt")));

        List<FileContainer> result = extractZipStream.extract(zip);
        Assertions.assertArrayEquals(stringBytes, result.get(0).fileContent());
    }

    @Test
    void extractZipInputStream_multipleFiles() throws IOException {
        // setup
        byte[] string1Bytes = getBytes("file 1 content");
        byte[] string2Bytes = getBytes("file 2 content");

        FileFolderContainer file1 = new FileFolderContainer(string1Bytes, "test1.txt");
        FileFolderContainer file2 = new FileFolderContainer(string2Bytes, "test2.txt");

        byte[] zip = CreateZipStream.createZip(Arrays.asList(file1, file2));

        // exec
        List<FileContainer> result = extractZipStream.extract(zip);

        // asserts
        Assertions.assertArrayEquals(string1Bytes, result.get(0).fileContent());
        Assertions.assertArrayEquals(string2Bytes, result.get(1).fileContent());
    }

    @Test
    void whenTooManyEntries_thenThrowError() throws IOException {
        FileFolderContainer file1 = new FileFolderContainer(getBytes("file 1 content"), "test1.txt");
        FileFolderContainer file2 = new FileFolderContainer(getBytes("file 2 content"), "test2.txt");
        FileFolderContainer file3 = new FileFolderContainer(getBytes("file 3 content"), "test3.txt");

        byte[] zip = CreateZipStream.createZip(Arrays.asList(file1, file2, file3));

        IllegalArgumentException illegalArgumentException = Assertions.assertThrows(IllegalArgumentException.class,
                () -> extractZipStream.extract(zip));
        Assertions.assertEquals("Too many entries in zip archive.", illegalArgumentException.getMessage());
    }

    @Test
    void whenExtractedBigArchive_thenThrowError() throws IOException {
        byte[] encodedString = generateFile(5);
        byte[] encodedString1 = generateFile(6);
        byte[] zip = CreateZipStream.createZip(List.of(
                new FileFolderContainer(encodedString, "test.txt"),
                new FileFolderContainer(encodedString1, "test1.txt")));
        Throwable throwable = Assertions.assertThrows(IllegalArgumentException.class,
                () -> extractZipStream.extract(zip));

        Assertions.assertEquals("Zip archive is too big.", throwable.getMessage());
    }

    @Test
    void whenExtractedBigFileInArchive_thenThrowError() throws IOException {
        byte[] encodedString = generateFile(8);
        byte[] zip = CreateZipStream.createZip(List.of(
                new FileFolderContainer(encodedString, "test.txt")));

        Throwable throwable = Assertions.assertThrows(IllegalArgumentException.class,
                () -> extractZipStream.extract(zip));

        Assertions.assertEquals("Extracted file in zip archive is too big. FileName:test.txt", throwable.getMessage());
    }

    @Test
    void whenFolderInsideArchive_thenThrowError() throws IOException {
        byte[] zip = CreateZipStream.createZip(List.of(
                new FileFolderContainer(null, "folder", true)));

        Throwable throwable = Assertions.assertThrows(IllegalArgumentException.class,
                () -> extractZipStream.extract(zip));

        Assertions.assertEquals("Folders inside zip archive are not supported.", throwable.getMessage());
    }

    @Test
    void whenZipBomb_thenThrowError() throws Exception {
        byte[] encodedString = generateZipBomb(5);

        byte[] zip = CreateZipStream.createZip(List.of(
                new FileFolderContainer(encodedString, "test.txt")));

        Path path = Paths.get("test.zip");
        Files.write(path, zip);

        Throwable throwable;
        try (FileInputStream fis = new FileInputStream("test.zip")) {

            byte[] data = fis.readAllBytes();
            ApacheExtractZip apacheExtractZip = new ApacheExtractZip();
            ByteArrayInputStream zipStream = new ByteArrayInputStream(zip);
            throwable = Assertions.assertThrows(IllegalArgumentException.class,
                    () -> apacheExtractZip.extract(data));
        }

        Assertions.assertEquals("Zip archive is too big.", throwable.getMessage());
    }


    private byte[] generateZipBomb(int kb) {
        int fileSize = getBytesInKb(kb);
        return new byte[fileSize];// by default, they all have 0 value
    }

    private byte[] generateFile(int kb) {
        int fileSize = getBytesInKb(kb);
        byte[] bytes = new byte[fileSize];
        // Purposefully generate same sequence of "random" numbers
        // for tests we need repeatable values
        Random r = new Random(1);
        r.nextBytes(bytes);
        return bytes;
    }
}
