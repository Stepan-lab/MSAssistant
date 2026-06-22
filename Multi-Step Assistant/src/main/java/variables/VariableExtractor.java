package variables;

import burp.api.montoya.http.message.responses.HttpResponse;
import model.VariableDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableExtractor {


    public List<VariableDefinition> suggestVariables(HttpResponse response) {
        List<VariableDefinition> suggestions = new ArrayList<>();

        if (response == null) return suggestions;

        // Ищем куки в заголовках ответа
        for (var header : response.headers()) {
            if (header.name().equalsIgnoreCase("Set-Cookie")) {
                String value = header.value();
                Matcher cookieMatcher = Pattern.compile("([^=]+)=([^;]+)").matcher(value);
                if (cookieMatcher.find()) {
                    String cookieName = cookieMatcher.group(1).trim();
                    suggestions.add(new VariableDefinition(
                            "cookie_" + cookieName,
                            VariableDefinition.SourceType.COOKIE,
                            "Set-Cookie:\\s*" + cookieName + "=([^;]+)"
                    ));
                }
            }
        }

        return suggestions;
    }

    public String extractValue(HttpResponse response, VariableDefinition def) {
        String dataToSearch = switch (def.getSourceType()) {
            case COOKIE, LOCATION_HEADER -> response.toString();
            default -> response.bodyToString();
        };

        Pattern p = Pattern.compile(def.getPattern(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(dataToSearch);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}