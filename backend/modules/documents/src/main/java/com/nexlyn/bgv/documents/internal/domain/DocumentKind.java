package com.nexlyn.bgv.documents.internal.domain;

/** What a stored file is for. */
public enum DocumentKind {
    /** The candidate photo (one per case; a new upload replaces it). */
    PHOTO,
    /** A supporting document of a check (JPEG, PNG or PDF). */
    CHECK_DOC,
    /** A picture used in a free image block on a check (JPEG or PNG). */
    FREE_IMAGE
}
