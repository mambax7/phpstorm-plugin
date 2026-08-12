package org.xoops.support.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(name = "XoopsSupportSettings", storages = @Storage("xoopsSupport.xml"))
public final class XoopsSettingsState implements PersistentStateComponent<XoopsSettingsState> {

    public boolean enabled = true;
    public boolean suppressStartupNotification = false;
    /** Auto | 2.5 | 2.7 | 4.0 */
    public String coreProfile = "Auto";
    public String tablePrefix = "";

    public static @NotNull XoopsSettingsState getInstance(@NotNull Project project) {
        return project.getService(XoopsSettingsState.class);
    }

    @Override
    public @Nullable XoopsSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull XoopsSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
