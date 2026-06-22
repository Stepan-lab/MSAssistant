package attack;

import model.Sequence;
import java.util.List;

public class AttackConfig {
    private Sequence sequence;
    private int payloadPositionStepIndex;
    private String payloadParameter;
    private List<String> payloads;
    private int threads = 3;
    private int maxRetries = 2;

    public Sequence getSequence() { return sequence; }
    public void setSequence(Sequence sequence) { this.sequence = sequence; }

    public int getPayloadPositionStepIndex() { return payloadPositionStepIndex; }
    public void setPayloadPositionStepIndex(int payloadPositionStepIndex) { this.payloadPositionStepIndex = payloadPositionStepIndex; }

    public String getPayloadParameter() { return payloadParameter; }
    public void setPayloadParameter(String payloadParameter) { this.payloadParameter = payloadParameter; }

    public List<String> getPayloads() { return payloads; }
    public void setPayloads(List<String> payloads) { this.payloads = payloads; }

    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}