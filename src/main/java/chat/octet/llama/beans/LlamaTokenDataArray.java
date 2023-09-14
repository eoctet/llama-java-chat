package chat.octet.llama.beans;


public class LlamaTokenDataArray {
    public LlamaTokenData[] data;
    public int size;
    public boolean sorted;

    public LlamaTokenDataArray() {
    }

    public LlamaTokenDataArray(LlamaTokenData[] data, int size, boolean sorted) {
        this.data = data;
        this.size = size;
        this.sorted = sorted;
    }
}
