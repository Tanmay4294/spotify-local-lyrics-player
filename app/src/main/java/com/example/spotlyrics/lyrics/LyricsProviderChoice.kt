package com.example.spotlyrics.lyrics

enum class LyricsProviderChoice(val displayName: String, val sourceKey: String) {
    LRCLIB("LRCLIB", "lrclib"),
    MUSIXMATCH("Musixmatch", "musixmatch"),
    NETEASE("NetEase", "netease"),
    QQ_MUSIC("QQ Music", "qq_music")
}
