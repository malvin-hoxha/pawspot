package com.pawspot.auth.refreshtoken;

import com.pawspot.auth.response.RefreshResult;

public record RotationOutcome(RefreshResult result, boolean reuseDetected) {
    
}
