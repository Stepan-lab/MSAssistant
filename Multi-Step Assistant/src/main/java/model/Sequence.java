package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Sequence {
    private String name;
    private List<Step> steps = new ArrayList<>();
    private Map<String, String> lastVariableValues = new HashMap<>();

    public Sequence(String name) {
        this.name = name;
    }

    public void addStep(Step step) {
        steps.add(step);
    }

    public List<Step> getSteps() {
        return steps;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLastVariableValue(String name, String value) {
        lastVariableValues.put(name, value);
    }

    public String getLastVariableValue(String name) {
        return lastVariableValues.get(name);
    }

    public Map<String, String> getLastVariableValues() {
        return lastVariableValues;
    }

    public void clearLastVariableValues() {
        lastVariableValues.clear();
    }
}