package model;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class Step {

    public enum Status {
        NOT_EXECUTED,
        EXECUTING,
        SUCCESS,
        ERROR
    }

    private HttpRequest request;
    private HttpResponse response;
    private List<VariableDefinition> extractions = new ArrayList<>();
    private Status status = Status.NOT_EXECUTED;
    private String errorMessage;

    public Step(HttpRequest request) {
        this.request = request;
    }

    public HttpRequest getRequest() { return request; }
    public void setRequest(HttpRequest request) { this.request = request; }

    public HttpResponse getResponse() { return response; }
    public void setResponse(HttpResponse response) { this.response = response; }

    public List<VariableDefinition> getExtractions() { return extractions; }
    public void addExtraction(VariableDefinition def) { extractions.add(def); }
    public void removeExtraction(VariableDefinition def) { extractions.remove(def); }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public void reset() {
        this.response = null;
        this.status = Status.NOT_EXECUTED;
        this.errorMessage = null;
    }
}