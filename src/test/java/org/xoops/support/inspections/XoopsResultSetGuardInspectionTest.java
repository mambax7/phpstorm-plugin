package org.xoops.support.inspections;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class XoopsResultSetGuardInspectionTest {

    @Test
    public void unguardedFetchIsReported() {
        String php = """
                <?php
                $result = $xoopsDB->query($sql);
                while (list($sumIn, $sumOut) = $xoopsDB->fetchRow($result)) {
                    $amount += $sumIn;
                }
                """;
        List<Integer> hits = XoopsResultSetGuardInspection.unguardedFetchOffsets(php);
        assertEquals(1, hits.size());
    }

    @Test
    public void whileConditionFetchIsCoveredByPrecedingEarlyExit() {
        String php = """
                <?php
                $result = $xoopsDB->query($sql);
                if (!$xoopsDB->isResultSet($result) || !$result instanceof \\mysqli_result) {
                    throw new \\RuntimeException('Database query failed');
                }
                while (list($sumIn, $sumOut) = $xoopsDB->fetchRow($result)) {
                    $amount += $sumIn;
                }
                """;
        assertTrue(XoopsResultSetGuardInspection.unguardedFetchOffsets(php).isEmpty());
    }

    @Test
    public void reassignmentClearsTheGuard() {
        String php = """
                <?php
                $result = $xoopsDB->query($sql);
                if (!$xoopsDB->isResultSet($result) || !$result instanceof \\mysqli_result) {
                    throw new \\RuntimeException('Database query failed');
                }
                $result = $xoopsDB->query($sql2);
                $row = $xoopsDB->fetchArray($result);
                """;
        assertEquals(1, XoopsResultSetGuardInspection.unguardedFetchOffsets(php).size());
    }

    @Test
    public void positiveIfBodyIsGuarded() {
        String php = """
                <?php
                $result = $db->query($sql);
                if ($db->isResultSet($result)) {
                    $row = $db->fetchArray($result);
                }
                """;
        assertTrue(XoopsResultSetGuardInspection.unguardedFetchOffsets(php).isEmpty());
    }

    @Test
    public void orFallbackIsNotASafePositiveGuard() {
        String php = """
                <?php
                $result = $db->query($sql);
                if ($db->isResultSet($result) || $fallback) {
                    $row = $db->fetchArray($result);
                }
                """;
        assertEquals(1, XoopsResultSetGuardInspection.unguardedFetchOffsets(php).size());
    }

    @Test
    public void alreadyGuardedAboveDetectsInsertedBlock() {
        String preceding = """
                    if (!$xoopsDB->isResultSet($result) || !$result instanceof \\mysqli_result) {
                        throw new \\RuntimeException('Database query failed');
                    }
                """;
        assertTrue(InsertBeforeStatementQuickFix.alreadyGuardedAbove(preceding, preceding.length(), "$result"));
        assertFalse(InsertBeforeStatementQuickFix.alreadyGuardedAbove("while (true) {\n", 12, "$result"));
    }
}
