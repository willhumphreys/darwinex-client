package uk.co.threebugs.darwinexclient.helpers

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import uk.co.threebugs.darwinexclient.metatrader.AccountInfo
import uk.co.threebugs.darwinexclient.metatrader.CurrencyInfo
import uk.co.threebugs.darwinexclient.metatrader.Orders
import uk.co.threebugs.darwinexclient.metatrader.TradeInfo
import uk.co.threebugs.darwinexclient.utils.logger
import java.io.File
import java.math.BigDecimal
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import kotlin.random.Random

class MetaTraderFileHelper {

    companion object {

        private val marketDataPath = Path.of("test-ea-files/DWX/DWX_Market_Data.json")
        private val ordersFile = File("test-ea-files/DWX/DWX_Orders.json")

        private val mapper = jacksonObjectMapper().registerModule(JavaTimeModule())

        private val accountInfo = AccountInfo(
            number = 123456,
            leverage = 100,
            balance = BigDecimal(1000.0),
            freeMargin = BigDecimal(1000.0),
            name = "test",
            currency = "USD",
            equity = BigDecimal(1000.0)
        )

        fun deleteFilesBeforeTest(path: Path, prefix: String, suffix: String) {
            Files.walkFileTree(
                path,
                setOf(FileVisitOption.FOLLOW_LINKS),
                Integer.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val fileName = file.fileName.toString()
                        if (fileName.startsWith(prefix) && fileName.endsWith(suffix)) {
                            logger.info("Deleting file: $fileName")
                            Files.delete(file)
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        return FileVisitResult.CONTINUE
                    }
                })
        }

        fun writeMarketData(symbol: String) {
            mapper.writeValue(
                marketDataPath.toFile(),
                mapOf(
                    symbol to generateCurrentInfo()
                )
            )
            logger.info("Wrote marketData file: $marketDataPath")
        }

        private const val BASE_VALUE = "1.05"

        private fun generateLastThreeDigits(): String {
            return Random.nextInt(100, 999).toString()
        }

        private fun generateCurrentInfo(): CurrencyInfo {


            val ask = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}")
            val bid = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}")
            val last = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}")
            val tickValue = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}")

            return CurrencyInfo(
                ask = ask,
                bid = bid,
                last = last,
                tickValue = tickValue
            )
        }


        fun deleteMarketDataFile() {
            if (Files.exists(marketDataPath)) {
                logger.info("Deleting marketData file: $marketDataPath")
                Files.delete(marketDataPath)
            }
        }

        fun writeEmptyOrders() {
            mapper.writeValue(ordersFile, Orders(accountInfo, emptyMap()))
        }

        fun writeOrdersWithMagic(magicTrade1: Int?, magicTrade2: Int?, symbol: String) {

            val openTradeOrder = Orders(
                accountInfo,
                mapOf(
                    1 to TradeInfo(
                        magic = magicTrade1!!,
                        lots = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}"),
                        symbol = symbol,
                        swap = null,
                        openTime = LocalDateTime.parse("2023-10-30T09:00"),
                        openPrice = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}"),
                        stopLoss = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}"),
                        takeProfit = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}"),
                        type = "buylimit",
                        comment = "test",
                    ),
                    2 to TradeInfo(
                        magic = magicTrade2!!,
                        lots = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}"),
                        symbol = symbol,
                        swap = null,
                        openTime = LocalDateTime.parse("2023-10-30T09:00"),
                        openPrice = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}"),
                        stopLoss = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}"),
                        takeProfit = BigDecimal("$BASE_VALUE${generateLastThreeDigits()}"),
                        type = "buylimit",
                        comment = "test",
                    )

                )
            )
            mapper.writeValue(ordersFile, openTradeOrder)
        }
    }

}