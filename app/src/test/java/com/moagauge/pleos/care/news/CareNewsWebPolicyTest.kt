package com.moagauge.pleos.care.news

import org.junit.Assert.*
import org.junit.Test

class CareNewsWebPolicyTest {
    @Test fun allowsOriginalArticlesAndSecurePublisherNavigation() {
        listOf(
            "https://www.moe.go.kr/boardCnts/viewRenew.do?boardID=294&boardSeq=100#content",
            "https://www.mafra.go.kr/bbs/home/792/100/artclView.do",
            "HTTPS://www.moe.go.kr/",
            "https://publisher.example:8443/article?return=https%3A%2F%2Fwww.moe.go.kr",
        ).forEach { assertTrue(it, CareNewsWebPolicy.isAllowedWebUrl(it)) }
    }

    @Test fun blocksExecutableLocalInsecureAndExternalAppNavigation() {
        listOf(
            "http://www.moe.go.kr/", "javascript:alert(1)", "file:///sdcard/article.html",
            "content://media/1", "data:text/html,test", "about:blank", "tel:110",
            "intent://article#Intent;scheme=app;S.browser_fallback_url=https%3A%2F%2Fwww.moe.go.kr;end",
        ).forEach { assertFalse(it, CareNewsWebPolicy.isAllowedWebUrl(it)) }
    }

    @Test fun blocksMalformedAndMisleadingSecureAddresses() {
        listOf(
            "", "https:", "https:///article", "https://user:password@www.moe.go.kr/",
            "https://www.moe.go.kr\\@other.example/", "https://www.moe.go.kr\n/article",
            "https://www.moe.go.kr:0/", "https://www.moe.go.kr:65536/", " https://www.moe.go.kr/",
        ).forEach { assertFalse(it, CareNewsWebPolicy.isAllowedWebUrl(it)) }
    }

    @Test fun displaysActualHostWithoutArticleParameters() {
        assertEquals("www.moe.go.kr", CareNewsWebPolicy.displayHost(
            "https://WWW.MOE.GO.KR/article?token=private&return=other.example#private"))
        assertNull(CareNewsWebPolicy.displayHost("https://www.moe.go.kr@other.example/"))
    }
}
