package org.prebid.server.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Contains logic for obtaining UIDs from the request and actualizing them.
 */
public class UidsCookieService {

    private static final Logger logger = LoggerFactory.getLogger(UidsCookieService.class);

    private static final String COOKIE_NAME = "uids";

    private final String optOutCookieName;
    private final String optOutCookieValue;
    private final String hostCookieFamily;
    private final String hostCookieName;
    private final String hostCookieDomain;
    private final Long ttlSeconds;

    public UidsCookieService(String optOutCookieName, String optOutCookieValue, String hostCookieFamily,
                             String hostCookieName, String hostCookieDomain, Integer ttlDays) {
        this.optOutCookieName = optOutCookieName;
        this.optOutCookieValue = optOutCookieValue;
        this.hostCookieFamily = hostCookieFamily;
        this.hostCookieName = hostCookieName;
        this.hostCookieDomain = hostCookieDomain;
        this.ttlSeconds = Duration.ofDays(ttlDays).getSeconds();
    }

    /**
     * Retrieves UIDs cookie (base64 encoded) value from http request and transforms it into {@link UidsCookie}.
     * <p>
     * Uids cookie value from http request may be represented in accordance with one of two formats:
     * <ul>
     * <li>Legacy cookies - had UIDs without expiration dates</li>
     * <li>Current cookies - always include UIDs with expiration dates</li>
     * </ul>
     * If request contains 'legacy' UIDs cookie format then it will be interpreted as already expired and forced
     * to re-sync
     * <p>
     * This method also sets 'hostCookieFamily' if 'hostCookie' is present in the request and feature is not opted-out.
     * If feature is opted-out uids attribute will be blank.
     * <p>
     * Note: UIDs will be excluded from resulting {@link UidsCookie} if their value are 'null'
     */
    public UidsCookie parseFromRequest(RoutingContext context) {
        final Uids parsedUids = parseUids(context);

        final Uids.UidsBuilder uidsBuilder = Uids.builder()
                .uidsLegacy(Collections.emptyMap())
                .bday(parsedUids != null ? parsedUids.getBday() : ZonedDateTime.now(Clock.systemUTC()));

        final Boolean optout;
        final Map<String, UidWithExpiry> uidsMap;

        if (isOptedOut(context)) {
            optout = true;
            uidsMap = Collections.emptyMap();
        } else {
            optout = parsedUids != null ? parsedUids.getOptout() : null;
            uidsMap = enrichAndSanitizeUids(parsedUids, context);
        }

        return new UidsCookie(uidsBuilder.uids(uidsMap).optout(optout).build());
    }

    /**
     * Parses {@link RoutingContext} and composes {@link Uids} model from {@link Cookie}.
     */
    public Uids parseUids(RoutingContext context) {
        final Cookie uidsCookie = context.getCookie(COOKIE_NAME);
        if (uidsCookie != null) {
            final String cookieValue = uidsCookie.getValue();
            try {
                return Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(cookieValue)), Uids.class);
            } catch (IllegalArgumentException | DecodeException e) {
                logger.debug("Could not decode or parse {0} cookie value {1}", e, COOKIE_NAME, cookieValue);
            }
        }
        return null;
    }

    /**
     * Creates a {@link Cookie} with 'uids' as a name and encoded JSON string representing supplied {@link UidsCookie}
     * as a value.
     */
    public Cookie toCookie(UidsCookie uidsCookie) {
        final Cookie cookie = Cookie
                .cookie(COOKIE_NAME, Base64.getUrlEncoder().encodeToString(uidsCookie.toJson().getBytes()))
                .setPath("/")
                .setMaxAge(ttlSeconds);

        if (StringUtils.isNotBlank(hostCookieDomain)) {
            cookie.setDomain(hostCookieDomain);
        }

        return cookie;
    }

    /**
     * Lookup host cookie value from request by configured host cookie name.
     */
    public String parseHostCookie(RoutingContext context) {
        final Cookie cookie = hostCookieName != null ? context.getCookie(hostCookieName) : null;
        return cookie != null ? cookie.getValue() : null;
    }

    /**
     * Returns configured host cookie family.
     */
    public String getHostCookieFamily() {
        return hostCookieFamily;
    }

    /**
     * Checks incoming request if it matches pre-configured opted-out cookie name, value and de-activates
     * UIDs cookie sync.
     */
    private boolean isOptedOut(RoutingContext context) {
        if (StringUtils.isNotBlank(optOutCookieName) && StringUtils.isNotBlank(optOutCookieValue)) {
            final Cookie cookie = context.getCookie(optOutCookieName);
            return cookie != null && Objects.equals(cookie.getValue(), optOutCookieValue);
        }
        return false;
    }

    /**
     * Enriches {@link Uids} parsed from request cookies with uid from host cookie (if applicable) and removes
     * invalid uids. Also converts legacy uids to uids with expiration.
     */
    private Map<String, UidWithExpiry> enrichAndSanitizeUids(Uids uids, RoutingContext context) {
        final Map<String, UidWithExpiry> originalUidsMap = uids != null ? uids.getUids() : null;
        final Map<String, UidWithExpiry> workingUidsMap = new HashMap<>(
                ObjectUtils.defaultIfNull(originalUidsMap, Collections.emptyMap()));

        final Map<String, String> legacyUids = uids != null ? uids.getUidsLegacy() : null;
        if (workingUidsMap.isEmpty() && legacyUids != null) {
            legacyUids.forEach((key, value) -> workingUidsMap.put(key, UidWithExpiry.expired(value)));
        }

        final String hostCookie = parseHostCookie(context);
        if (hostCookie != null && hostCookieDiffers(hostCookie, workingUidsMap.get(hostCookieFamily))) {
            // make host cookie precedence over uids
            workingUidsMap.put(hostCookieFamily, UidWithExpiry.live(hostCookie));
        }

        workingUidsMap.entrySet().removeIf(UidsCookieService::facebookSentinelOrEmpty);

        return workingUidsMap;
    }

    /**
     * Returns true if host cookie value differs from the given UID value.
     */
    private static boolean hostCookieDiffers(String hostCookie, UidWithExpiry uid) {
        return uid == null || !Objects.equals(hostCookie, uid.getUid());
    }

    private static boolean facebookSentinelOrEmpty(Map.Entry<String, UidWithExpiry> entry) {
        return UidsCookie.isFacebookSentinel(entry.getKey(), entry.getValue().getUid())
                || StringUtils.isEmpty(entry.getValue().getUid());
    }
}
