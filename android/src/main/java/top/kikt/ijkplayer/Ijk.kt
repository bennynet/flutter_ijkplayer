package top.kikt.ijkplayer

/// create 2019/3/7 by cai

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.annotation.RequiresApi
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import top.kikt.ijkplayer.entity.IjkOption
import top.kikt.ijkplayer.entity.Info
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.TextureMediaPlayer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

class   Ijk(private val registry: MyRegistrar, private val options: Map<String, Any>) {

    private val textureEntry = registry.textureRegistry.createSurfaceTexture();
    val id: Long
        get() = textureEntry.id()

    val mediaPlayer: IjkMediaPlayer = IjkMediaPlayer()
    private val textureMediaPlayer: TextureMediaPlayer

    private val methodChannel: MethodChannel = MethodChannel(registry.messenger, "top.kikt/ijkplayer/$id")

    private val notifyChannel: NotifyChannel = NotifyChannel(registry, id, this)

    private val handler = Handler(Looper.getMainLooper())

    var degree = 0

    var isDisposed = false

    private var tmpFile:File? = null

    init {
        textureMediaPlayer = TextureMediaPlayer(mediaPlayer)
        configOptions()
        textureMediaPlayer.surfaceTexture = textureEntry.surfaceTexture()
        methodChannel.setMethodCallHandler { call, result ->
            if (isDisposed) {
                return@setMethodCallHandler
            }
            when (call?.method) {
                "setNetworkDataSource" -> {
                    val uri = call.argument<String>("uri")
                    val params = call.argument<Map<String, String>>("headers")
                    if (uri == null) {
                        handleSetUriResult(Exception("uri是必传参数"), result)
                        return@setMethodCallHandler
                    }
                    setUri(uri, params) { throwable ->
                        handleSetUriResult(throwable, result)
                    }
                }
                "setAssetDataSource" -> {
                    val name = call.argument<String>("name")
                    val `package` = call.argument<String>("package")
                    if (name != null) {
                        setAssetUri(name, `package`) { throwable ->
                            handleSetUriResult(throwable, result)
                        }
                    } else {
                        handleSetUriResult(Exception("没有找到资源"), result)
                    }
                }
                "setFileDataSource" -> {
                    val path = call.argument<String>("path")
                    if (path != null) {
                        setUri("file://$path", hashMapOf()) { throwable ->
                            handleSetUriResult(throwable, result)
                        }
                    }
                }
                "setPhotoManagerUrl" -> {
                    val mediaUrl = call.argument<String>("mediaUrl")
                    if (mediaUrl != null) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            setUri("file://$mediaUrl", hashMapOf()) { throwable ->
                                handleSetUriResult(throwable, result)
                            }
                        } else {
                            setAndroidQUrl(mediaUrl){
                                handleSetUriResult(it, result)
                            }
                        }
                    } else {
                        handleSetUriResult(Exception("没有找到资源"), result)
                    }
                }
                "play" -> {
                    play()
                    result?.success(true)
                }
                "pause" -> {
                    pause()
                    result?.success(true)
                }
                "stop" -> {
                    stop()
                    result?.success(true)
                }
                "getInfo" -> {
                    val info = getInfo()
                    result?.success(info.toMap())
                }
                "seekTo" -> {
                    val target = call.argument<Double>("target")
                    if (target != null) {
                        seekTo((target * 1000).toLong())
                    }
                    result?.success(true)
                }
                "setVolume" -> {
                    val volume = call.argument<Int>("volume")
                    setVolume(volume)
                    result?.success(true)
                }
                "getVolume" -> {
//                result?.success(this.mediaPlayer.setVolume())
                }
                "screenShot" -> {
                    val bytes = screenShot()
                    result?.success(bytes)
                }
                "setSpeed" -> {
                    val speed = call.arguments<Double>()
                    if (speed != null) {
                        mediaPlayer.setSpeed(speed.toFloat())
                    }
                }
                else -> {
                    result?.notImplemented()
                }
            }
        }
    }


    private val appContext: Context
        get() = registry.activity.application //registry.activity().application

    private fun configOptions() {
        // see https://www.jianshu.com/p/843c86a9e9ad
        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "fastseek")

        // 以下注释有选项会导致m3u8类型无声音
//        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzemaxduration", 100L)
//        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 1)
//        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1024 * 10)
//        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "flush_packets", 1L)

        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "reconnect", 5)
        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 5)
        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1)
        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1) // 开硬解
        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1)

        //        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", maxCacheSize)
        //        mediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", if (isBufferCache) 1 else 0)

        fun setOptionToMediaPlayer(option: IjkOption) {
            if (option.isInit.not()) {
                return
            }
            val category = when (option.type) {
                IjkOption.Type.Format -> IjkMediaPlayer.OPT_CATEGORY_FORMAT
                IjkOption.Type.Player -> IjkMediaPlayer.OPT_CATEGORY_PLAYER
                IjkOption.Type.Sws -> IjkMediaPlayer.OPT_CATEGORY_SWS
                IjkOption.Type.Codec -> IjkMediaPlayer.OPT_CATEGORY_CODEC
                else -> -1
            }

            if (category == -1) {
                return
            }

            val value = option.value
            when (value) {
                is Int -> mediaPlayer.setOption(category, option.key, value.toLong())
                is String -> mediaPlayer.setOption(category, option.key, value)
                is Long -> mediaPlayer.setOption(category, option.key, value)
            }
        }

        val options = options["options"]
        if (options is List<*>) {
            for (option in options) {
                if (option is Map<*, *>) {
                    val ijkOptions = IjkOption(option)
                    setOptionToMediaPlayer(ijkOptions)
                }
            }
        }
    }

    private fun screenShot(): ByteArray? {
        val frameBitmap = mediaPlayer.frameBitmap
        return if (frameBitmap != null) {
            val outputStream = ByteArrayOutputStream()
            frameBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            frameBitmap.recycle()
            outputStream.toByteArray()
        } else {
            null
        }
    }

    fun getInfo(): Info {
        val duration = mediaPlayer.duration
        val currentPosition = mediaPlayer.currentPosition
        val width = mediaPlayer.videoWidth
        val height = mediaPlayer.videoHeight
        val outputFps = mediaPlayer.videoOutputFramesPerSecond
        return Info(
                duration = duration.toDouble() / 1000,
                currentPosition = currentPosition.toDouble() / 1000,
                width = width,
                height = height,
                isPlaying = textureMediaPlayer.isPlaying,
                degree = degree,
                tcpSpeed = mediaPlayer.tcpSpeed,
                outputFps = outputFps
//                decodeFps = mediaPlayer.videoDecodeFramesPerSecond
        )
    }

    private fun handleSetUriResult(throwable: Throwable?, result: MethodChannel.Result?) {
        if (throwable == null) {
            result?.success(true)
        } else {
            throwable.printStackTrace()
            result?.error("1", "set resource error", throwable.toString())
        }
    }

    private fun setUri(uriString: String, headers: Map<String, String>?, callback: (Throwable?) -> Unit) {
        try {
            val uri = Uri.parse(uriString)
            mediaPlayer.setDataSource(appContext, uri, headers)
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)

            val timeoutRunnable = Runnable {
                // to avoid 'Reply already submitted' exception
                mediaPlayer.setOnPreparedListener(null)
                callback(RuntimeException("Prepare timeout"))
            }
            handler.postDelayed(timeoutRunnable, 15 * 1000)
            mediaPlayer.setOnPreparedListener {
                handler.removeCallbacks(timeoutRunnable)
                callback(null)
            }

            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            callback(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setAndroidQUrl(mediaUri: String, callback: (Throwable?) -> Unit) {
        try {
            val inputStream = appContext.contentResolver.openInputStream(Uri.parse(mediaUri))
                    ?: throw RuntimeException("Cannot open $mediaUri")
            if (inputStream is FileInputStream) {
                mediaPlayer.setDataSource(inputStream.fd)
            } else {
                throw RuntimeException("Cannot open $mediaUri")
            }
            val timeoutRunnable = Runnable {
                mediaPlayer.setOnPreparedListener(null)
                callback(RuntimeException("Prepare timeout"))
            }
            handler.postDelayed(timeoutRunnable, 15 * 1000)
            mediaPlayer.setOnPreparedListener {
                handler.removeCallbacks(timeoutRunnable)
                callback(null)
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            callback(e)
        }
    }

    private fun setAssetUri(name: String, `package`: String?, callback: (Throwable?) -> Unit) {
        try {
            mediaPlayer.setOnPreparedListener {
                callback(null)
            }
            val asset =
                    if (`package` == null) {
                        registry.getResourceHandler1(name)
                    } else {
                        registry.getResourceHandler2(name, `package`)
                    }
            val assetManager = registry.activity.assets
            val input = assetManager.open(asset)
            if (input is FileInputStream) {
                mediaPlayer.setDataSource(input.fd)
            } else {
                val cacheDir = registry.activity.cacheDir.absoluteFile.path

                val fileName = Base64.encodeToString(asset.toByteArray(), Base64.DEFAULT)
                val file = File(cacheDir, fileName)
                file.outputStream().use { outputStream ->
                    input.use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                this.tmpFile = file
                mediaPlayer.setDataSource(FileMediaDataSource(file))
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            callback(e)
        }
    }

    fun dispose() {
        if (isDisposed) {
            return
        }
        isDisposed = true
        tryCatchError {
            if (tmpFile?.exists() == true) {
                tmpFile?.delete()
            }
            notifyChannel.dispose()
            methodChannel.setMethodCallHandler(null)
            textureMediaPlayer.stop()
        }
        tryCatchError {
            textureMediaPlayer.release()
        }
        tryCatchError {
            textureEntry.release()
        }
    }

    private fun tryCatchError(runnable: () -> Unit) {
        try {
            runnable()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun play() {
        try {
            mediaPlayer.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pause() {
        textureMediaPlayer.pause()
    }

    private fun stop() {
        textureMediaPlayer.stop()
    }

    private fun seekTo(msec: Long) {
        textureMediaPlayer.seekTo(msec)
    }

    private fun setVolume(volume: Int?) {
        if (volume == null) {
            return
        }
        val v = volume.toFloat() / 100
        mediaPlayer.setVolume(v, v)
    }

}

