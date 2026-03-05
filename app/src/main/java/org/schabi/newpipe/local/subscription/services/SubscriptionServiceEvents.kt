package org.schabi.newpipe.local.subscription.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for one-shot subscription import/export completion events.
 */
object SubscriptionServiceEvents {
    private val mutableEvents = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    @JvmStatic
    fun events(): SharedFlow<Event> = mutableEvents.asSharedFlow()

    @JvmStatic
    fun emitImportCompleted() {
        mutableEvents.tryEmit(Event.ImportCompleted)
    }

    @JvmStatic
    fun emitExportCompleted() {
        mutableEvents.tryEmit(Event.ExportCompleted)
    }

    enum class Event {
        ImportCompleted,
        ExportCompleted
    }
}
