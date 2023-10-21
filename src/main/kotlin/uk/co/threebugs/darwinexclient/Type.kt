package uk.co.threebugs.darwinexclient

enum class Type {
    PENDING,
    ORDER_SENT,
    PLACED_IN_MT,
    FILLED,
    OUT_OF_TIME,
    CLOSED_BY_STOP,
    CLOSED_BY_LIMIT,
    CLOSED_BY_TIME,
    CLOSED_BY_USER
}
