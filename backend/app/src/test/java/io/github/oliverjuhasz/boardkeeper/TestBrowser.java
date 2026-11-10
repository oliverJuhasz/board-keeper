package io.github.oliverjuhasz.boardkeeper;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Calls the API the way the frontend in a browser does: it keeps the cookies it is given and
 * sends them back, and it sends the CSRF token from the XSRF-TOKEN cookie as a header.
 * Each instance is a separate browser, so two of them are two people.
 */
public class TestBrowser {

    private final MockMvcTester mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private boolean sendCsrfToken = true;

    public TestBrowser(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    /** A browser that has loaded the app: the frontend's first call is always to /api/session. */
    public static TestBrowser open(MockMvcTester mvc) {
        TestBrowser browser = new TestBrowser(mvc);
        browser.get("/api/session");
        return browser;
    }

    /** Stops sending the CSRF token, as a request forged by another website would. */
    public TestBrowser withoutCsrfToken() {
        sendCsrfToken = false;
        return this;
    }

    public MvcTestResult get(String uri) {
        return exchange(HttpMethod.GET, uri, null);
    }

    public MvcTestResult post(String uri, String json) {
        return exchange(HttpMethod.POST, uri, json);
    }

    public MvcTestResult put(String uri, String json) {
        return exchange(HttpMethod.PUT, uri, json);
    }

    public MvcTestResult delete(String uri) {
        return exchange(HttpMethod.DELETE, uri, null);
    }

    public Cookie cookie(String name) {
        return cookies.get(name);
    }

    private MvcTestResult exchange(HttpMethod method, String uri, String json) {
        var request = mvc.method(method).uri(uri);
        if (!cookies.isEmpty()) {
            request = request.cookie(cookies.values().toArray(Cookie[]::new));
        }
        Cookie csrf = cookies.get("XSRF-TOKEN");
        if (sendCsrfToken && csrf != null) {
            request = request.header("X-XSRF-TOKEN", csrf.getValue());
        }
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        MvcTestResult result = request.exchange();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
        return result;
    }
}
