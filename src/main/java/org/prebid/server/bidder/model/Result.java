package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines generic result that might bear error alongside.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class Result<T> {

    T value;
    List<BidderError> errors;

}