import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import uk.co.threebugs.darwinexclient.Status
import uk.co.threebugs.darwinexclient.helpers.MetaTraderFileHelper.Companion.deleteFilesBeforeTest
import uk.co.threebugs.darwinexclient.helpers.MetaTraderFileHelper.Companion.deleteMarketDataFile
import uk.co.threebugs.darwinexclient.helpers.MetaTraderFileHelper.Companion.readOrdersFile
import uk.co.threebugs.darwinexclient.helpers.MetaTraderFileHelper.Companion.writeEmptyOrders
import uk.co.threebugs.darwinexclient.helpers.MetaTraderFileHelper.Companion.writeMarketData
import uk.co.threebugs.darwinexclient.helpers.MetaTraderFileHelper.Companion.writeOrdersFile
import uk.co.threebugs.darwinexclient.helpers.MetaTraderFileHelper.Companion.writeOrdersWithMagic
import uk.co.threebugs.darwinexclient.helpers.RestCallHelper.Companion.deleteTradesFromTestAccount
import uk.co.threebugs.darwinexclient.helpers.RestCallHelper.Companion.getTrades
import uk.co.threebugs.darwinexclient.helpers.RestCallHelper.Companion.startProcessing
import uk.co.threebugs.darwinexclient.helpers.RestCallHelper.Companion.stopProcessing
import uk.co.threebugs.darwinexclient.helpers.TimeHelper.Companion.getTime
import uk.co.threebugs.darwinexclient.helpers.TimeHelper.Companion.setTimeToNearlyCloseTime
import uk.co.threebugs.darwinexclient.helpers.TimeHelper.Companion.setTimeToNextMonday
import uk.co.threebugs.darwinexclient.helpers.TimeOutHelper.Companion.waitForCondition
import uk.co.threebugs.darwinexclient.utils.logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime


private const val SECONDS_30 = 30000L
private const val SECONDS_5 = 5000L
private const val EURUSD = "EURUSD"

class TradeServiceTest : FunSpec() {

    private val accountName = "test"

    override suspend fun beforeEach(testCase: TestCase) {
        deleteFilesBeforeTest(Path.of("test-ea-files/DWX"), "DWX_Commands_", ".txt")

        writeEmptyOrders()
        deleteTradesFromTestAccount(accountName)
        deleteMarketDataFile()
        getTrades(accountName).shouldBeEmpty()
        setTimeToNextMonday()
        startProcessing()
        delay(5000)


        super.beforeEach(testCase)
    }

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {

        writeEmptyOrders()

        delay(SECONDS_5)

        stopProcessing()

        //deleteFilesBeforeTest(Path.of("test-ea-files/DWX"), "DWX_Commands_", ".txt")

        super.afterEach(testCase, result)
    }

    init {

        test("place 2 eurusd long trades and close by user") {

            writeMarketData(EURUSD)

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for trades with status PENDING to be written to the db..."
            ) {
                logger.info("Client time ${getTime()}")
                val foundTrades = getTrades(accountName)

                if (foundTrades.isNotEmpty()) {

                    foundTrades.forEach {
                        logger.info("Found trade: $it")
                        it.status shouldBe Status.PENDING
                        it.setup shouldNotBe null
                        it.setup?.symbol shouldBe EURUSD
                        it.setup shouldNotBe null
                        it.setup?.isLong() shouldBe true
                        it.targetPlaceDateTime shouldBe ZonedDateTime.parse("2023-10-30T09:00Z[UTC]")
                    }

                    return@waitForCondition true  // Breaks out of the waiting loop
                }
                false  // Continues the waiting loop
            }


            val foundTrades = getTrades(accountName)
            val (magicTrade1, magicTrade2) = foundTrades.take(2).map { it.id }

            writeMarketData(EURUSD)


            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for the time to be 09:00..."
            ) {

                logger.info("Waiting for EA to write file...")
                val time = getTime()
                logger.info("Client time $time")

                if (!time.isBefore(ZonedDateTime.parse("2023-10-30T09:00Z[UTC]")))
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

            writeMarketData(EURUSD)


            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to be OrderSent..."
            ) {

                logger.info("Client time ${getTime()}")
                val tradesWithStatusOrderSent = getTrades(accountName)

                tradesWithStatusOrderSent.size shouldBe 2

                val allTradesHaveStatusSent = tradesWithStatusOrderSent.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.ORDER_SENT
                }

                tradesWithStatusOrderSent.any { it.id == magicTrade1 } shouldBe true
                tradesWithStatusOrderSent.any { it.id == magicTrade2 } shouldBe true

                writeMarketData(EURUSD)

                if (allTradesHaveStatusSent) {
                    return@waitForCondition true  // Breaks out of the waiting loop
                }

                false  // Continues the waiting loop
            }

            writeOrdersWithMagic(magicTrade1, magicTrade2, "EURUSD")

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to be placed in MT..."
            ) {

                logger.info("Client time ${getTime()}")
                val placedInMtTrades = getTrades(accountName)

                placedInMtTrades.size shouldBe 2

                val allTradesHaveStatusPlacedInMT = placedInMtTrades.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.PLACED_IN_MT
                }

                writeMarketData(EURUSD)

                if (allTradesHaveStatusPlacedInMT)
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

            Files.exists(Path.of("test-ea-files/DWX/DWX_Commands_0.txt")) shouldBe true
            Files.exists(Path.of("test-ea-files/DWX/DWX_Commands_1.txt")) shouldBe true

            Files.readString(Path.of("test-ea-files/DWX/DWX_Commands_0.txt"))
                .contains("OPEN_ORDER|EURUSD,buylimit,") shouldBe true
            Files.readString(Path.of("test-ea-files/DWX/DWX_Commands_1.txt"))
                .contains("OPEN_ORDER|EURUSD,buylimit,") shouldBe true

            writeMarketData(EURUSD)

            val ordersAndAccount = readOrdersFile()
            ordersAndAccount.orders.size shouldBe 2

            ordersAndAccount.orders[1]?.type = "buy"
            ordersAndAccount.orders[2]?.type = "buy"

            writeOrdersFile(ordersAndAccount)

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to have status filled..."
            ) {

                logger.info("Client time ${getTime()}")
                val filledTrades = getTrades(accountName)

                filledTrades.size shouldBe 2

                val allTradesHaveStatusFilled = filledTrades.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.FILLED
                }

                writeMarketData(EURUSD)

                if (allTradesHaveStatusFilled)
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

            writeMarketData(EURUSD)

            writeEmptyOrders()

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to have status closed by user"
            ) {

                logger.info("Client time ${getTime()}")
                val closedByUserTrades = getTrades(accountName)

                closedByUserTrades.size shouldBe 2

                val allTradesHaveStatusClosedByUser = closedByUserTrades.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.CLOSED_BY_USER
                }

                writeMarketData(EURUSD)

                if (allTradesHaveStatusClosedByUser)
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

        }

        test("place 2 eurusd long trades and out of time close") {

            writeMarketData(EURUSD)

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for trades with status PENDING to be written to the db..."
            ) {
                logger.info("Client time ${getTime()}")
                val foundTrades = getTrades(accountName)

                if (foundTrades.isNotEmpty()) {

                    foundTrades.forEach {
                        logger.info("Found trade: $it")
                        it.status shouldBe Status.PENDING
                        it.setup shouldNotBe null
                        it.setup?.symbol shouldBe EURUSD
                        it.setup shouldNotBe null
                        it.setup?.isLong() shouldBe true
                        it.targetPlaceDateTime shouldBe ZonedDateTime.parse("2023-10-30T09:00Z[UTC]")
                    }

                    return@waitForCondition true  // Breaks out of the waiting loop
                }
                false  // Continues the waiting loop
            }


            val foundTrades = getTrades(accountName)
            val (magicTrade1, magicTrade2) = foundTrades.take(2).map { it.id }

            writeMarketData(EURUSD)


            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for the time to be 09:00..."
            ) {

                logger.info("Waiting for EA to write file...")
                val time = getTime()
                logger.info("Client time $time")

                if (!time.isBefore(ZonedDateTime.parse("2023-10-30T09:00Z[UTC]")))
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

            writeMarketData(EURUSD)


            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to be OrderSent..."
            ) {

                logger.info("Client time ${getTime()}")
                val tradesWithStatusOrderSent = getTrades(accountName)

                tradesWithStatusOrderSent.size shouldBe 2

                val allTradesHaveStatusSent = tradesWithStatusOrderSent.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.ORDER_SENT
                }

                tradesWithStatusOrderSent.any { it.id == magicTrade1 } shouldBe true
                tradesWithStatusOrderSent.any { it.id == magicTrade2 } shouldBe true

                writeMarketData(EURUSD)

                if (allTradesHaveStatusSent) {
                    return@waitForCondition true  // Breaks out of the waiting loop
                }

                false  // Continues the waiting loop
            }

            writeOrdersWithMagic(magicTrade1, magicTrade2, "EURUSD")

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to be placed in MT..."
            ) {

                logger.info("Client time ${getTime()}")
                val trades = getTrades(accountName)

                trades.size shouldBe 2

                val tradesHaveCorrectStatus = trades.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.PLACED_IN_MT
                }

                writeMarketData(EURUSD)

                if (tradesHaveCorrectStatus)
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

            Files.exists(Path.of("test-ea-files/DWX/DWX_Commands_0.txt")) shouldBe true
            Files.exists(Path.of("test-ea-files/DWX/DWX_Commands_1.txt")) shouldBe true

            Files.readString(Path.of("test-ea-files/DWX/DWX_Commands_0.txt"))
                .contains("OPEN_ORDER|EURUSD,buylimit,") shouldBe true
            Files.readString(Path.of("test-ea-files/DWX/DWX_Commands_1.txt"))
                .contains("OPEN_ORDER|EURUSD,buylimit,") shouldBe true

            writeMarketData(EURUSD)


            writeEmptyOrders()

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to have status out of time"
            ) {

                logger.info("Client time ${getTime()}")
                val trades = getTrades(accountName)

                trades.size shouldBe 2

                val tradesHaveCorrectStatus = trades.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.OUT_OF_TIME
                }

                writeMarketData(EURUSD)

                if (tradesHaveCorrectStatus)
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

        }

        test("place 2 eurusd long trades and close at time") {

            writeMarketData(EURUSD)

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for trades with status PENDING to be written to the db..."
            ) {
                logger.info("Client time ${getTime()}")
                val foundTrades = getTrades(accountName)

                if (foundTrades.isNotEmpty()) {

                    foundTrades.forEach {
                        logger.info("Found trade: $it")
                        it.status shouldBe Status.PENDING
                        it.setup shouldNotBe null
                        it.setup?.symbol shouldBe EURUSD
                        it.setup shouldNotBe null
                        it.setup?.isLong() shouldBe true
                        it.targetPlaceDateTime shouldBe ZonedDateTime.parse("2023-10-30T09:00Z[UTC]")
                    }

                    return@waitForCondition true  // Breaks out of the waiting loop
                }
                false  // Continues the waiting loop
            }


            val foundTrades = getTrades(accountName)
            val (magicTrade1, magicTrade2) = foundTrades.take(2).map { it.id }

            writeMarketData(EURUSD)


            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for the time to be 09:00..."
            ) {

                logger.info("Waiting for EA to write file...")
                val time = getTime()
                logger.info("Client time $time")

                if (!time.isBefore(ZonedDateTime.parse("2023-10-30T09:00Z[UTC]")))
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

            writeMarketData(EURUSD)


            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to be OrderSent..."
            ) {

                logger.info("Client time ${getTime()}")
                val tradesWithStatusOrderSent = getTrades(accountName)

                tradesWithStatusOrderSent.size shouldBe 2

                val allTradesHaveStatusSent = tradesWithStatusOrderSent.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.ORDER_SENT
                }

                tradesWithStatusOrderSent.any { it.id == magicTrade1 } shouldBe true
                tradesWithStatusOrderSent.any { it.id == magicTrade2 } shouldBe true

                writeMarketData(EURUSD)

                if (allTradesHaveStatusSent) {
                    return@waitForCondition true  // Breaks out of the waiting loop
                }

                false  // Continues the waiting loop
            }

            writeOrdersWithMagic(magicTrade1, magicTrade2, "EURUSD")

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to be placed in MT..."
            ) {

                logger.info("Client time ${getTime()}")
                val placedInMtTrades = getTrades(accountName)

                placedInMtTrades.size shouldBe 2

                val allTradesHaveStatusPlacedInMT = placedInMtTrades.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.PLACED_IN_MT
                }

                writeMarketData(EURUSD)

                if (allTradesHaveStatusPlacedInMT)
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }


            Files.exists(Path.of("test-ea-files/DWX/DWX_Commands_0.txt")) shouldBe true
            Files.exists(Path.of("test-ea-files/DWX/DWX_Commands_1.txt")) shouldBe true

            Files.readString(Path.of("test-ea-files/DWX/DWX_Commands_0.txt"))
                .contains("OPEN_ORDER|EURUSD,buylimit,") shouldBe true
            Files.readString(Path.of("test-ea-files/DWX/DWX_Commands_1.txt"))
                .contains("OPEN_ORDER|EURUSD,buylimit,") shouldBe true


            writeMarketData(EURUSD)

            val ordersAndAccount = readOrdersFile()
            ordersAndAccount.orders.size shouldBe 2

            ordersAndAccount.orders[1]?.type = "buy"
            ordersAndAccount.orders[2]?.type = "buy"

            writeOrdersFile(ordersAndAccount)

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to have status filled..."
            ) {

                logger.info("Client time ${getTime()}")
                val filledTrades = getTrades(accountName)

                filledTrades.size shouldBe 2

                val allTradesHaveStatusFilled = filledTrades.all {
                    logger.info("Found trade status: ${it.status}")
                    it.status == Status.FILLED
                }

                writeMarketData(EURUSD)

                if (allTradesHaveStatusFilled)
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }

            writeMarketData(EURUSD)

            val filledTrades = getTrades(accountName)

            filledTrades.size shouldBe 2

            setTimeToNearlyCloseTime(filledTrades[1])


            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to have status closed by magic sent"
            ) {

                logger.info("Client time ${getTime()}")
                val closedByUserTrades = getTrades(accountName)


                val closedByMagicSentCount = closedByUserTrades.count { it.status == Status.CLOSED_BY_MAGIC_SENT }
                logger.info("Number of trades with status CLOSED_BY_MAGIC_SENT: $closedByMagicSentCount")

                if (closedByMagicSentCount == 2) {
                    return@waitForCondition true
                }
                writeMarketData(EURUSD)

                false
            }

            Files.exists(Path.of("test-ea-files/DWX/DWX_Commands_2.txt")) shouldBe true
            Files.exists(Path.of("test-ea-files/DWX/DWX_Commands_3.txt")) shouldBe true

            Files.readString(Path.of("test-ea-files/DWX/DWX_Commands_2.txt"))
                .contains("|CLOSE_ORDERS_BY_MAGIC|") shouldBe true
            Files.readString(Path.of("test-ea-files/DWX/DWX_Commands_3.txt"))
                .contains("|CLOSE_ORDERS_BY_MAGIC|") shouldBe true

            writeMarketData(EURUSD)

            writeEmptyOrders()

            waitForCondition(
                timeout = SECONDS_30,
                interval = SECONDS_5,
                logMessage = "Waiting for all trades to have status closed by time"
            ) {

                logger.info("Client time ${getTime()}")
                val closedByUserTrades = getTrades(accountName).count { it.status == Status.CLOSED_BY_TIME }

                writeMarketData(EURUSD)

                if (closedByUserTrades == 2)
                    return@waitForCondition true  // Breaks out of the waiting loop

                false  // Continues the waiting loop
            }
        }
    }
}