package uk.co.threebugs.darwinexclient.trade

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.query.QueryByExampleExecutor
import org.springframework.stereotype.Repository
import uk.co.threebugs.darwinexclient.Status
import uk.co.threebugs.darwinexclient.account.Account
import uk.co.threebugs.darwinexclient.setup.Setup
import java.time.ZonedDateTime

@Repository
interface TradeRepository : JpaRepository<Trade, Int>, QueryByExampleExecutor<Trade> {
    fun findByStatusAndSetup_SymbolAndAccount(status: Status, symbol: String, account: Account): List<Trade>
    fun findBySetupAndTargetPlaceDateTimeAndAccount(
        setup: Setup,
        placedDateTime: ZonedDateTime,
        account: Account
    ): Trade?

    fun findByAccount_Name(name: String): List<Trade>

    @Transactional
    @Modifying
    @Query(
        value = """
    DELETE FROM trade
    WHERE id IN (
        SELECT * FROM (
            SELECT t.id FROM trade t
            JOIN setup s ON t.setup_id = s.id
            JOIN setup_group sg ON s.setup_group_id = sg.id
            JOIN setup_groups sgs ON sg.setup_groups_id = sgs.id
            WHERE sgs.name = :name
        ) AS temp
    )
""", nativeQuery = true
    )
    fun deleteBySetupGroupName(@Param("name") name: String): Int

    @Transactional
    @Modifying
    @Query(
        value = """
    DELETE FROM trade
    WHERE id IN (
        SELECT * FROM (
            SELECT t.id FROM trade t
            JOIN account a ON t.account_id = a.id
            WHERE a.name = :name
        ) AS temp
    )
""", nativeQuery = true
    )
    fun deleteByAccountName(@Param("name") name: String): Int

}
