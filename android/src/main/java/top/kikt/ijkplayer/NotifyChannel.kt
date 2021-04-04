package top.kikt.ijkplayer

import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import tv.danmaku.ijk.media.player.IMediaPlayer

class NotifyChannel(val registry: PluginRegistry.Registrar, val textureId: Long, val ijk: Ijk) {

    private val player
        get() = ijk.mediaPlayer

    private val channel = MethodChannel(
            registry.messenger(),
            "top.kikt/ijkplayer/event/$textureId"
    )
//    private val channel = Temp()

    private val info
        get() = ijk.getInfo().toMap()

    init {
        player.setOnPreparedListener {
            logi("prepare $info")
            player.trackInfo.forEach {
                
            }
            channel.invokeMethod("prepare", info)
        }
        player.setOnCompletionListener {
            logi("completion $info")
            channel.invokeMethod("finish", info)
        }
        player.setOnBufferingUpdateListener { mp, percent ->
            /// 在线视频缓冲
            logi("completion buffer update1 $info $percent")
            //channel.invokeMethod("buffering", info)
        }
        player.setOnSeekCompleteListener {
            logi("onSeekCompletion1 $info")
        }
        player.setOnErrorListener { mp, what, extra ->
            channel.invokeMethod("error", what)
            logi("onError $what , extra = $extra")
            false
        }
        player.setOnInfoListener { mp, what, extra ->
            logi("onInfoListener $what, extra = $extra, isPlaying = ${player.isPlaying} ")
            when (what) {
                IMediaPlayer.MEDIA_INFO_AUDIO_DECODED_START, IMediaPlayer.MEDIA_INFO_VIDEO_DECODED_START -> {
//                    channel.invokeMethod("playStateChange", info)
                }
                IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED -> {
                    ijk.degree = extra
                    channel.invokeMethod("rotateChanged", info)
                }
                IMediaPlayer.MEDIA_INFO_BUFFERING_START  -> {
                    channel.invokeMethod("buffering_start", info)
                }
                IMediaPlayer.MEDIA_INFO_BUFFERING_END  -> {
                    channel.invokeMethod("buffering_end", info)
                }
            }
            false
        }
        player.setOnNativeInvokeListener { what, args ->
            logi("onNativeInvoke $what")
            false
        }
        player.setOnControlMessageListener {
            logi("onController message $it, isPlaying = ${player.isPlaying}")
            ""
        }
    }

    fun dispose() {
        player.resetListeners()
    }

}