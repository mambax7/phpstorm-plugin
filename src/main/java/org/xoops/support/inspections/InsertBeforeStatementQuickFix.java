package org.xoops.support.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.GroupStatement;
import com.jetbrains.php.lang.psi.elements.Statement;

import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Inserts an isResultSet throw-guard immediately before the enclosing statement of
 * the problem element. Offset and indent are computed at apply-time so a batch
 * Inspect Code run still works after an earlier fix in the same file shifted lines.
 */
public final class InsertBeforeStatementQuickFix implements LocalQuickFix {

    private static final String FAIL_ACTION = "throw new \\RuntimeException('Database query failed');";

    private final String dbExpr;
    private final String resultVar;

    public InsertBeforeStatementQuickFix(@NotNull String dbExpr, @NotNull String resultVar) {
        this.dbExpr = dbExpr;
        this.resultVar = resultVar;
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Insert isResultSet guard before fetch";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement leaf = descriptor.getPsiElement();
        if (leaf == null) {
            return;
        }
        Statement stmt = PsiTreeUtil.getParentOfType(leaf, Statement.class, false);
        if (stmt == null) {
            return;
        }
        stmt = outermostUnbracedAncestor(stmt);
        Document document = DocumentEditHelper.documentOf(project, leaf);
        if (document == null) {
            return;
        }
        int insertAt = stmt.getTextRange().getStartOffset();
        if (insertAt < 0 || insertAt > document.getTextLength()) {
            return;
        }
        String text = document.getText();
        int fetchOffset = leaf.getTextRange().getStartOffset();
        if (XoopsResultSetGuardInspection.isFetchGuardedAt(text, fetchOffset, resultVar)) {
            return;
        }
        if (assignsResultBeforeFetch(text, insertAt, Math.max(insertAt, fetchOffset), resultVar)) {
            // e.g. while (($result = $db->query($sql)) && $db->fetchRow($result)):
            // a guard above the statement would test a stale value. Leave it to the user.
            return;
        }
        String indent = guessIndent(text, insertAt);
        String block = indent + "if (!" + dbExpr + "->isResultSet(" + resultVar
                + ") || !" + resultVar + " instanceof \\mysqli_result) {\n"
                + indent + "    " + FAIL_ACTION + "\n"
                + indent + "}\n";
        document.insertString(insertAt, block);
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    /**
     * When the statement is the sole, unbraced body of an if / while / for / foreach,
     * inserting before it would put the guard inside that control structure and push
     * the fetch out of it. Climb to the control statement instead (an early-exit guard
     * before the loop is equivalent) and stop at the first enclosing {@code { }} block.
     */
    private static @NotNull Statement outermostUnbracedAncestor(@NotNull Statement stmt) {
        Statement current = stmt;
        while (true) {
            Statement up = PsiTreeUtil.getParentOfType(current, Statement.class, true);
            GroupStatement block = PsiTreeUtil.getParentOfType(current, GroupStatement.class, true);
            if (up == null || (block != null && PsiTreeUtil.isAncestor(up, block, true))) {
                return current;
            }
            current = up;
        }
    }

    static boolean assignsResultBeforeFetch(
            @NotNull String text, int from, int to, @NotNull String resultVar
    ) {
        String masked = PhpTextUtil.maskCommentsAndStrings(text).substring(from, to);
        return Pattern.compile(Pattern.quote(resultVar) + "(?![\\w])\\s*=(?![=>])")
                .matcher(masked).find();
    }

    private static String guessIndent(@NotNull String text, int offset) {
        int start = text.lastIndexOf('\n', Math.max(0, offset - 1));
        start = start < 0 ? 0 : start + 1;
        int i = start;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return text.substring(start, i);
    }
}
