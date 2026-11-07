package com.example.alakey

import com.example.alakey.data.EventLog
import org.junit.Assert.assertEquals
import org.junit.Test

/** M12: LIKE wildcards in a query must match themselves, not everything. */
class EventLogEscapeTest {

    @Test
    fun `percent is escaped`() {
        assertEquals("100\\%", EventLog.escapeLike("100%"))
    }

    @Test
    fun `underscore is escaped`() {
        assertEquals("feed\\_url", EventLog.escapeLike("feed_url"))
    }

    @Test
    fun `backslash is escaped first`() {
        // input a\ -> a\\ (backslash doubling survives the later wildcard pass)
        assertEquals("a\\\\", EventLog.escapeLike("a\\"))
    }

    @Test
    fun `plain text passes through unchanged`() {
        assertEquals("sync feed 12", EventLog.escapeLike("sync feed 12"))
    }
}
