package pro.professionaldev.zipstream;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

public interface ExtractZipStream {
    List<FileContainer> extract(byte[] data) throws IOException;
    void extract(byte[] data, Consumer<FileContainer> extractedFileConsumer) throws IOException;

    List<FileContainer> extract(InputStream zipStream) throws IOException;
    void extract(InputStream zipStream, Consumer<FileContainer> extractedFileConsumer) throws IOException;
}