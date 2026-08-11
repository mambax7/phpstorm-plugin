package org.xoops.support.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Scaffolds a module under htdocs/modules or modules.
 * Scaffolds a XOOPS module (legacy or hybrid PSR-4) (composer.json, src/, AGENTS.md).
 */
public final class NewXoopsModuleStubAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        String dirname = Messages.showInputDialog(
                project,
                "Module dirname (letters, numbers, underscore):",
                "New XOOPS Module Stub",
                Messages.getQuestionIcon(),
                "mymodule",
                null
        );
        if (dirname == null || dirname.isBlank()) {
            return;
        }
        dirname = dirname.trim().toLowerCase(Locale.ROOT);
        if (!dirname.matches("[a-z][a-z0-9_]{1,32}")) {
            Messages.showErrorDialog(project, "Invalid dirname.", "New XOOPS Module Stub");
            return;
        }

        int style = Messages.showYesNoCancelDialog(
                project,
                "Include modern extras (composer.json, src/ PSR-4 service, AGENTS.md)?",
                "New XOOPS Module Stub",
                "Yes - hybrid",
                "Legacy only",
                "Cancel",
                Messages.getQuestionIcon()
        );
        if (style == Messages.CANCEL) {
            return;
        }
        boolean modern = style == Messages.YES;

        VirtualFile base = findModulesRoot(project);
        if (base == null) {
            Messages.showErrorDialog(
                    project,
                    "Could not find a modules/ directory. Create htdocs/modules first.",
                    "New XOOPS Module Stub"
            );
            return;
        }
        String finalDirname = dirname;
        try {
            WriteAction.runAndWait(() -> {
                VirtualFile moduleDir = base.findChild(finalDirname);
                if (moduleDir != null) {
                    throw new IOException("Module directory already exists: " + finalDirname);
                }
                moduleDir = base.createChildDirectory(this, finalDirname);
                String upper = finalDirname.toUpperCase(Locale.ROOT);
                String cap = Character.toUpperCase(finalDirname.charAt(0)) + finalDirname.substring(1);

                write(moduleDir, "xoops_version.php", xoopsVersion(finalDirname, upper));
                write(moduleDir, "index.php", indexPhp(finalDirname));
                VirtualFile lang = moduleDir.createChildDirectory(this, "language");
                VirtualFile en = lang.createChildDirectory(this, "english");
                write(en, "modinfo.php", modinfo(upper, finalDirname, cap));
                write(en, "main.php", mainLang(upper, cap));
                VirtualFile templates = moduleDir.createChildDirectory(this, "templates");
                write(templates, finalDirname + "_index.tpl",
                        "<{* " + finalDirname + " index *}>\n"
                                + "<{include file=\"db:system_header.tpl\"}>\n"
                                + "<div class=\"module-" + finalDirname + "\">\n"
                                + "    <h1><{$smarty.const._MD_" + upper + "_TITLE|escape}></h1>\n"
                                + "    <p><{$message|default:''|escape}></p>\n"
                                + "</div>\n"
                                + "<{include file=\"db:system_footer.tpl\"}>\n");

                if (modern) {
                    write(moduleDir, "composer.json", composerJson(finalDirname, cap));
                    write(moduleDir, "AGENTS.md", agentsMd(cap));
                    moduleDir.createChildDirectory(this, "config");
                    VirtualFile src = moduleDir.createChildDirectory(this, "src");
                    VirtualFile service = src.createChildDirectory(this, "Service");
                    write(service, cap + "Service.php", servicePhp(cap));
                }
            });
            Messages.showInfoMessage(
                    project,
                    "Created modules/" + dirname
                            + (modern ? " (hybrid: composer + src + AGENTS.md)" : " (legacy stub)"),
                    "New XOOPS Module Stub"
            );
        } catch (Exception ex) {
            Messages.showErrorDialog(project, ex.getMessage(), "New XOOPS Module Stub");
        }
    }

    private static VirtualFile findModulesRoot(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return null;
        }
        VirtualFile root = VfsUtil.findFileByIoFile(new java.io.File(basePath), true);
        if (root == null) {
            return null;
        }
        VirtualFile htdocsModules = VfsUtil.findRelativeFile("htdocs/modules", root);
        if (htdocsModules != null && htdocsModules.isDirectory()) {
            return htdocsModules;
        }
        VirtualFile modules = VfsUtil.findRelativeFile("modules", root);
        if (modules != null && modules.isDirectory()) {
            return modules;
        }
        return null;
    }

    private static void write(VirtualFile dir, String name, String content) throws IOException {
        VirtualFile file = dir.createChildData(NewXoopsModuleStubAction.class, name);
        file.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String xoopsVersion(String dirname, String upper) {
        return """
                <?php
                
                defined('XOOPS_ROOT_PATH') || exit('Restricted access');
                
                $modversion['name']        = _MI_%s_NAME;
                $modversion['version']     = '0.1.0';
                $modversion['description'] = _MI_%s_DESC;
                $modversion['author']      = 'XOOPS Project';
                $modversion['credits']     = 'XOOPS Project';
                $modversion['license']     = 'GPL see LICENSE';
                $modversion['official']    = 0;
                $modversion['image']       = 'assets/images/logo.png';
                $modversion['dirname']     = '%s';
                
                $modversion['hasMain'] = 1;
                
                $modversion['templates'][] = [
                    'file'        => '%s_index.tpl',
                    'description' => 'Module index',
                ];
                """.formatted(upper, upper, dirname, dirname);
    }

    private static String indexPhp(String dirname) {
        return """
                <?php
                
                defined('XOOPS_ROOT_PATH') || exit('Restricted access');
                
                include_once __DIR__ . '/../../mainfile.php';
                $GLOBALS['xoopsOption']['template_main'] = '%s_index.tpl';
                include_once XOOPS_ROOT_PATH . '/header.php';
                $GLOBALS['xoopsTpl']->assign('message', '');
                include_once XOOPS_ROOT_PATH . '/footer.php';
                """.formatted(dirname);
    }

    private static String modinfo(String upper, String dirname, String cap) {
        return """
                <?php
                
                defined('XOOPS_ROOT_PATH') || exit('Restricted access');
                
                define('_MI_%s_NAME', '%s');
                define('_MI_%s_DESC', '%s module for XOOPS');
                """.formatted(upper, cap, upper, cap);
    }

    private static String mainLang(String upper, String cap) {
        return """
                <?php
                
                defined('XOOPS_ROOT_PATH') || exit('Restricted access');
                
                define('_MD_%s_TITLE', '%s');
                """.formatted(upper, cap);
    }

    private static String composerJson(String dirname, String cap) {
        return """
                {
                  "name": "xoopsmodules/%s",
                  "description": "XOOPS module %s",
                  "type": "xoops-module",
                  "license": "GPL-2.0-or-later",
                  "autoload": {
                    "psr-4": {
                      "XoopsModules\\\\%s\\\\": "src/"
                    }
                  }
                }
                """.formatted(dirname, cap, cap);
    }

    private static String servicePhp(String cap) {
        return """
                <?php
                
                declare(strict_types=1);
                
                namespace XoopsModules\\%s\\Service;
                
                defined('XOOPS_ROOT_PATH') || exit('Restricted access');
                
                final class %sService
                {
                    public function status(): string
                    {
                        return '%s service active';
                    }
                }
                """.formatted(cap, cap, cap);
    }

    private static String agentsMd(String cap) {
        return """
                # %s module guidelines
                
                - Guard entry points with `defined('XOOPS_ROOT_PATH') || exit('Restricted access');`
                - Prefer `\\Xmf\\Request` over superglobals
                - Use `exec()` for mutating SQL; guard fetches with `isResultSet`
                - Templates use XOOPS Smarty delimiters `<{ }>` and `|escape` on output
                """.formatted(cap);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
