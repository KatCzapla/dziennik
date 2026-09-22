package pl.usagi.edziennik.network.messages

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface MessagesNetworkModule {
    @Binds
    @Singleton
    fun bindMessagesUrlProvider(provider: UonetMessagesUrlProvider): MessagesUrlProvider

    @Binds
    @Singleton
    fun bindMessagesSsoClient(client: UonetMessagesSsoClient): MessagesSsoClient

    @Binds
    @Singleton
    fun bindMessagesApiClient(client: OkHttpMessagesApiClient): MessagesApiClient

    @Binds
    @Singleton
    fun bindMessagesRemoteDataSource(dataSource: MessagesDataSource): MessagesRemoteDataSource
}
