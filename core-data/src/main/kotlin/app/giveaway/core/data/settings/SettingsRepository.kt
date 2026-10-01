package app.giveaway.core.data.settings

import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.data.db.SettingsDao
import app.giveaway.core.data.db.SettingsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The single settings row (spec: S5), with the spec's defaults until the user changes something. */
interface SettingsRepository {
    fun observe(): Flow<SettingsEntity>

    suspend fun get(): SettingsEntity

    /** Turns app lock on with [method], or off with null. */
    suspend fun setAppLock(method: AppLockMethod?)

    suspend fun update(transform: (SettingsEntity) -> SettingsEntity)
}

class DefaultSettingsRepository @Inject constructor(private val dao: SettingsDao) : SettingsRepository {

    override fun observe(): Flow<SettingsEntity> = dao.observe().map { it ?: SettingsEntity() }

    override suspend fun get(): SettingsEntity = dao.get() ?: SettingsEntity()

    override suspend fun setAppLock(method: AppLockMethod?) =
        update { it.copy(appLockEnabled = method != null, appLockMethod = method) }

    override suspend fun update(transform: (SettingsEntity) -> SettingsEntity) = dao.upsert(transform(get()))
}
