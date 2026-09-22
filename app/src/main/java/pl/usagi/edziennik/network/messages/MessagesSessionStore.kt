package pl.usagi.edziennik.network.messages

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagesSessionStore @Inject constructor() {
    private var context: MessagesSessionContext? = null

    @Synchronized
    fun get(): MessagesSessionContext? = context

    @Synchronized
    fun save(context: MessagesSessionContext) {
        this.context = context
    }

    @Synchronized
    fun clear() {
        context = null
    }
}
