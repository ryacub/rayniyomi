package tachiyomi.domain.library.model.search

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class LibrarySearchParserTest {

    @Test
    fun `AND binds tighter than OR`() {
        QueryNode.from("a || b && c") shouldBe
            OrNode(
                listOf(
                    GeneralQueryNode("a", false),
                    AndNode(
                        listOf(
                            GeneralQueryNode("b", false),
                            GeneralQueryNode("c", false),
                        ),
                    ),
                ),
            )
        QueryNode.from("a && b || c") shouldBe
            OrNode(
                listOf(
                    AndNode(
                        listOf(
                            GeneralQueryNode("a", false),
                            GeneralQueryNode("b", false),
                        ),
                    ),
                    GeneralQueryNode("c", false),
                ),
            )
    }

    @Test
    fun `parentheses group expressions`() {
        QueryNode.from("(a || b) && c") shouldBe
            AndNode(
                listOf(
                    OrNode(listOf(GeneralQueryNode("a", false), GeneralQueryNode("b", false))),
                    GeneralQueryNode("c", false),
                ),
            )
        QueryNode.from("((a))") shouldBe GeneralQueryNode("a", false)
    }

    @Test
    fun `leading minus negates a term`() {
        // Upstream applies the minus as a negated flag on the leaf node, not as a NotNode;
        // NotNode wraps only parenthesized groups.
        QueryNode.from("-a") shouldBe GeneralQueryNode("a", true)
        QueryNode.from("--a") shouldBe GeneralQueryNode("a", false)
        QueryNode.from("-genre:comedy") shouldBe FieldQueryNode(LibrarySearchField.GENRE, "comedy", true)
        QueryNode.from("-(a || b)") shouldBe NotNode(
            OrNode(listOf(GeneralQueryNode("a", false), GeneralQueryNode("b", false))),
        )
    }

    @Test
    fun `a negated empty group stays matches-all`() {
        // A NotNode over an empty group would match nothing and blank the library while the
        // user is still typing "-(".
        QueryNode.from("-(") shouldBe EmptyQueryNode
        QueryNode.from("-()") shouldBe EmptyQueryNode
        QueryNode.from("title:a && -(") shouldBe FieldQueryNode(LibrarySearchField.TITLE, "a", false)
        QueryNode.from("title:a && -()") shouldBe FieldQueryNode(LibrarySearchField.TITLE, "a", false)
    }

    @Test
    fun `known field prefixes become field nodes`() {
        QueryNode.from("genre:action") shouldBe FieldQueryNode(LibrarySearchField.GENRE, "action", false)
        QueryNode.from("tag:action") shouldBe FieldQueryNode(LibrarySearchField.GENRE, "action", false)
        QueryNode.from("desc:foo") shouldBe FieldQueryNode(LibrarySearchField.DESCRIPTION, "foo", false)
        QueryNode.from("title:\"one piece\"") shouldBe FieldQueryNode(LibrarySearchField.TITLE, "one piece", false)
    }

    @Test
    fun `unknown field prefix stays a general term`() {
        QueryNode.from("foo:bar") shouldBe GeneralQueryNode("foo:bar", false)
    }

    @Test
    fun `known comparison fields become comparison nodes`() {
        QueryNode.from("unread>5") shouldBe
            ComparisonQueryNode(ComparisonField.UNREAD, "5", ComparisonOperator.GT, false)
        QueryNode.from("added>=2024-01-01") shouldBe
            ComparisonQueryNode(ComparisonField.DATE_ADDED, "2024-01-01", ComparisonOperator.GTE, false)
        QueryNode.from("fi=7") shouldBe
            ComparisonQueryNode(ComparisonField.FETCH_INTERVAL, "7", ComparisonOperator.EQ, false)
        QueryNode.from("nu<2027-01-01") shouldBe
            ComparisonQueryNode(ComparisonField.NEXT_UPDATE, "2027-01-01", ComparisonOperator.LT, false)
        QueryNode.from("id=42") shouldBe
            ComparisonQueryNode(ComparisonField.ID, "42", ComparisonOperator.EQ, false)
        QueryNode.from("total>=100") shouldBe
            ComparisonQueryNode(ComparisonField.TOTAL, "100", ComparisonOperator.GTE, false)
        QueryNode.from("read<=3") shouldBe
            ComparisonQueryNode(ComparisonField.READ, "3", ComparisonOperator.LTE, false)
    }

    @Test
    fun `undeclared comparison aliases stay general terms`() {
        QueryNode.from("date_added>=2024-01-01") shouldBe
            GeneralQueryNode("date_added>=2024-01-01", false)
        QueryNode.from("fetch_interval=7") shouldBe
            GeneralQueryNode("fetch_interval=7", false)
        QueryNode.from("next_update<2027-01-01") shouldBe
            GeneralQueryNode("next_update<2027-01-01", false)
    }

    @Test
    fun `leading minus negates a comparison`() {
        QueryNode.from("-unread>5") shouldBe
            ComparisonQueryNode(ComparisonField.UNREAD, "5", ComparisonOperator.GT, true)
    }

    @Test
    fun `unknown comparison field stays a general term`() {
        QueryNode.from("foo>5") shouldBe GeneralQueryNode("foo>5", false)
    }

    @Test
    fun `comparison nodes join compound queries`() {
        QueryNode.from("unread>=1 || id=5") shouldBe
            OrNode(
                listOf(
                    ComparisonQueryNode(ComparisonField.UNREAD, "1", ComparisonOperator.GTE, false),
                    ComparisonQueryNode(ComparisonField.ID, "5", ComparisonOperator.EQ, false),
                ),
            )
        QueryNode.from("(unread>5 || id=5) && genre:action") shouldBe
            AndNode(
                listOf(
                    OrNode(
                        listOf(
                            ComparisonQueryNode(ComparisonField.UNREAD, "5", ComparisonOperator.GT, false),
                            ComparisonQueryNode(ComparisonField.ID, "5", ComparisonOperator.EQ, false),
                        ),
                    ),
                    FieldQueryNode(LibrarySearchField.GENRE, "action", false),
                ),
            )
    }

    @Test
    fun `quoted general terms keep their value`() {
        QueryNode.from("\"one piece\"") shouldBe GeneralQueryNode("one piece", false)
    }

    @Test
    fun `consecutive general terms join with implicit AND`() {
        QueryNode.from("a b") shouldBe AndNode(listOf(GeneralQueryNode("a", false), GeneralQueryNode("b", false)))
    }

    @Test
    fun `chained operators flatten into one node`() {
        QueryNode.from("a && b && c") shouldBe AndNode(
            listOf(
                GeneralQueryNode("a", false),
                GeneralQueryNode("b", false),
                GeneralQueryNode("c", false),
            ),
        )
        QueryNode.from("a || b || c") shouldBe OrNode(
            listOf(
                GeneralQueryNode("a", false),
                GeneralQueryNode("b", false),
                GeneralQueryNode("c", false),
            ),
        )
    }

    @Test
    fun `empty input produces an empty AND node`() {
        QueryNode.from("") shouldBe AndNode(emptyList())
        QueryNode.from("   ") shouldBe AndNode(emptyList())
    }

    @Test
    fun `malformed expressions never throw and always produce a node`() {
        assertDoesNotThrow {
            QueryNode.from("((((") shouldBe EmptyQueryNode
            QueryNode.from(")") shouldBe EmptyQueryNode
            QueryNode.from("&&") shouldBe EmptyQueryNode
            QueryNode.from("a &&") shouldBe GeneralQueryNode("a", false)
            QueryNode.from("\"unclosed") shouldBe GeneralQueryNode("\"unclosed", false)
            QueryNode.from("genre:") shouldBe GeneralQueryNode("genre:", false)
        }
    }

    @Test
    fun `dangling operators produce nodes that include empty node children`() {
        assertDoesNotThrow {
            QueryNode.from("a ||") shouldBe
                OrNode(listOf(GeneralQueryNode("a", false), EmptyQueryNode))
            QueryNode.from("(a ||") shouldBe
                OrNode(listOf(GeneralQueryNode("a", false), EmptyQueryNode))
            QueryNode.from("||") shouldBe
                OrNode(listOf(EmptyQueryNode, EmptyQueryNode))
        }
    }
}
