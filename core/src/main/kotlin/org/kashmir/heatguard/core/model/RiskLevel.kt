package org.kashmir.heatguard.core.model

enum class RiskLevel(val rank: Int) {
    SAFE(0),
    CHECK(1),
    ACTION(2),
    EMERGENCY(3);

    companion object {
        fun maxOf(vararg levels: RiskLevel): RiskLevel = levels.maxBy { it.rank }
    }
}

enum class Confidence {
    LOW,
    MEDIUM,
    HIGH
}

enum class OfficialHeatWarning {
    NONE,
    WATCH,
    WARNING,
    EXTREME
}

enum class AgeBand {
    CHILD,
    ADULT,
    OLDER_ADULT,
    UNKNOWN
}

enum class ResponseState {
    NOT_ASKED,
    RESPONDED,
    NO_RESPONSE,
    MANUAL_EMERGENCY
}
