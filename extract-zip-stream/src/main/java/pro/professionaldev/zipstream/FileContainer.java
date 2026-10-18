package pro.professionaldev.zipstream;

public record FileContainer(byte[] fileContent, String fileName) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FileContainer that = (FileContainer) o;

        return fileName.equals(that.fileName);
    }

    @Override
    public int hashCode() {
        return fileName.hashCode();
    }

    @Override
    public String toString() {
        return "FileContainer{" +
                "fileName='" + fileName + '\'' +
                '}';
    }
}
