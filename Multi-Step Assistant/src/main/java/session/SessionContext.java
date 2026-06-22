package session;

import java.util.HashMap;
import java.util.Map;


 // Изолированный контекст переменных для одной сессии (потока).

public class SessionContext {
    private Map<String, String> variables = new HashMap<>();

    public void setVariable(String name, String value) {
        variables.put(name, value);
    }

    public String getVariable(String name) {
        return variables.get(name);
    }

    public boolean contains(String name) {
        return variables.containsKey(name);
    }

    public Map<String, String> getAllVariables() {
        return new HashMap<>(variables);
    }
}