package model;


public class VariableDefinition {
    public enum SourceType {
        REGEX, COOKIE, CSRF, HIDDEN_FIELD, LOCATION_HEADER
    }

    private String name;
    private SourceType sourceType;
    private String pattern;  // regex

    public VariableDefinition(String name, SourceType sourceType, String pattern) {
        this.name = name;
        this.sourceType = sourceType;
        this.pattern = pattern;
    }

    public String getName() { return name; }
    public SourceType getSourceType() { return sourceType; }
    public String getPattern() { return pattern; }
}