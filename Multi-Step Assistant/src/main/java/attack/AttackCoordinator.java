package attack;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import model.Sequence;
import model.Step;
import model.VariableDefinition;
import session.SessionContext;
import variables.VariableExtractor;
import variables.VariableResolver;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AttackCoordinator {

    private final MontoyaApi api;
    private final AttackConfig config;
    private final VariableResolver resolver;
    private final VariableExtractor extractor;

    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger completedPayloads = new AtomicInteger(0);

    private Consumer<String> logConsumer;
    private Consumer<Integer> progressConsumer;

    public AttackCoordinator(MontoyaApi api, AttackConfig config) {
        this.api = api;
        this.config = config;
        this.resolver = new VariableResolver();
        this.extractor = new VariableExtractor();
        this.executor = Executors.newFixedThreadPool(config.getThreads());
    }

    public void setLogConsumer(Consumer<String> logConsumer) { this.logConsumer = logConsumer; }
    public void setProgressConsumer(Consumer<Integer> progressConsumer) { this.progressConsumer = progressConsumer; }

    public void start() {
        running.set(true);
        List<String> payloads = config.getPayloads();
        int totalPayloads = payloads.size();
        log("Attack started|" + config.getThreads() + " threads|" + totalPayloads + " payloads||");

        for (int threadId = 0; threadId < config.getThreads(); threadId++) {
            executor.submit(() -> runAttacker(payloads, totalPayloads));
        }

        new Thread(() -> {
            try {
                executor.shutdown();
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (InterruptedException ignored) {
            } finally {
                if (running.get()) {
                    log("Attack completed|" + completedPayloads.get() + " processed||");
                }
            }
        }).start();
    }

    private void runAttacker(List<String> payloads, int totalPayloads) {
        Sequence sequence = config.getSequence();
        int targetStepIndex = config.getPayloadPositionStepIndex();
        VariableExtractor localExtractor = new VariableExtractor();
        VariableResolver localResolver = new VariableResolver();

        while (running.get()) {
            int index = completedPayloads.getAndIncrement();
            if (index >= totalPayloads) break;

            String payload = payloads.get(index);
            SessionContext ctx = new SessionContext();

            try {
                for (int i = 0; i < targetStepIndex && i < sequence.getSteps().size(); i++) {
                    if (!running.get()) return;
                    Step step = sequence.getSteps().get(i);
                    HttpRequest resolved = localResolver.resolve(step.getRequest(), ctx);
                    HttpResponse response = api.http().sendRequest(resolved).response();

                    for (VariableDefinition def : step.getExtractions()) {
                        String val = localExtractor.extractValue(response, def);
                        if (val != null) ctx.setVariable(def.getName(), val);
                    }
                }

                if (!running.get()) return;

                Step targetStep = sequence.getSteps().get(targetStepIndex);
                HttpRequest request = targetStep.getRequest();

                ctx.setVariable("payload", payload);

                HttpRequest resolved = localResolver.resolve(request, ctx);
                HttpResponse response = api.http().sendRequest(resolved).response();

                String status = String.valueOf(response.statusCode());
                String length = String.valueOf(response.bodyToString().length());
                String reqStr = resolved.toString();
                String respStr = response.toString();

                log(payload + "|" + status + "|" + length + "|" + reqStr + "|" + respStr);

            } catch (Exception ex) {
                if (running.get()) {
                    log(payload + "|ERROR|0|" + ex.getMessage() + "|");
                }
            } finally {
                if (progressConsumer != null) progressConsumer.accept(index + 1);
            }
        }
    }

    public void stop() {
        running.set(false);
        executor.shutdownNow();
        log("Attack stopped|" + completedPayloads.get() + " processed||");
    }

    private void log(String msg) {
        if (logConsumer != null) logConsumer.accept(msg);
        else api.logging().logToOutput(msg);
    }
}