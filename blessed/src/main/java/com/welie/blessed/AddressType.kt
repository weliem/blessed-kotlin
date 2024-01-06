package com.welie.blessed

enum class AddressType {
    /** Address type is public and registered with the IEEE. */
    PUBLIC,

    /** Address type is random static. */
    RANDOM_STATIC,

    /** Address type is random resolvable. */
    RANDOM_RESOLVABLE,

    /** Address type is random non resolvable. */
    RANDOM_NON_RESOLVABLE,

    /** Address type is unknown. */
    UNKNOWN
}