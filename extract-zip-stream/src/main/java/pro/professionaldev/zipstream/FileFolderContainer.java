package pro.professionaldev.zipstream;

public record FileFolderContainer(FileContainer fileContainer,
                                  boolean folder) {
    public FileFolderContainer(byte[] fileContent, String fileName, boolean folder) {
        this(new FileContainer(fileContent, fileName), folder);
    }

    public FileFolderContainer(byte[] fileContent, String fileName) {
        this(fileContent, fileName, false);
    }
}