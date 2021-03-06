package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpPrebid {

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedrequest
     */
    ExtStoredRequest storedrequest;
}
