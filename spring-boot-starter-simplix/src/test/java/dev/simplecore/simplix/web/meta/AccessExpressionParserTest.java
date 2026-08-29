package dev.simplecore.simplix.web.meta;

import dev.simplecore.simplix.web.meta.model.AccessMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    // Pins the parser's safety property against real unrecognised shapes found in the target
    // application (hasAuthority/hasRole calls and compound and/or expressions): each of these
    // MUST resolve to "expression", never "public", or a frontend button would render for an
    // access rule the server actually refuses. The first three deliberately contain "permitAll"
    // as a substring so a careless switch from equality to substring matching goes red here too.
    @ParameterizedTest
    @ValueSource(strings = {
            "permitAll",
            "permitAll() or hasPermission('A','b')",
            "hasPermission('A','b') or permitAll()",
            "isAuthenticated() and hasRole('ADMIN')",
            "hasAuthority('SCOPE_read')",
            "hasPermission('A','b','c')",
            "hasPermission(\"A\",\"b\")",
            "hasPermission('','')",
            "@customBean.check(#id)"
    })
    void unrecognisedShapesNeverBecomePublic(String expression) {
        AccessMeta meta = AccessExpressionParser.parse(expression);
        assertThat(meta.kind()).isEqualTo("expression");
        assertThat(meta.kind()).isNotEqualTo("public");
        assertThat(meta.raw()).isEqualTo(expression.trim());
    }
}
