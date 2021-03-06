package org.prebid.server.analytics.model;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.ext.web.RoutingContext;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.cookie.UidsCookie;

import java.util.List;

/**
 * Represents a transaction at /openrtb2/auction endpoint.
 */
@Builder
@Value
public class AuctionEvent {

    Integer status;

    List<String> errors;

    RoutingContext context;

    UidsCookie uidsCookie;

    BidRequest bidRequest;

    BidResponse bidResponse;
}
