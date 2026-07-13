package hr.bebindnevnik.app.notifications

import hr.bebindnevnik.app.data.TummyInputMethod
import hr.bebindnevnik.app.data.TummySessionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class TimerControllerTest {
    @Test fun threeConsecutiveSessionsRemainSeparateAndReturnToIdle() =
        runTest {
            val fixture = fixture(this)

            fixture.record(4 * 60 + 20)
            fixture.record(7 * 60 + 10)
            fixture.record(3 * 60 + 30)

            assertEquals(listOf(260L, 430L, 210L), fixture.saved.map { it.durationSeconds })
            assertEquals(900L, fixture.saved.sumOf { it.durationSeconds })
            assertEquals(
                3,
                fixture.saved
                    .map { it.id }
                    .distinct()
                    .size,
            )
            assertTrue(fixture.saved.all { it.inputMethod == TummyInputMethod.STOPERICA })
            assertEquals(TimerPhase.IDLE, fixture.controller.state.value.phase)
            assertFalse(fixture.notifications.visible)
        }

    @Test fun repeatedStartAndStopCannotCreateTwoActiveOrDuplicateSessions() =
        runTest {
            val fixture = fixture(this)
            fixture.controller.start()
            fixture.controller.start()
            assertEquals(1, fixture.notifications.showCount)

            fixture.elapsedMillis += 10_000
            fixture.controller.stopAndSave()
            fixture.controller.stopAndSave()
            advanceUntilIdle()

            assertEquals(1, fixture.saved.size)
            assertEquals(TimerPhase.IDLE, fixture.controller.state.value.phase)
        }

    @Test fun shortSessionRequiresConfirmationAndDismissOrBackgroundNeverSaves() =
        runTest {
            val fixture = fixture(this)
            fixture.controller.start()
            fixture.elapsedMillis = 4_000
            fixture.controller.stopAndSave()
            assertEquals(TimerPhase.CONFIRMING, fixture.controller.state.value.phase)
            assertTrue(fixture.saved.isEmpty())

            fixture.controller.cancel()
            assertEquals(TimerPhase.IDLE, fixture.controller.state.value.phase)
            assertTrue(fixture.saved.isEmpty())

            fixture.controller.start()
            fixture.elapsedMillis += 4_000
            fixture.controller.stopAndSave()
            fixture.controller.confirmSave()
            advanceUntilIdle()
            assertEquals(1, fixture.saved.size)

            fixture.controller.start()
            fixture.elapsedMillis += 10_000
            fixture.controller.onBackgrounded()
            advanceUntilIdle()
            assertEquals(TimerPhase.IDLE, fixture.controller.state.value.phase)
            assertEquals(1, fixture.saved.size)

            fixture.controller.start()
            fixture.elapsedMillis += 3_601_000
            fixture.controller.stopAndSave()
            assertEquals(TimerPhase.CONFIRMING, fixture.controller.state.value.phase)
            fixture.controller.cancel()
            assertEquals(1, fixture.saved.size)
        }

    @Test fun cancellationPreservesPreviouslySavedSessionsAndAllowsImmediateRestart() =
        runTest {
            val fixture = fixture(this)
            fixture.record(10)
            fixture.controller.start()
            fixture.elapsedMillis += 20_000
            fixture.controller.cancel(TimerCancelReason.DATE_CHANGED)
            fixture.controller.start()
            fixture.elapsedMillis += 30_000
            fixture.controller.stopAndSave()
            advanceUntilIdle()

            assertEquals(listOf(10L, 30L), fixture.saved.map { it.durationSeconds })
            assertEquals(TimerPhase.IDLE, fixture.controller.state.value.phase)
        }

    @Test fun crossingMidnightStopsAndRequiresExplicitSaveForPreviousDate() =
        runTest {
            val fixture = fixture(this)
            fixture.controller.start()
            fixture.elapsedMillis = 120_000

            fixture.controller.onLocalDateChanged(LocalDate.of(2026, 7, 13))

            assertEquals(TimerPhase.CONFIRMING, fixture.controller.state.value.phase)
            assertTrue(fixture.controller.state.value.crossedMidnight)
            assertEquals(LocalDate.of(2026, 7, 12), fixture.controller.state.value.sessionDate)
            assertFalse(fixture.notifications.visible)
            assertTrue(fixture.saved.isEmpty())

            fixture.controller.confirmSave()
            advanceUntilIdle()
            assertEquals("2026-07-12", fixture.saved.single().date)
            assertEquals(120L, fixture.saved.single().durationSeconds)
        }

    private fun fixture(scope: TestScope): Fixture {
        lateinit var fixture: Fixture
        val notifications = FakeTimerNotifications()
        val saved = mutableListOf<TummySessionEntity>()
        var nextId = 1L
        val controller =
            TimerController(
                saveSession = { date, time, duration, method ->
                    TummySessionEntity(
                        id = nextId++,
                        date = date.toString(),
                        time = time.toString(),
                        durationSeconds = duration,
                        inputMethod = method,
                        createdAt = nextId,
                        updatedAt = nextId,
                    ).also(saved::add)
                },
                notifications = notifications,
                scope = scope,
                environment =
                    TimerEnvironment(
                        elapsedRealtime = { fixture.elapsedMillis },
                        currentDate = { LocalDate.of(2026, 7, 12) },
                        currentTime = { LocalTime.of(10, 15) },
                        tickDelayMillis = 10,
                    ),
            )
        fixture = Fixture(controller, notifications, saved, scope)
        return fixture
    }

    private data class Fixture(
        val controller: TimerController,
        val notifications: FakeTimerNotifications,
        val saved: MutableList<TummySessionEntity>,
        val scope: TestScope,
        var elapsedMillis: Long = 0,
    ) {
        fun record(seconds: Long) {
            controller.start()
            elapsedMillis += seconds * 1_000
            controller.stopAndSave()
            scope.advanceUntilIdle()
            assertEquals(TimerPhase.IDLE, controller.state.value.phase)
        }
    }

    private class FakeTimerNotifications : TimerNotifications {
        var visible = false
        var showCount = 0

        override fun showTimer() {
            visible = true
            showCount++
        }

        override fun cancelTimer() {
            visible = false
        }
    }
}
