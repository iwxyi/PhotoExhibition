package com.photoexhibition.aisearch.resolver;

import com.photoexhibition.aisearch.reducer.AiSearchEvidenceBundle;

public interface AiSearchResolver {
    String resolve(AiSearchEvidenceBundle evidenceBundle);
}
