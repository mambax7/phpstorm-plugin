<?php
/**
 * Sample file for manually verifying XOOPS Grok inspections + Alt+Enter quick fixes.
 * Copy under a path containing /modules/ (e.g. htdocs/modules/_grok_demo/) and re-inspect.
 *
 * See TUTORIAL.md section 4.
 */

// Missing XOOPS_ROOT_PATH guard -> Alt+Enter: Insert guard

$id = $_REQUEST['id'] ?? 0;           // keyed REQUEST -> Xmf\Request quick fix
$name = $_POST['name'] ?? '';         // keyed POST -> Xmf\Request quick fix
$bare = $_GET;                        // warn only (no key)

$result = $db->query("INSERT INTO demo (name) VALUES ('x')"); // query -> exec
$row = $db->fetchArray($result);                              // insert isResultSet guard

$legacy = $db->queryF("SELECT * FROM demo"); // queryF -> query
$q = $db->quoteString($name);                // quoteString -> quote

include XOOPS_ROOT_PATH . '/header.php';     // include -> include_once

