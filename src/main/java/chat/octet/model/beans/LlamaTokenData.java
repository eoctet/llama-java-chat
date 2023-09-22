package chat.octet.model.beans;


public class LlamaTokenData {
    /**
     * token id
     */
    public int id;
    /**
     * log-odds of the token
     */
    public float logit;
    /**
     * probability of the token
     */
    public float p;

    public LlamaTokenData() {
    }

    public LlamaTokenData(int id, float logit) {
        this.id = id;
        this.logit = logit;
    }
}
