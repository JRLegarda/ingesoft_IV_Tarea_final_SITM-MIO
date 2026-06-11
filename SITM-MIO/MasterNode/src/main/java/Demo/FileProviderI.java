package Demo;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import com.zeroc.Ice.Current;
import org.example.util.ErrorLog;

public class FileProviderI implements FileProvider {

    private final File file;
    private final long length;
    private final FileChannel channel;

    public FileProviderI(File file) throws IOException {
        this.file = file.getAbsoluteFile();
        this.length = this.file.length();
        this.channel = FileChannel.open(this.file.toPath(), StandardOpenOption.READ);
    }

    @Override
    public long fileSize(Current current) {
        return this.length;
    }

    @Override
    public byte[] readChunk(long offset, int size, Current current) {
        if (offset < 0 || size <= 0 || offset >= length) {
            return new byte[0];
        }

        int bytesToRead = (int) Math.min(size, length - offset);
        ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);

        try {
            int totalRead = 0;
            while (totalRead < bytesToRead) {
                int read = channel.read(buffer, offset + totalRead);
                if (read <= 0) {
                    break;
                }
                totalRead += read;
            }

            if (totalRead == bytesToRead) {
                return buffer.array();
            }

            byte[] trimmed = new byte[totalRead];
            System.arraycopy(buffer.array(), 0, trimmed, 0, totalRead);
            return trimmed;
        } catch (IOException e) {
            System.err.println("[FileProvider][ERROR] readChunk fallo"
                    + " | file=" + file.getAbsolutePath()
                    + " | length=" + length
                    + " | offset=" + offset
                    + " | requestedSize=" + size
                    + " | bytesToRead=" + bytesToRead
                    + " | error=" + ErrorLog.describe(e)
                    + " | at=" + ErrorLog.topStack(e));
            throw new RuntimeException("No se pudo leer el CSV en el master: " + e.getMessage(), e);
        }
    }
}
