package com.moadrive.pleos.care.news

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChildNewsPreviewTest {
    private val article = "https://www.moe.go.kr/boardCnts/viewRenew.do?boardID=294&boardSeq=107612&lev=0&s=moe"
    private val id = "053250706cd42af42a7a5758414b88fb"
    private fun anchor(value: String = id) =
        """<a class="btnBorder" href="javascript:fncFilePreView('$value')" title="미리보기 새 창 열림" data-icon="preview">미리보기</a>"""

    @Test fun resolvesObservedOfficialPreviewWithoutExecutingJavascript() {
        assertEquals("https://www.moe.go.kr/boardCnts/filePreView.do?seq=$id",
            ChildNewsPreviewParser.previewUrl(article, anchor()))
    }

    @Test fun permitsQuotedArgumentsAndWhitespaceButKeepsTheExactFileIdentity() {
        val html = """<a href=' javascript: fncFilePreView ( "$id" ) ; '>미리보기</a>"""
        assertEquals("https://www.moe.go.kr/boardCnts/filePreView.do?seq=$id",
            ChildNewsPreviewParser.previewUrl(article, html))
    }

    @Test fun usesTheFirstActualPreviewAndIgnoresCommentsAndScripts() {
        val later = "f".repeat(32)
        val fake = "a".repeat(32)
        val html = "<!-- ${anchor(fake)} --><script>var fake = `${anchor(fake)}`;</script>" +
            "<style>/* ${anchor(fake)} */</style>" + anchor() + anchor(later)
        assertEquals("https://www.moe.go.kr/boardCnts/filePreView.do?seq=$id",
            ChildNewsPreviewParser.previewUrl(article, html))
    }

    @Test fun missingInvalidAndInjectedPreviewCallsDoNotBecomeDestinations() {
        listOf("<html>첨부파일 없음</html>", "<script>${anchor()}</script>", "<!-- ${anchor()} -->",
            anchor("123"), anchor("g".repeat(32)), anchor("../$id"),
            anchor().replace(")\"", ");alert(1)\""),
            anchor().replace("javascript:fncFilePreView", "javascript:otherFunction"),
            """<div href="javascript:fncFilePreView('$id')">미리보기</div>""")
            .forEach { assertNull(it, ChildNewsPreviewParser.previewUrl(article, it)) }
    }

    @Test fun acceptsOnlyOfficialEducationArticleUrls() {
        assertTrue(ChildNewsPreviewParser.isOfficialArticleUrl(article))
        val rejected = listOf(
            article.replace("https:", "http:"),
            article.replace("www.moe.go.kr", "www.moe.go.kr.evil.example"),
            article.replace("www.moe.go.kr", "user@www.moe.go.kr"),
            article.replace("www.moe.go.kr", "www.moe.go.kr:8443"),
            article.replace("viewRenew.do", "listRenew.do"),
            article.replace("boardID=294", "boardID=999"),
            article.replace("boardSeq=107612", "boardSeq=invalid"),
            "$article&boardSeq=2", "$article&boardID=294", " $article", "$article\n",
        )
        rejected.forEach {
            assertFalse(it, ChildNewsPreviewParser.isOfficialArticleUrl(it))
            assertNull(it, ChildNewsPreviewParser.previewUrl(it, anchor()))
        }
    }

    @Test fun oversizedSourceIsIgnored() {
        assertNull(ChildNewsPreviewParser.previewUrl(article, "x".repeat(2_000_001) + anchor()))
    }

    @Test fun unsupportedArticleFallsBackWithoutMakingARequest() = runBlocking {
        val pet = "https://www.mafra.go.kr/bbs/home/792/582084/artclView.do"
        assertEquals(pet, resolveChildNewsPreviewUrl(pet))
    }
}
