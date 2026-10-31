package violetarchive;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal gzip NBT reader for the structure templates shipped in the jar.
 */
public final class NbtStructures {
    private NbtStructures() {
    }

    public static Map<String, Object> read(byte[] gzip) throws IOException {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzip)))) {
            int type = input.readUnsignedByte();
            if (type != 10) {
                throw new IOException("root is not a compound");
            }
            readString(input);
            return readCompound(input);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private static Map<String, Object> readCompound(DataInputStream input) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) {
                return values;
            }
            String name = readString(input);
            values.put(name, readValue(input, type));
        }
    }

    private static Object readValue(DataInputStream input, int type) throws IOException {
        return switch (type) {
            case 1 -> input.readByte();
            case 2 -> input.readShort();
            case 3 -> input.readInt();
            case 4 -> input.readLong();
            case 5 -> input.readFloat();
            case 6 -> input.readDouble();
            case 7 -> {
                int length = input.readInt();
                yield input.readNBytes(length);
            }
            case 8 -> readString(input);
            case 9 -> readList(input);
            case 10 -> readCompound(input);
            case 11 -> {
                int length = input.readInt();
                int[] values = new int[length];
                for (int index = 0; index < length; index++) {
                    values[index] = input.readInt();
                }
                yield values;
            }
            case 12 -> {
                int length = input.readInt();
                long[] values = new long[length];
                for (int index = 0; index < length; index++) {
                    values[index] = input.readLong();
                }
                yield values;
            }
            default -> throw new IOException("unsupported tag " + type);
        };
    }

    private static List<Object> readList(DataInputStream input) throws IOException {
        int type = input.readUnsignedByte();
        int length = input.readInt();
        List<Object> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            values.add(readValue(input, type));
        }
        return values;
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        return new String(input.readNBytes(length), java.nio.charset.StandardCharsets.UTF_8);
    }
}
