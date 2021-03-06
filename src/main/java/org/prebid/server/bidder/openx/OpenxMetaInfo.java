package org.prebid.server.bidder.openx;

import org.prebid.server.bidder.MetaInfo;
import org.prebid.server.proto.response.BidderInfo;

import java.util.Arrays;
import java.util.Collections;

public class OpenxMetaInfo implements MetaInfo {

    private BidderInfo bidderInfo;

    public OpenxMetaInfo(boolean enabled, boolean pbsEnforcesGdpr) {
        bidderInfo = BidderInfo.create(enabled, "team-openx@openx.com",
                Collections.singletonList("banner"), Arrays.asList("banner", "video"),
                null, 69, pbsEnforcesGdpr);
    }

    @Override
    public BidderInfo info() {
        return bidderInfo;
    }
}
