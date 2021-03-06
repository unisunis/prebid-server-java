package org.prebid.server.bidder.brightroll;

import org.junit.Test;
import org.prebid.server.proto.response.UsersyncInfo;

import static org.assertj.core.api.Assertions.assertThat;

public class BrightrollUsersyncerTest {

    @Test
    public void creationShouldInitExpectedUsersyncInfo() {
        assertThat(new BrightrollUsersyncer("//usersync.org/", "http://external.org/").usersyncInfo())
                .isEqualTo(UsersyncInfo.of(
                        "//usersync.org/http%3A%2F%2Fexternal.org%2F%2Fsetuid%3Fbidder%3Dbrightroll"
                                + "%26gdpr%3D{{gdpr}}%26gdpr_consent%3D{{gdpr_consent}}%26uid%3D%24%7BUID%7D",
                        "redirect", false));
    }
}
