package chat.octet.llama;

import com.sun.jna.IntegerType;
import com.sun.jna.Native;

public class NativeSize extends IntegerType {
    private static final long serialVersionUID = 1L;

    public NativeSize() {
        this(0);
    }

    public NativeSize(long value) {
        super(Native.SIZE_T_SIZE, value, true);
    }

}
