package com.algolearner.platform.auth;

/** Explicit namespace for legacy identifiers; omitted values use AUTO. */
@io.swagger.v3.oas.annotations.media.Schema(description = "Identity namespace. Omitted or AUTO checks both email and username; different matching accounts return 409 AMBIGUOUS_IDENTITY. Email-specific fields always use EMAIL.")
public enum IdentityType { AUTO, EMAIL, USERNAME }
