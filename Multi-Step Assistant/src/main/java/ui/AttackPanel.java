package ui;
import attack.AttackConfig;
import attack.AttackCoordinator;
import burp.api.montoya.MontoyaApi;
import model.Sequence;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AttackPanel extends JPanel {

    private final MontoyaApi api;
    private final SequenceEditorPanel sequenceEditorPanel;

    // Настройки
    private JComboBox<String> sequenceCombo;
    private JButton refreshSequencesButton;
    private JSpinner stepSpinner;
    private JTextField parameterField;
    private JTextArea payloadsArea;
    private JButton loadPayloadsFromFileButton;
    private JSpinner threadsSpinner;

    // Управление
    private JButton startButton;
    private JButton stopButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;

    // Результаты
    private DefaultTableModel resultsTableModel;
    private JTable resultsTable;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private JTextArea requestViewer;
    private JTextArea responseViewer;

    // Фильтры
    private JTextField searchField;
    private JTextField statusFilterField;
    private JTextField regexFilterField;
    private JButton applyFilterButton;
    private JButton clearFilterButton;

    private List<AttackResult> results = new ArrayList<>();
    private AttackCoordinator currentCoordinator;
    private volatile boolean attackRunning = false;

    public AttackPanel(MontoyaApi api, SequenceEditorPanel sequenceEditorPanel) {
        this.api = api;
        this.sequenceEditorPanel = sequenceEditorPanel;
        initComponents();
        updateButtonStates();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));

        // === Верхняя панель с настройками ===
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 5, 3, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        int row = 0;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        settingsPanel.add(new JLabel("Sequence:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JPanel seqPanel = new JPanel(new BorderLayout(3, 0));
        sequenceCombo = new JComboBox<>();
        refreshSequencesButton = new JButton("Refresh");
        refreshSequencesButton.addActionListener(e -> refreshSequenceList());
        seqPanel.add(sequenceCombo, BorderLayout.CENTER);
        seqPanel.add(refreshSequencesButton, BorderLayout.EAST);
        settingsPanel.add(seqPanel, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        settingsPanel.add(new JLabel("Payload Step #:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        stepSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        settingsPanel.add(stepSpinner, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        settingsPanel.add(new JLabel("Payloads:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        payloadsArea = new JTextArea(4, 30);
        payloadsArea.setToolTipText("One payload per line. Use {{payload}} in request.");
        JScrollPane payloadsScroll = new JScrollPane(payloadsArea);
        loadPayloadsFromFileButton = new JButton("Load from file...");
        loadPayloadsFromFileButton.addActionListener(this::loadPayloadsFromFile);
        JPanel payloadPanel = new JPanel(new BorderLayout(3, 0));
        payloadPanel.add(payloadsScroll, BorderLayout.CENTER);
        payloadPanel.add(loadPayloadsFromFileButton, BorderLayout.SOUTH);
        settingsPanel.add(payloadPanel, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        settingsPanel.add(new JLabel("Threads:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        threadsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        settingsPanel.add(threadsSpinner, gbc);

        add(settingsPanel, BorderLayout.NORTH);

        // === Центр: фильтры + таблица + просмотр ===
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));

        // Панель фильтров
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filters"));

        filterPanel.add(new JLabel("Search payload:"));
        searchField = new JTextField(8);
        filterPanel.add(searchField);

        filterPanel.add(new JLabel("Status:"));
        statusFilterField = new JTextField(5);
        filterPanel.add(statusFilterField);

        filterPanel.add(new JLabel("Regex in response:"));
        regexFilterField = new JTextField(12);
        filterPanel.add(regexFilterField);

        applyFilterButton = new JButton("Apply");
        applyFilterButton.addActionListener(e -> applyFilters());
        filterPanel.add(applyFilterButton);

        clearFilterButton = new JButton("Clear");
        clearFilterButton.addActionListener(e -> clearFilters());
        filterPanel.add(clearFilterButton);

        centerPanel.add(filterPanel, BorderLayout.NORTH);

        // Таблица + просмотр
        JSplitPane resultsSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        resultsTableModel = new DefaultTableModel(new Object[]{"#", "Payload", "Status", "Length"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        resultsTable = new JTable(resultsTableModel);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        resultsTable.setAutoCreateRowSorter(true);

        rowSorter = new TableRowSorter<>(resultsTableModel);
        resultsTable.setRowSorter(rowSorter);

        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = resultsTable.getSelectedRow();
            if (viewRow >= 0) {
                int modelRow = resultsTable.convertRowIndexToModel(viewRow);
                if (modelRow >= 0 && modelRow < results.size()) {
                    AttackResult r = results.get(modelRow);
                    requestViewer.setText(r.request != null ? r.request : "");
                    responseViewer.setText(r.response != null ? r.response : "");
                }
            }
        });
        JScrollPane resultsScroll = new JScrollPane(resultsTable);
        resultsSplit.setTopComponent(resultsScroll);

        // Просмотр запроса/ответа
        JSplitPane viewerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        JPanel reqPanel = new JPanel(new BorderLayout());
        reqPanel.setBorder(BorderFactory.createTitledBorder("Request"));
        requestViewer = new JTextArea();
        requestViewer.setEditable(false);
        reqPanel.add(new JScrollPane(requestViewer), BorderLayout.CENTER);

        JPanel respPanel = new JPanel(new BorderLayout());
        respPanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responseViewer = new JTextArea();
        responseViewer.setEditable(false);
        respPanel.add(new JScrollPane(responseViewer), BorderLayout.CENTER);

        viewerSplit.setLeftComponent(reqPanel);
        viewerSplit.setRightComponent(respPanel);
        viewerSplit.setResizeWeight(0.5);

        resultsSplit.setBottomComponent(viewerSplit);
        resultsSplit.setResizeWeight(0.5);

        centerPanel.add(resultsSplit, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // === Нижняя панель ===
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottomPanel.add(progressBar, BorderLayout.NORTH);

        JPanel controlPanel = new JPanel(new BorderLayout(5, 5));
        statusLabel = new JLabel("Idle");
        controlPanel.add(statusLabel, BorderLayout.WEST);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        startButton = new JButton("Start Attack");
        startButton.addActionListener(this::startAttack);
        buttonsPanel.add(startButton);

        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopAttack());
        buttonsPanel.add(stopButton);
        controlPanel.add(buttonsPanel, BorderLayout.EAST);

        bottomPanel.add(controlPanel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void applyFilters() {
        List<RowFilter<Object, Object>> filters = new ArrayList<>();

        // Поиск по payload
        String searchText = searchField.getText().trim();
        if (!searchText.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(searchText), 1));
        }

        // Фильтр по точному статусу
        String statusText = statusFilterField.getText().trim();
        if (!statusText.isEmpty()) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(statusText) + "$", 2));
        }

        // Фильтр по регулярке в ответе
        String regexText = regexFilterField.getText().trim();
        if (!regexText.isEmpty()) {
            try {
                Pattern.compile(regexText);
                filters.add(new RowFilter<Object, Object>() {
                    @Override
                    public boolean include(Entry<?, ?> entry) {
                        int modelRow = (int) entry.getIdentifier();
                        if (modelRow >= 0 && modelRow < results.size()) {
                            AttackResult r = results.get(modelRow);
                            return r.response != null && r.response.contains(regexText);
                        }
                        return false;
                    }
                });
            } catch (PatternSyntaxException ex) {
                JOptionPane.showMessageDialog(this, "Invalid regex: " + ex.getMessage());
                return;
            }
        }

        rowSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private void clearFilters() {
        searchField.setText("");
        statusFilterField.setText("");
        regexFilterField.setText("");
        rowSorter.setRowFilter(null);
    }

    private void updateButtonStates() {
        startButton.setEnabled(!attackRunning);
        stopButton.setEnabled(attackRunning);
        stepSpinner.setEnabled(!attackRunning);
        payloadsArea.setEnabled(!attackRunning);
        threadsSpinner.setEnabled(!attackRunning);
        loadPayloadsFromFileButton.setEnabled(!attackRunning);
        refreshSequencesButton.setEnabled(!attackRunning);
        statusLabel.setText(attackRunning ? "Running..." : "Idle");
    }

    private void refreshSequenceList() {
        sequenceCombo.removeAllItems();
        Sequence seq = sequenceEditorPanel.getSequence();
        if (seq != null && !seq.getSteps().isEmpty()) {
            sequenceCombo.addItem(seq.getName() + " (active)");
            sequenceCombo.setSelectedIndex(0);
        } else {
            sequenceCombo.addItem("No sequence defined");
        }
    }

    private void loadPayloadsFromFile(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(
                        fileChooser.getSelectedFile().toPath()));
                payloadsArea.setText(content);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to load file: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void startAttack(ActionEvent e) {
        Sequence seq = sequenceEditorPanel.getSequence();
        if (seq == null || seq.getSteps().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No sequence defined.");
            return;
        }
        if (payloadsArea.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Please enter payloads.");
            return;
        }

        String[] lines = payloadsArea.getText().split("\\n");
        List<String> payloads = new ArrayList<>();
        for (String line : lines) {
            if (!line.trim().isEmpty()) payloads.add(line.trim());
        }

        AttackConfig config = new AttackConfig();
        config.setSequence(seq);
        config.setPayloadPositionStepIndex((int) stepSpinner.getValue() - 1);
        config.setPayloadParameter("");
        config.setPayloads(payloads);
        config.setThreads((int) threadsSpinner.getValue());

        results.clear();
        resultsTableModel.setRowCount(0);
        requestViewer.setText("");
        responseViewer.setText("");

        currentCoordinator = new AttackCoordinator(api, config);
        currentCoordinator.setLogConsumer(this::addResult);
        currentCoordinator.setProgressConsumer(this::updateProgress);

        attackRunning = true;
        updateButtonStates();
        progressBar.setValue(0);
        progressBar.setMaximum(payloads.size());

        new Thread(() -> currentCoordinator.start()).start();
    }

    private void stopAttack() {
        if (currentCoordinator != null) {
            currentCoordinator.stop();
        }
        attackRunning = false;
        updateButtonStates();
    }

    private void addResult(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.startsWith("Attack started") || message.startsWith("Attack completed") || message.startsWith("Attack stopped")) {
                return;
            }
            String[] parts = message.split("\\|", 5);
            if (parts.length >= 3) {
                AttackResult r = new AttackResult();
                r.payload = parts[0];
                r.status = parts[1];
                r.length = parts[2];
                r.request = parts.length > 3 ? parts[3] : "";
                r.response = parts.length > 4 ? parts[4] : "";
                results.add(r);
                resultsTableModel.addRow(new Object[]{results.size(), r.payload, r.status, r.length});
            }
        });
    }

    private void updateProgress(int completed) {
        SwingUtilities.invokeLater(() -> progressBar.setValue(completed));
    }

    private static class AttackResult {
        String payload;
        String status;
        String length;
        String request;
        String response;
    }
}