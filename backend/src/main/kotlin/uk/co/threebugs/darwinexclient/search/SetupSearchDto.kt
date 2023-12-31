package uk.co.threebugs.darwinexclient.search

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.ZonedDateTime

data class SetupSearchDto(
    var id: Int? = null,
    var createdDateTime: ZonedDateTime? = null,
    var setupGroup: SetupGroupSearchDto? = null,
    var symbol: String? = null,
    var rank: Int? = null,
    var dayOfWeek: Int? = null,
    var hourOfDay: Int? = null,
    var stop: Int? = null,
    var limit: Int? = null,
    var tickOffset: Int? = null,
    var tradeDuration: Int? = null,
    var outOfTime: Int? = null
) {
    @JsonIgnore
    fun isLong(): Boolean = stop!! < limit!!
}
