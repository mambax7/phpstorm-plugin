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

    @Test
    public void commentMarkersInsideStringsAreNotComments() {
        String manifest = """
                <?php
                $modversion['templates'][] = ['description' => 'see https://xoops.org #1', 'file' => 'listed.tpl'];
                // 'file' => 'commented.tpl'
                """;
        Set<String> names = XoopsUnregisteredTemplateInspection.registeredTemplates(manifest);
        assertTrue(names.contains("listed.tpl"));
        assertFalse(names.contains("commented.tpl"));
    }

    @Test
    public void assignmentSyntaxRegistersTemplatesToo() {
        String manifest = """
                <?php
                $modversion['blocks'][1]['template'] = 'demo_block.tpl';
                $modversion['templates'][2]['file'] = "demo_list.tpl";
                """;
        Set<String> names = XoopsUnregisteredTemplateInspection.registeredTemplates(manifest);
        assertTrue(names.contains("demo_block.tpl"));
        assertTrue(names.contains("demo_list.tpl"));
    }

    @Test
    public void moduleRootPrefixedRegistrationsMatchRelativeNames() {
        String manifest = """
                <?php
                $modversion['templates'][] = ['file' => 'templates/rooted.tpl'];
                $modversion['templates'][] = ['template' => 'blocks/rooted_block.tpl'];
                """;
        Set<String> names = XoopsUnregisteredTemplateInspection.registeredTemplates(manifest);
        assertTrue(names.contains("rooted.tpl"));
        assertTrue(names.contains("rooted_block.tpl"));
        assertTrue(names.contains("templates/rooted.tpl"));
        Set<String> full = XoopsUnregisteredTemplateInspection.registeredTemplates(
                "<?php $modversion['templates'][] = ['file' => 'templates/blocks/deep_block.tpl'];");
        assertTrue(full.contains("blocks/deep_block.tpl"));
        assertTrue(full.contains("deep_block.tpl"));
    }
}
