package ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;
import model.Sequence;
import model.Step;
import model.VariableDefinition;
import variables.VariableExtractor;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class VariableManagerPanel extends JPanel {

    private final MontoyaApi api;
    private final SequenceEditorPanel sequenceEditorPanel;
    private final VariableExtractor extractor;

    // Предложения для текущего выбранного шага
    private DefaultTableModel suggestionTableModel;
    private JTable suggestionTable;
    private JButton addSelectedButton;
    private JButton addCustomButton;
    private List<VariableDefinition> currentSuggestions = new ArrayList<>();

    // Таблица всех переменных
    private DefaultTableModel allVarsTableModel;
    private JTable allVarsTable;
    private JButton deleteVariableButton;

    public VariableManagerPanel(MontoyaApi api, SequenceEditorPanel sequenceEditorPanel) {
        this.api = api;
        this.sequenceEditorPanel = sequenceEditorPanel;
        this.extractor = new VariableExtractor();
        setLayout(new BorderLayout(0, 5));

        // Верхняя часть: предложения
        JPanel topPanel = new JPanel(new BorderLayout());
        suggestionTableModel = new DefaultTableModel(new Object[]{"Use?", "Name", "Type", "Pattern"}, 0) {
            @Override
            public Class<?> getColumnClass(int col) { return col == 0 ? Boolean.class : String.class; }
            @Override
            public boolean isCellEditable(int row, int col) { return col == 0 || col == 1; }
        };
        suggestionTable = new JTable(suggestionTableModel);
        suggestionTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        suggestionTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        suggestionTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        suggestionTable.getColumnModel().getColumn(3).setPreferredWidth(300);
        JScrollPane suggestionScroll = new JScrollPane(suggestionTable);
        topPanel.add(suggestionScroll, BorderLayout.CENTER);

        JPanel suggestionBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addSelectedButton = new JButton("Add Selected to Step (auto-rename)");
        addSelectedButton.addActionListener(this::addSelectedVariables);
        suggestionBtnPanel.add(addSelectedButton);
        addCustomButton = new JButton("Add Custom...");
        addCustomButton.addActionListener(this::addCustomVariable);
        suggestionBtnPanel.add(addCustomButton);
        topPanel.add(suggestionBtnPanel, BorderLayout.SOUTH);
        topPanel.setBorder(BorderFactory.createTitledBorder("Suggestions for selected step"));

        // Нижняя часть: все переменные
        JPanel bottomPanel = new JPanel(new BorderLayout());
        allVarsTableModel = new DefaultTableModel(new Object[]{"Name", "Step #", "Type", "Pattern", "Current Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        allVarsTable = new JTable(allVarsTableModel);
        allVarsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        allVarsTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        allVarsTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        allVarsTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        allVarsTable.getColumnModel().getColumn(3).setPreferredWidth(250);
        allVarsTable.getColumnModel().getColumn(4).setPreferredWidth(200);
        JScrollPane allVarsScroll = new JScrollPane(allVarsTable);
        bottomPanel.add(allVarsScroll, BorderLayout.CENTER);

        // Кнопка удаления переменной
        deleteVariableButton = new JButton("Delete Selected Variable");
        deleteVariableButton.addActionListener(this::deleteSelectedVariable);
        bottomPanel.add(deleteVariableButton, BorderLayout.SOUTH);
        bottomPanel.setBorder(BorderFactory.createTitledBorder("All variables (use as {{name}} in any step)"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, bottomPanel);
        split.setResizeWeight(0.3);
        add(split, BorderLayout.CENTER);

        refreshAllVariablesTable();
    }

    // Анализирует ответ и заполняет предложения
    public void analyzeResponse(HttpResponse response) {
        if (response == null) {
            clearSuggestions();
            return;
        }
        List<VariableDefinition> suggestions = extractor.suggestVariables(response);
        currentSuggestions.clear();
        suggestionTableModel.setRowCount(0);
        for (VariableDefinition def : suggestions) {
            currentSuggestions.add(def);
            suggestionTableModel.addRow(new Object[]{Boolean.FALSE, def.getName(), def.getSourceType().name(), def.getPattern()});
        }
    }

    public void clearSuggestions() {
        currentSuggestions.clear();
        suggestionTableModel.setRowCount(0);
    }

    public void refreshAllVariablesTable() {
        allVarsTableModel.setRowCount(0);
        Sequence seq = sequenceEditorPanel.getSequence();
        if (seq == null) return;
        List<Step> steps = seq.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            for (VariableDefinition def : step.getExtractions()) {
                String currentValue = seq.getLastVariableValue(def.getName());
                allVarsTableModel.addRow(new Object[]{
                        def.getName(),
                        (i + 1),
                        def.getSourceType().name(),
                        def.getPattern(),
                        currentValue != null ? currentValue : ""
                });
            }
        }
    }

    private void addSelectedVariables(ActionEvent e) {
        Step selectedStep = sequenceEditorPanel.getSelectedStep();
        int stepIndex = sequenceEditorPanel.getSequence().getSteps().indexOf(selectedStep);
        if (selectedStep == null) {
            JOptionPane.showMessageDialog(this, "No step selected in Sequence Editor.");
            return;
        }
        int added = 0;
        for (int i = 0; i < suggestionTableModel.getRowCount(); i++) {
            Boolean checked = (Boolean) suggestionTableModel.getValueAt(i, 0);
            if (checked != null && checked) {
                VariableDefinition def = currentSuggestions.get(i);
                String newName = def.getName() + "_" + (stepIndex + 1);
                String tableName = (String) suggestionTableModel.getValueAt(i, 1);
                if (tableName != null && !tableName.isBlank()) {
                    newName = tableName + "_" + (stepIndex + 1);
                }
                VariableDefinition newDef = new VariableDefinition(newName, def.getSourceType(), def.getPattern());
                selectedStep.addExtraction(newDef);
                added++;
            }
        }
        if (added > 0) {
            refreshAllVariablesTable();
            clearSuggestions();
            JOptionPane.showMessageDialog(this, "Added " + added + " variable(s) to step " + (stepIndex + 1) + ".");
        } else {
            JOptionPane.showMessageDialog(this, "No variables selected.");
        }
    }

    private void addCustomVariable(ActionEvent e) {
        Step selectedStep = sequenceEditorPanel.getSelectedStep();
        int stepIndex = sequenceEditorPanel.getSequence().getSteps().indexOf(selectedStep);
        if (selectedStep == null) {
            JOptionPane.showMessageDialog(this, "No step selected in Sequence Editor.");
            return;
        }
        int currentStepNumber = stepIndex + 1;

        JTextField nameField = new JTextField("var_" + currentStepNumber, 15);
        JComboBox<VariableDefinition.SourceType> typeCombo = new JComboBox<>(VariableDefinition.SourceType.values());
        JTextField regexField = new JTextField(25);
        SpinnerNumberModel stepModel = new SpinnerNumberModel(currentStepNumber, 1, sequenceEditorPanel.getSequence().getSteps().size(), 1);
        JSpinner stepSpinner = new JSpinner(stepModel);

        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Type:"));
        panel.add(typeCombo);
        panel.add(new JLabel("Pattern (regex):"));
        panel.add(regexField);
        panel.add(new JLabel("Source step #:"));
        panel.add(stepSpinner);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Custom Variable",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && !nameField.getText().isBlank() && !regexField.getText().isBlank()) {
            String newName = nameField.getText().trim();
            // Проверка уникальности
            if (sequenceEditorPanel.getSequence().getSteps().stream()
                    .flatMap(s -> s.getExtractions().stream())
                    .anyMatch(v -> v.getName().equals(newName))) {
                JOptionPane.showMessageDialog(this, "Variable name already exists. Choose another name.");
                return;
            }
            int targetStepIdx = (int) stepSpinner.getValue() - 1;
            Step targetStep = sequenceEditorPanel.getSequence().getSteps().get(targetStepIdx);
            VariableDefinition def = new VariableDefinition(
                    newName,
                    (VariableDefinition.SourceType) typeCombo.getSelectedItem(),
                    regexField.getText().trim()
            );
            targetStep.addExtraction(def);
            refreshAllVariablesTable();
            api.logging().logToOutput("Added custom variable: " + newName + " to step " + (targetStepIdx + 1));
        }
    }

    private void deleteSelectedVariable(ActionEvent e) {
        int selectedRow = allVarsTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a variable to delete.");
            return;
        }
        String varName = (String) allVarsTableModel.getValueAt(selectedRow, 0);
        int stepNumber = (int) allVarsTableModel.getValueAt(selectedRow, 1);
        if (varName == null) return;

        // Ищем переменную в шаге
        List<Step> steps = sequenceEditorPanel.getSequence().getSteps();
        if (stepNumber > 0 && stepNumber <= steps.size()) {
            Step step = steps.get(stepNumber - 1);
            step.getExtractions().removeIf(def -> def.getName().equals(varName));
            // Обновляем таблицу и очищаем последнее значение
            sequenceEditorPanel.getSequence().getLastVariableValues().remove(varName);
            refreshAllVariablesTable();
            api.logging().logToOutput("Deleted variable: " + varName);
        } else {
            JOptionPane.showMessageDialog(this, "Cannot find step for this variable.");
        }
    }
}