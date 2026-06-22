import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import ui.MultiStepTab;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class Extension implements BurpExtension {

    private MultiStepTab multiStepTab;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Multi-Step Attack Assistant");

        multiStepTab = new MultiStepTab(api);
        api.userInterface().registerSuiteTab("Multi-Step", multiStepTab);


        api.userInterface().registerContextMenuItemsProvider(new ContextMenuItemsProvider() {
            @Override
            public List<Component> provideMenuItems(ContextMenuEvent event) {
                JMenuItem menuItem = new JMenuItem("Send to Multi-Step");
                menuItem.addActionListener(l -> {
                    List<HttpRequestResponse> selected = event.selectedRequestResponses();
                    for (HttpRequestResponse item : selected) {
                        multiStepTab.getSequenceEditorPanel().addStep(item.request());
                    }
                    api.logging().logToOutput("Added " + selected.size() + " request(s) to Multi-Step.");
                });
                return List.of(menuItem);
            }
        });

        api.logging().logToOutput("Multi-Step Attack Assistant loaded.");
    }
}