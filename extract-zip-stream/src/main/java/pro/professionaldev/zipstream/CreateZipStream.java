package pro.professionaldev.zipstream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CreateZipStream {

    private CreateZipStream() {
        copyList(Arrays.asList(1, 2, 3, 4));
    }

    public static byte[] createZip(Iterable<FileFolderContainer> files) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            createZip(files, byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }
    }

    public static void createZip(Iterable<FileFolderContainer> files, OutputStream outputStream) throws IOException {
        try(ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            addFilesToArchive(files, zipOutputStream);
        }
    }

    private List<String> copyList(List<Integer> input) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            Integer integer = input.get(i);
            String s = String.valueOf(integer);
            list.add(s);
        }
        return list;
    }



    public static byte[] compress(byte[] input) {
        Deflater compressor = new Deflater();

        compressor.setInput(input);

        compressor.finish();

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        byte[] readBuffer = new byte[1024];
        int readCount = 0;

        while (!compressor.finished()) {
            readCount = compressor.deflate(readBuffer);
            if (readCount > 0) {
                bao.write(readBuffer, 0, readCount);
            }
        }

        compressor.end();
        return bao.toByteArray();
    }


    private static void addFilesToArchive(Iterable<FileFolderContainer> files,
                                          ZipOutputStream zipOutputStream) throws IOException {
        for (FileFolderContainer file : files) {
            if (!file.folder()) {
                addFileZipEntry(zipOutputStream, file.fileContainer().fileName(), file.fileContainer().fileContent());
            } else {
                addFolderZipEntry(zipOutputStream, file.fileContainer().fileName());
            }
        }
    }

    private static void addFolderZipEntry(ZipOutputStream zipOutputStream, String fileName) throws IOException {
        String folderIndicator = "/";
        ZipEntry entry = new ZipEntry(fileName + folderIndicator);
        zipOutputStream.putNextEntry(entry);
        zipOutputStream.closeEntry();
    }

    private static void addFileZipEntry(ZipOutputStream zipOutputStream,
                                        String name,
                                        byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(content.length);
        zipOutputStream.putNextEntry(entry);
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

}
