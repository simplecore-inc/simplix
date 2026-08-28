package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.web.meta.model.AccessMeta;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessExpressionParserTest {

    @Test
    void parsesHasPermission() {
        AccessMeta meta = AccessExpressionParser.parse("hasPermission('SYSTEM', 'edit')");
        assertThat(meta.kind()).isEqualTo("permission");
        assertThat(meta.group()).isEqualTo("SYSTEM");
        assertThat(meta.action()).isEqualTo("edit");
    }

    @Test
    void parsesHasPermissionWithoutSpace() {
        AccessMeta meta = AccessExpressionParser.parse("hasPermission('SAFETY_SITE','view')");
        assertThat(meta.group()).isEqualTo("SAFETY_SITE");
        assertThat(meta.action()).isEqualTo("view");
    }

    @Test
    void parsesIsAuthenticated() {
        assertThat(AccessExpressionParser.parse("isAuthenticated()").kind()).isEqualTo("authenticated");
    }

    @Test
    void parsesPermitAll() {
        assertThat(AccessExpressionParser.parse("permitAll()").kind()).isEqualTo("public");
    }

    @Test
    void unknownSpelBecomesRawExpression() {
        AccessMeta meta = AccessExpressionParser.parse("hasPermission('A','edit') and #id != null");
        assertThat(meta.kind()).isEqualTo("expression");
        assertThat(meta.raw()).isEqualTo("hasPermission('A','edit') and #id != null");
    }

    @Test
    void absentAnnotationMeansAuthenticated() {
        // No @PreAuthorize under an authenticated-by-default filter chain: authenticated-only.
        assertThat(AccessExpressionParser.parse(null).kind()).isEqualTo("authenticated");
        assertThat(AccessExpressionParser.parse("  ").kind()).isEqualTo("authenticated");
    }
}
