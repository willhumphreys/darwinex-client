package uk.co.threebugs.darwinexclient.tradingstance

import uk.co.threebugs.darwinexclient.accountsetupgroups.*
import uk.co.threebugs.darwinexclient.setupgroup.*

data class TradingStanceDto(
    val id: Int? = null,
    val symbol: String,
    val direction: Direction,
    val accountSetupGroups: AccountSetupGroupsDto
)