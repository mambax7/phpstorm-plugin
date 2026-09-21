package org.xoops.support.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@Service(Service.Level.PROJECT)
@State(name = "XoopsSupportSettings", storages = @Storage("xoopsSupport.xml"))
public final class XoopsSettingsState implements PersistentStateComponent<XoopsSettingsState> {

    public boolean enabled = true;
    public boolean suppressStartupNotification = false;
    /**
     * When true, the Overview tool window scans the project as soon as it opens.
     * Default false: full module tree walks are expensive on monorepos / multi-project
     * boot; user must click Refresh (or Tools → Refresh XOOPS Overview).
     */
    public boolean autoScanOnToolWindowOpen = false;
    /**
     * Auto | 2.5 | 2.7 | 4.0.
     * Null means the XML did not contain this key (Alpha 3+). Distinguishes missing
     * from an explicit {@code Auto} when both this and {@link #coreProfile} are present.
     */
    public String coreVersion;
    /**
     * Alpha 2 persisted name. Read on load, then cleared so it is not written again.
     */
    public String coreProfile;
    public String tablePrefix = "";

    public static @NotNull XoopsSettingsState getInstance(@NotNull Project project) {
        return project.getService(XoopsSettingsState.class);
    }

    /**
     * Never null after {@link #loadState}; {@code Auto} when unset.
     */
    public @NotNull String resolvedCoreVersion() {
        return (coreVersion == null || coreVersion.isBlank()) ? "Auto" : coreVersion;
    }

    @Override
    public @Nullable XoopsSettingsState getState() {
        coreProfile = null;
        if (coreVersion == null || coreVersion.isBlank()) {
            coreVersion = "Auto";
        }
        return this;
    }

    @Override
    public void loadState(@NotNull XoopsSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
        migrateLoadedCoreVersion();
    }

    /**
     * Precedence: non-null {@code coreVersion} (including explicit {@code Auto}) wins.
     * Else {@code coreProfile}. Else {@code Auto}.
     */
    void migrateLoadedCoreVersion() {
        if (coreVersion == null || coreVersion.isBlank()) {
            if (coreProfile != null && !coreProfile.isBlank()) {
                coreVersion = coreProfile;
            } else {
                coreVersion = "Auto";
            }
        }
        coreProfile = null;
    }

    @TestOnly
    static @NotNull XoopsSettingsState migrateForTest(
            @Nullable String coreVersion,
            @Nullable String coreProfile
    ) {
        XoopsSettingsState s = new XoopsSettingsState();
        s.coreVersion = coreVersion;
        s.coreProfile = coreProfile;
        s.migrateLoadedCoreVersion();
        return s;
    }
}
