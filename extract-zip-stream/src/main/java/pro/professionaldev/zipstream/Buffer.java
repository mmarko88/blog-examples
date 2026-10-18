package pro.professionaldev.zipstream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;

class Buffer {
    // DANGER: this buffer can be big
    final byte[] bufferContent;
    int dataLength;

    public Buffer(int size) {
        bufferContent = new byte[size];
    }

    public void writeTo(ByteArrayOutputStream out) {
        out.write(bufferContent, 0, dataLength);
    }

    public int getBufferSize() {
        return dataLength;
    }

    public int read(ZipInputStream zipInputStream) throws IOException {
        int nBytesRead = zipInputStream.read(bufferContent);
        dataLength = nBytesRead;
        return nBytesRead;
    }
}