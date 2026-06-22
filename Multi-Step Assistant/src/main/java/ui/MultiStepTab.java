package ui;

import burp.api.montoya.MontoyaApi;
import javax.swing.*;
import java.awt.*;

public class MultiStepTab extends JPanel {
    private SequenceEditorPanel sequenceEditorPanel;
    private VariableManagerPanel variableManagerPanel;
    private AttackPanel attackPanel;

    public MultiStepTab(MontoyaApi api) {
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();

        sequenceEditorPanel = new SequenceEditorPanel(api);
        variableManagerPanel = new VariableManagerPanel(api, sequenceEditorPanel);
        sequenceEditorPanel.setVariableManagerPanel(variableManagerPanel);

        attackPanel = new AttackPanel(api, sequenceEditorPanel);

        tabs.addTab("Sequence Editor", sequenceEditorPanel);
        tabs.addTab("Variable Manager", variableManagerPanel);
        tabs.addTab("Attack", attackPanel);

        add(tabs, BorderLayout.CENTER);
        add(new JLabel("Ready"), BorderLayout.SOUTH);
    }

    public SequenceEditorPanel getSequenceEditorPanel() { return sequenceEditorPanel; }
    public VariableManagerPanel getVariableManagerPanel() { return variableManagerPanel; }
    public AttackPanel getAttackPanel() { return attackPanel; }
}