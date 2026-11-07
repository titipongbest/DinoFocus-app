package com.titipong.dinofocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Simulates two phones syncing through one shared file, the way AppViewModel.syncNow does:
 * read file → mergeData(local, remote) → write the merged data back.
 */
class SyncTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private fun at(day: LocalDate, h: Int, m: Int = 0) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun round(id: String, ts: Long, min: Int, tag: String? = null, dead: Boolean = false, sp: String = "rex") =
        Entry(id = id, sp = sp, ts = ts, workSec = min * 60, targetMin = 25, tag = tag, dead = dead)

    /** One device's local data as recordResult would leave it. */
    private fun device(vararg rounds: Entry) = AppData(collection = rounds.toList()).recomputed()

    /** Shared file as written by Backup and read back with Backup.parse. */
    private var file: String? = null

    private fun sync(local: AppData): AppData {
        val remote = file?.let { Backup.parse(it) }
        val merged = mergeData(local, remote)
        file = Backup.json.encodeToString(Backup.serializer(), Backup(data = merged.copy(session = Session())))
        return merged
    }

    @Test fun twoPhonesAddUp_15plus20is35_onBoth() {
        var a = device(round("a1", at(today, 9), 15))
        var b = device(round("b1", at(today, 10), 20))
        a = sync(a)
        assertEquals(15, a.minutesOn(today.toString()))
        b = sync(b)
        assertEquals(35, b.minutesOn(today.toString()))
        a = sync(a)
        assertEquals(35, a.minutesOn(today.toString()))
        assertEquals(2, a.dailyPomos[today.toString()])
        assertEquals(2, b.totalPomos)
    }

    @Test fun syncingAgainNeverDoubleCounts() {
        var a = device(round("a1", at(today, 9), 15))
        var b = device(round("b1", at(today, 10), 20))
        repeat(5) { a = sync(a); b = sync(b) }
        assertEquals(35, a.minutesOn(today.toString()))
        assertEquals(35, b.minutesOn(today.toString()))
        assertEquals(2, a.collection.size)
        assertEquals(2, b.collection.size)
    }

    @Test fun newRoundAfterSyncIsAddedOnTop() {
        var a = device(round("a1", at(today, 9), 15))
        var b = device(round("b1", at(today, 10), 20))
        a = sync(a); b = sync(b); a = sync(a)
        a = mergeData(a.copy(collection = a.collection + round("a2", at(today, 11), 25)), null)
        a = sync(a); b = sync(b)
        assertEquals(60, b.minutesOn(today.toString()))
        assertEquals(3, b.dailyPomos[today.toString()])
    }

    @Test fun gaveUpRoundsCountTimeAndRoundButNotStreak() {
        val y = today.minusDays(1)
        var a = device(round("a1", at(y, 9), 25), round("a2", at(today, 9), 7, dead = true))
        a = sync(a)
        assertEquals(7, a.minutesOn(today.toString()))
        assertEquals(1, a.dailyPomos[today.toString()])
        assertEquals(2, a.totalPomos)
        assertEquals(y.toString(), a.lastDay)   // a grave does not extend the streak
        assertEquals(1, a.streak)
    }

    @Test fun tagsAreMergedByNameAndKeepPerTagTime() {
        var a = device(round("a1", at(today, 9), 15, tag = "Blender"))
        a = a.copy(tags = a.tags + Tag("Python", 0xFF8B5CF6))
        var b = device(round("b1", at(today, 10), 20, tag = "Blender"), round("b2", at(today, 11), 10, tag = "Kotlin"))
        b = b.copy(tags = b.tags + Tag("kotlin", 0xFF06B6D4))
        a = sync(a); b = sync(b); a = sync(a)
        val names = a.tags.map { it.name.lowercase() }
        assertTrue("python" in names && "kotlin" in names && "blender" in names)
        assertEquals(names.size, names.toSet().size)
        assertEquals(35 * 60, a.collection.filter { it.tag == "Blender" }.sumOf { it.workSec })
    }

    @Test fun eachPhoneKeepsItsOwnSettingsAndRunningRound() {
        var a = device(round("a1", at(today, 9), 15)).copy(pet = "spino", workMin = 45, tag = "Blender",
            session = Session(accumulated = 0, runStart = 123L))
        var b = device(round("b1", at(today, 10), 20)).copy(pet = "ptero", workMin = 25)
        b = sync(b)
        a = sync(a)
        assertEquals("spino", a.pet)
        assertEquals(45, a.workMin)
        assertEquals(123L, a.session.runStart)
        assertNull("running round is not written to the shared file", Backup.parse(file!!)!!.session.runStart)
    }

    @Test fun oldRoundsWithoutIdsDoNotDuplicate() {
        val old = Entry(sp = "rex", ts = at(today, 8), workSec = 900)   // saved before ids existed
        var a = device(old)
        var b = device(old, round("b1", at(today, 10), 20))
        a = sync(a); b = sync(b); a = sync(a)
        assertEquals(2, a.collection.size)
        assertEquals(35, a.minutesOn(today.toString()))
    }

    @Test fun fileWithLeftoverBytesStillReads() {
        val a = sync(device(round("a1", at(today, 9), 15)))
        val longer = file!!
        // overwrite with shorter JSON without truncation: old tail stays behind
        val shorter = Backup.json.encodeToString(Backup.serializer(), Backup(data = AppData(collection = a.collection)))
        val damaged = shorter + longer.substring(minOf(shorter.length, longer.length))
        assertNotNull(Backup.parse(damaged))
        assertEquals(1, Backup.parse(damaged)!!.collection.size)
    }

    @Test fun oldBackupFileStillMerges() {
        file = Backup.json.encodeToString(Backup.serializer(), Backup(data = device(round("x1", at(today, 7), 30))))
        val a = sync(device(round("a1", at(today, 9), 15)))
        assertEquals(45, a.minutesOn(today.toString()))
    }

    @Test fun secondsAreDroppedSoRoundsAddUp() {
        // 4:50 + 15:40 + 0:45 + 25:30 + 17:40 → 4 + 15 + 0 + 25 + 17 = 61
        val secs = listOf(290, 940, 45, 1530, 1060)
        var a = AppData(collection = secs.mapIndexed { i, s -> Entry(id = "s$i", sp = "rex", ts = at(today, 8 + i), workSec = s) })
        a = sync(a)
        assertEquals(61, a.minutesOn(today.toString()))
        assertEquals(a.collection.sumOf { it.workSec / 60 }, a.minutesOn(today.toString()))
    }

    @Test fun savedTagsReachTheOtherPhone() {
        var a = device().copy(tags = listOf(Tag("Blender", 1), Tag("ฟิสิกส์", 2)))
        var b = device().copy(tags = listOf(Tag("Blender", 1)))
        a = sync(a); b = sync(b)
        assertTrue(b.tags.any { it.name == "ฟิสิกส์" })
        assertEquals(2, b.tags.size)
    }
}
