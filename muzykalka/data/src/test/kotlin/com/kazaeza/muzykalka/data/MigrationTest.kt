package com.kazaeza.muzykalka.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kazaeza.muzykalka.data.db.MuzykalkaDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationTest {
    @Test fun oldAnswersSurviveWithUnknownFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = MuzykalkaDatabase.FILE_NAME
        val path = "schemas/com.kazaeza.muzykalka.data.db.MuzykalkaDatabase/1.json"
        val schema = listOf(File(path), File("data", path)).first { it.isFile }
        val old = JSONObject(schema.readText()).getJSONObject("database")
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val tables = old.getJSONArray("entities")
            for (i in 0 until tables.length()) {
                val entity = tables.getJSONObject(i)
                val table = entity.getString("tableName")
                fun exec(sql: String) = db.execSQL(sql.replace("\${TABLE_NAME}", table))
                exec(entity.getString("createSql"))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) exec(indices.getJSONObject(j).getString("createSql"))
            }
            val setup = old.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO session VALUES (7,'SET',100,1234,NULL)")
            db.execSQL("INSERT INTO attempt VALUES (9,7,'TIR_CLASSIC',2,'tir:L2:4','4','5',0,950,NULL,0,'C',1235)")
            db.version = 1
        }
        val room = MuzykalkaDatabase.open(context)
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(2, db.version)
            db.query("SELECT focusStatus, focusRound, replayCount, given FROM session JOIN attempt ON session.id = attempt.sessionId WHERE attempt.id = 9").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0) && it.isNull(1) && it.isNull(2))
                assertEquals("5", it.getString(3))
            }
        } finally {
            room.close()
        }
    }
}
