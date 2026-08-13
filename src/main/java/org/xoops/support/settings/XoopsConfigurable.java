package org.xoops.support.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;

public final class XoopsConfigurable implements Configurable {

    private final Project project;
    private JCheckBox enabledBox;
    private JCheckBox suppressNotifyBox;
    private JCheckBox autoScanBox;
    private JComboBox<String> profileBox;
    private JTextField prefixField;
    private JPanel panel;

    public XoopsConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NlsContexts.ConfigurableName String getDisplayName() {
        return "XOOPS Support";
    }

    @Override
    public @Nullable JComponent createComponent() {
        panel = new JPanel(new BorderLayout());
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;

        enabledBox = new JCheckBox("Enable XOOPS Support for this project");
        form.add(enabledBox, c);

        c.gridy++;
        suppressNotifyBox = new JCheckBox("Suppress startup notification");
        form.add(suppressNotifyBox, c);

        c.gridy++;
        autoScanBox = new JCheckBox(
                "Auto-scan project when Overview tool window opens (slow on large trees)"
        );
        form.add(autoScanBox, c);

        c.gridy++;
        form.add(new JLabel("Core profile:"), c);
        c.gridx = 1;
        profileBox = new JComboBox<>(new String[]{"Auto", "2.5", "2.7", "4.0"});
        form.add(profileBox, c);

        c.gridx = 0;
        c.gridy++;
        form.add(new JLabel("Table prefix (optional):"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        prefixField = new JTextField(16);
        form.add(prefixField, c);

        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    @Override
    public boolean isModified() {
        XoopsSettingsState s = XoopsSettingsState.getInstance(project);
        String selectedProfile = String.valueOf(profileBox.getSelectedItem());
        String storedProfile = s.coreProfile == null ? "Auto" : s.coreProfile;
        String storedPrefix = s.tablePrefix == null ? "" : s.tablePrefix;
        return enabledBox.isSelected() != s.enabled
                || suppressNotifyBox.isSelected() != s.suppressStartupNotification
                || autoScanBox.isSelected() != s.autoScanOnToolWindowOpen
                || !Objects.equals(selectedProfile, storedProfile)
                || !Objects.equals(prefixField.getText().trim(), storedPrefix);
    }

    @Override
    public void apply() {
        XoopsSettingsState s = XoopsSettingsState.getInstance(project);
        s.enabled = enabledBox.isSelected();
        s.suppressStartupNotification = suppressNotifyBox.isSelected();
        s.autoScanOnToolWindowOpen = autoScanBox.isSelected();
        s.coreProfile = String.valueOf(profileBox.getSelectedItem());
        s.tablePrefix = prefixField.getText().trim();
    }

    @Override
    public void reset() {
        XoopsSettingsState s = XoopsSettingsState.getInstance(project);
        enabledBox.setSelected(s.enabled);
        suppressNotifyBox.setSelected(s.suppressStartupNotification);
        autoScanBox.setSelected(s.autoScanOnToolWindowOpen);
        profileBox.setSelectedItem(s.coreProfile == null ? "Auto" : s.coreProfile);
        prefixField.setText(s.tablePrefix == null ? "" : s.tablePrefix);
    }
}
