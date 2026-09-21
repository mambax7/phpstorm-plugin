<?php
/**
 * Manual Inspect Code shapes from Goffy's wgSimpleAcc report (00_docs/goffy.md).
 * Copy under htdocs/modules/_xoops_demo/ (or any /modules/ + /class/ path) and re-inspect.
 */

namespace XoopsModules\_xoops_demo;

use RuntimeException;

defined('XOOPS_ROOT_PATH') || die('Restricted access');

/**
 * Expected:
 * - NO missing ROOT_PATH warning (die after namespace/use is a valid guard)
 * - fetchRow in the while condition is guarded by the early-exit if
 * - a second fetch after $result is reassigned IS flagged
 */
class GoffyShapes
{
    public function balances($xoopsDB): void
    {
        $sql = 'SELECT 1';
        $result = $xoopsDB->query($sql);
        if (!$xoopsDB->isResultSet($result) || !$result instanceof \mysqli_result) {
            throw new RuntimeException('Database query failed');
        }
        while (list($sumIn, $sumOut) = $xoopsDB->fetchRow($result)) {
            $unused = $sumIn + $sumOut;
        }

        $result = $xoopsDB->query('SELECT 2');
        $row = $xoopsDB->fetchArray($result); // should warn — $result was reassigned
    }
}
