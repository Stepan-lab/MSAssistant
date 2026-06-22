package variables;

import burp.api.montoya.http.message.requests.HttpRequest;
import session.SessionContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    public HttpRequest resolve(HttpRequest template, SessionContext context) {
        if (context == null || template == null) return template;

        HttpRequest result = template;

        // Заголовки
        for (var header : result.headers()) {
            String name = header.name();
            String value = header.value();
            String resolvedValue = replacePlaceholders(value, context);
            if (!value.equals(resolvedValue)) {
                result = result.withHeader(name, resolvedValue);
            }
        }

        // Тело
        String body = result.bodyToString();
        if (body != null && !body.isEmpty()) {
            String resolvedBody = replacePlaceholders(body, context);
            if (!body.equals(resolvedBody)) {
                result = result.withBody(resolvedBody);
            }
        }

        return result;
    }

    private String replacePlaceholders(String input, SessionContext context) {
        Matcher matcher = PLACEHOLDER.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = context.getVariable(varName);
            matcher.appendReplacement(sb, value != null ? Matcher.quoteReplacement(value) : matcher.group(0));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}