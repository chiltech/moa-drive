package com.moadrive.pleos.insurance

import org.junit.Assert.*
import org.junit.Test

class InsuranceWebPolicyTest {
    @Test fun allowsHttpsInsurerAndAuthenticationPages() {
        listOf(
            "https://direct.samsungfire.com/ria/fw/index.html?state=Front&product=endo&endotypecd=E10",
            "https://identity.example.com:8443/verify?return=https%3A%2F%2Fdirect.samsungfire.com",
            "HTTPS://direct.samsungfire.com/",
        ).forEach { assertTrue(it, InsuranceWebPolicy.isAllowedWebUrl(it)) }
    }

    @Test fun rejectsExecutableLocalAndUnencryptedNavigation() {
        listOf("http://direct.samsungfire.com/", "javascript:alert(1)",
            "file:///sdcard/private.html", "content://contacts/1", "data:text/html,test",
            "intent://verify#Intent;scheme=auth;end", "tel:15885114", "about:blank")
            .forEach { assertFalse(it, InsuranceWebPolicy.isAllowedWebUrl(it)) }
    }

    @Test fun rejectsMalformedAndMisleadingAuthorities() {
        listOf("", "https:", "https:///no-host", "https://user:password@example.com/",
            "https://example.com\\@other.example/", "https://example.com\n/path",
            "https://example.com:0/", "https://example.com:65536/")
            .forEach { assertFalse(it, InsuranceWebPolicy.isAllowedWebUrl(it)) }
    }

    @Test fun headerShowsOnlyActualHostWithoutQueryOrFragment() {
        assertEquals("identity.example.com", InsuranceWebPolicy.displayHost(
            "https://identity.example.com/verify?token=private&return=direct.samsungfire.com#private"))
        assertNull(InsuranceWebPolicy.displayHost("https://user@example.com/"))
    }
}
