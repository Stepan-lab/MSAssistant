package ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import model.Sequence;
import model.Step;
import model.VariableDefinition;
import session.SessionContext;
import variables.VariableExtractor;
import variables.VariableResolver;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;


public class SequenceEditorPanel extends JPanel {

    private final MontoyaApi api;
    private final Sequence sequence;
    private final DefaultTableModel tableModel;
    private final JTable stepsTable;
    private JTextArea requestViewer = new JTextArea();
    private JTextArea responseViewer = new JTextArea();
    private VariableManagerPanel variableManagerPanel;

    private final VariableExtractor extractor;
    private final VariableResolver resolver;

    public SequenceEditorPanel(MontoyaApi api) {
        this.api = api;
        this.sequence = new Sequence("New Sequence");
        this.extractor = new VariableExtractor();
        this.resolver = new VariableResolver();
        setLayout(new BorderLayout());

        tableModel = new DefaultTableModel(new Object[]{"#", "Method", "URL", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        stepsTable = new JTable(tableModel);
        stepsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stepsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = stepsTable.getSelectedRow();
            if (row >= 0) {
                Step step = sequence.getSteps().get(row);
                requestViewer.setText(step.getRequest().toString());
                responseViewer.setText(step.getResponse() != null ? step.getResponse().toString() : "No response yet.");
                if (variableManagerPanel != null && step.getResponse() != null) {
                    variableManagerPanel.analyzeResponse(step.getResponse());
                }
            } else {
                requestViewer.setText("");
                responseViewer.setText("");
            }
        });

        JScrollPane tableScroll = new JScrollPane(stepsTable);
        tableScroll.setPreferredSize(new Dimension(300, 150));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addStepButton = new JButton("Add from Proxy");
        addStepButton.addActionListener(e -> addLastRequestFromProxy());
        buttonPanel.add(addStepButton);

        JButton removeStepButton = new JButton("Remove");
        removeStepButton.addActionListener(e -> removeSelectedStep());
        buttonPanel.add(removeStepButton);

        JButton moveUpButton = new JButton("Up");
        moveUpButton.addActionListener(e -> moveStep(-1));
        buttonPanel.add(moveUpButton);

        JButton moveDownButton = new JButton("Down");
        moveDownButton.addActionListener(e -> moveStep(1));
        buttonPanel.add(moveDownButton);

        JButton executeStepButton = new JButton("Execute Step");
        executeStepButton.addActionListener(this::executeSelectedStep);
        buttonPanel.add(executeStepButton);

        JButton executeAllButton = new JButton("Execute All");
        executeAllButton.addActionListener(e -> executeAllSteps());
        buttonPanel.add(executeAllButton);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(tableScroll, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        JSplitPane upperSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("Request (use {{variable}} for placeholders, {{payload}} for attack)"));
        requestViewer.setEditable(false);
        JScrollPane requestScroll = new JScrollPane(requestViewer);
        requestPanel.add(requestScroll, BorderLayout.CENTER);

        JPanel editButtonsPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        JButton editBodyCookieBtn = new JButton("Edit Body & Cookie");
        editBodyCookieBtn.addActionListener(e -> openBodyCookieEditor());
        JButton editHeadersBtn = new JButton("Edit Headers");
        editHeadersBtn.addActionListener(e -> openHeadersEditor());
        editButtonsPanel.add(editBodyCookieBtn);
        editButtonsPanel.add(editHeadersBtn);
        requestPanel.add(editButtonsPanel, BorderLayout.SOUTH);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responseViewer.setEditable(false);
        JScrollPane responseScroll = new JScrollPane(responseViewer);
        responsePanel.add(responseScroll, BorderLayout.CENTER);

        upperSplit.setLeftComponent(requestPanel);
        upperSplit.setRightComponent(responsePanel);
        upperSplit.setResizeWeight(0.5);

        add(upperSplit, BorderLayout.CENTER);
    }

    public void setVariableManagerPanel(VariableManagerPanel panel) {
        this.variableManagerPanel = panel;
    }

    //  Редактор тела и Cookie
    private void openBodyCookieEditor() {
        int row = stepsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a step first.");
            return;
        }
        Step step = sequence.getSteps().get(row);
        HttpRequest original = step.getRequest();

        JTextArea bodyArea = new JTextArea(original.bodyToString(), 5, 40);
        bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        String currentCookie = "";
        for (var h : original.headers()) {
            if (h.name().equalsIgnoreCase("Cookie")) {
                currentCookie = h.value();
                break;
            }
        }
        JTextField cookieField = new JTextField(currentCookie, 25);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel("Body:"), BorderLayout.NORTH);
        panel.add(bodyScroll, BorderLayout.CENTER);

        JPanel cookiePanel = new JPanel(new BorderLayout(5, 5));
        cookiePanel.add(new JLabel("Cookie:"), BorderLayout.WEST);
        cookiePanel.add(cookieField, BorderLayout.CENTER);
        panel.add(cookiePanel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Body & Cookie",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            HttpRequest modified = original.withBody(bodyArea.getText());
            if (!cookieField.getText().trim().isEmpty()) {
                modified = modified.withHeader("Cookie", cookieField.getText().trim());
            }
            step.setRequest(modified);
            tableModel.setValueAt(modified.method(), row, 1);
            tableModel.setValueAt(modified.url(), row, 2);
            requestViewer.setText(modified.toString());
            JOptionPane.showMessageDialog(this, "Request updated successfully.", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    //  Редактор заголовков
    private void openHeadersEditor() {
        int row = stepsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a step first.");
            return;
        }
        Step step = sequence.getSteps().get(row);
        HttpRequest original = step.getRequest();

        // Собираем все заголовки в виде текста
        StringBuilder headersText = new StringBuilder();
        for (var h : original.headers()) {
            headersText.append(h.name()).append(": ").append(h.value()).append("\n");
        }

        JTextArea headersArea = new JTextArea(headersText.toString(), 10, 50);
        headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(headersArea);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel("Edit headers (one per line: Name: Value):"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Headers",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            HttpRequest modified = applyHeaders(original, headersArea.getText());
            step.setRequest(modified);
            requestViewer.setText(modified.toString());
            JOptionPane.showMessageDialog(this, "Request updated successfully.", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private HttpRequest applyHeaders(HttpRequest original, String headersText) {
        HttpRequest modified = original;


        java.util.Set<String> namesToRemove = new java.util.HashSet<>();
        for (var h : original.headers()) {
            namesToRemove.add(h.name());
        }
        for (String name : namesToRemove) {
            modified = modified.withRemovedHeader(name);
        }


        String[] lines = headersText.split("\\n");
        for (String line : lines) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String name = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                if (!name.isEmpty()) {
                    modified = modified.withHeader(name, value);
                }
            }
        }

        return modified;
    }

    public void addStep(HttpRequest request) {
        sequence.addStep(new Step(request));
        tableModel.addRow(new Object[]{sequence.getSteps().size(), request.method(), request.url(), "—"});
    }

    public Sequence getSequence() { return sequence; }

    public Step getSelectedStep() {
        int row = stepsTable.getSelectedRow();
        return (row >= 0) ? sequence.getSteps().get(row) : null;
    }

    private void addLastRequestFromProxy() {
        HttpRequest lastRequest = getLastRequestFromProxy();
        if (lastRequest != null) {
            addStep(lastRequest);
        } else {
            JOptionPane.showMessageDialog(this, "No request found in Proxy history.", "Add Step", JOptionPane.WARNING_MESSAGE);
        }
    }

    private HttpRequest getLastRequestFromProxy() {
        List<ProxyHttpRequestResponse> history = api.proxy().history();
        if (history != null && !history.isEmpty()) {
            return history.get(history.size() - 1).request();
        }
        return null;
    }

    private void removeSelectedStep() {
        int selectedRow = stepsTable.getSelectedRow();
        if (selectedRow >= 0) {
            sequence.getSteps().remove(selectedRow);
            tableModel.removeRow(selectedRow);
            updateRowNumbers();
            requestViewer.setText("");
            responseViewer.setText("");
        }
    }

    private void moveStep(int delta) {
        int selectedRow = stepsTable.getSelectedRow();
        if (selectedRow < 0) return;
        int newRow = selectedRow + delta;
        if (newRow < 0 || newRow >= tableModel.getRowCount()) return;
        Step moved = sequence.getSteps().remove(selectedRow);
        sequence.getSteps().add(newRow, moved);
        tableModel.removeRow(selectedRow);
        tableModel.insertRow(newRow, new Object[]{newRow + 1, moved.getRequest().method(), moved.getRequest().url(), "—"});
        updateRowNumbers();
        stepsTable.setRowSelectionInterval(newRow, newRow);
    }

    private void updateRowNumbers() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(i + 1, i, 0);
        }
    }

    private void executeSelectedStep(ActionEvent e) {
        int selectedRow = stepsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a step to execute.");
            return;
        }
        executeStep(sequence.getSteps().get(selectedRow), selectedRow);
    }

    private void executeAllSteps() {

        new Thread(() -> {
            for (int i = 0; i < sequence.getSteps().size(); i++) {
                Step step = sequence.getSteps().get(i);
                executeStepSequentially(step, i);
            }
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "All steps executed.");
            });
        }).start();
    }

    private void executeStepSequentially(Step step, int rowIndex) {
        SessionContext tempContext = new SessionContext();
        // Собираем переменные из предыдущих уже выполненных шагов
        for (int i = 0; i < rowIndex; i++) {
            Step prevStep = sequence.getSteps().get(i);
            if (prevStep.getResponse() != null) {
                for (VariableDefinition def : prevStep.getExtractions()) {
                    String val = extractor.extractValue(prevStep.getResponse(), def);
                    if (val != null) {
                        tempContext.setVariable(def.getName(), val);
                    }
                }
            }
        }

        HttpRequest requestToExecute = step.getRequest();
        HttpRequest resolved = resolver.resolve(requestToExecute, tempContext);

        SwingUtilities.invokeLater(() -> {
            step.setStatus(Step.Status.EXECUTING);
            updateStepStatusInTable(rowIndex, "Executing...");
        });

        try {
            HttpResponse response = api.http().sendRequest(resolved).response();
            SwingUtilities.invokeLater(() -> {
                step.setResponse(response);
                step.setStatus(Step.Status.SUCCESS);
                updateStepStatusInTable(rowIndex, String.valueOf(response.statusCode()));

                for (VariableDefinition def : step.getExtractions()) {
                    String val = extractor.extractValue(response, def);
                    if (val != null) {
                        sequence.setLastVariableValue(def.getName(), val);
                        api.logging().logToOutput("Extracted " + def.getName() + " = " + val);
                    }
                }

                if (variableManagerPanel != null) {
                    variableManagerPanel.refreshAllVariablesTable();
                    variableManagerPanel.analyzeResponse(response);
                }

                if (stepsTable.getSelectedRow() == rowIndex) {
                    requestViewer.setText(resolved.toString());
                    responseViewer.setText(response.toString());
                }
            });
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                step.setStatus(Step.Status.ERROR);
                step.setErrorMessage(ex.getMessage());
                updateStepStatusInTable(rowIndex, "Error");
            });
        }
    }

    private void executeStep(Step step, int rowIndex) {
        SessionContext tempContext = new SessionContext();
        for (int i = 0; i < rowIndex; i++) {
            Step prevStep = sequence.getSteps().get(i);
            if (prevStep.getResponse() != null) {
                for (VariableDefinition def : prevStep.getExtractions()) {
                    String val = extractor.extractValue(prevStep.getResponse(), def);
                    if (val != null) tempContext.setVariable(def.getName(), val);
                }
            }
        }

        HttpRequest requestToExecute = step.getRequest();
        HttpRequest resolved = resolver.resolve(requestToExecute, tempContext);
        step.setStatus(Step.Status.EXECUTING);
        updateStepStatusInTable(rowIndex, "Executing...");

        new Thread(() -> {
            try {
                HttpResponse response = api.http().sendRequest(resolved).response();
                SwingUtilities.invokeLater(() -> {
                    step.setResponse(response);
                    step.setStatus(Step.Status.SUCCESS);
                    updateStepStatusInTable(rowIndex, String.valueOf(response.statusCode()));
                    for (VariableDefinition def : step.getExtractions()) {
                        String val = extractor.extractValue(response, def);
                        if (val != null) {
                            sequence.setLastVariableValue(def.getName(), val);
                            api.logging().logToOutput("Extracted " + def.getName() + " = " + val);
                        }
                    }
                    if (variableManagerPanel != null) {
                        variableManagerPanel.refreshAllVariablesTable();
                        if (stepsTable.getSelectedRow() == rowIndex)
                            variableManagerPanel.analyzeResponse(response);
                    }
                    requestViewer.setText(requestToExecute.toString());
                    responseViewer.setText(response.toString());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    step.setStatus(Step.Status.ERROR);
                    step.setErrorMessage(ex.getMessage());
                    updateStepStatusInTable(rowIndex, "Error");
                });
            }
        }).start();
    }

    private void updateStepStatusInTable(int row, String status) {
        if (row >= 0 && row < tableModel.getRowCount()) {
            tableModel.setValueAt(status, row, 3);
        }
    }
}