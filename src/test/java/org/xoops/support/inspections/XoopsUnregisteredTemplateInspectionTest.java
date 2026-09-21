package org.xoops.support.inspections;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XoopsUnregisteredTemplateInspectionTest {

    @Test
    public void parsesFileAndTemplateKeys() {
        String manifest = """
                <?php
                $modversion['templates'][] = [
                    'file' => 'demo_index.tpl',
                    'description' => 'Index',
                ];
                $modversion['templates'][] = ['template' => 'demo_block.tpl'];
                // 'file' => 'commented.tpl'
                """;
        Set<String> names = XoopsUnregisteredTemplateInspection.registeredTemplates(manifest);
        assertTrue(names.contains("demo_index.tpl"));
        assertTrue(names.contains("demo_block.tpl"));
        assertFalse(names.contains("commented.tpl"));
    }
}
