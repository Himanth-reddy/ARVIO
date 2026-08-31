@file:Suppress("unused")

package com.lagradost.cloudstream3.syncproviders

enum class SyncIdName {
    Mal,
    Anilist,
    AniList,
    OpenSubtitles,
    SubDL,
    Simkl,
    LocalList,
    Kitsu,
    None
}

data class AuthUser(
    val username: String = "",
    val id: String = "",
    val avatarUrl: String? = null
)

open class SyncAPI(
    open val name: String = "",
    open val key: String = "",
    open val mainUrl: String = "",
    open val iconUrl: String? = null,
    open val requiresLogin: Boolean = false,
    open val syncIdName: SyncIdName = SyncIdName.AniList
) {
    data class LibraryList(
        val name: String = "",
        val items: List<LibraryMetadata> = emptyList()
    )

    data class LibraryMetadata(
        val id: String = "",
        val name: String = "",
        val posterUrl: String? = null
    )
}

open class SyncRepo(
    open val name: String = "",
    open val mainUrl: String = "",
    open val iconUrl: String? = null,
    open val requiresLogin: Boolean = false,
    open val syncIdName: SyncIdName = SyncIdName.AniList
) {
    open var hasUser: Boolean = false
    open var requireLibrary: Boolean = false
}

object AccountManager {
    @JvmField
    val syncApis: Array<SyncAPI> = emptyArray()
    @JvmField
    val malApi: SyncAPI? = null
    @JvmField
    val aniListApi: SyncAPI? = null
    @JvmField
    val openSubtitlesApi: SyncAPI? = null
    @JvmField
    val subDlApi: SyncAPI? = null

    fun getSyncApis(): List<SyncAPI> = emptyList()
}
