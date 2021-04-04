part of '../ijkplayer.dart';

class _IJKEventChannel {
  int get textureId => controller?.textureId;

  IjkMediaController controller;

  _IJKEventChannel(this.controller);

  MethodChannel channel;

  String get channelName => "top.kikt/ijkplayer/event/$textureId";

  Completer _prepareCompleter;

  bool _isDisposed = false;

  bool get isDisposed => _isDisposed;

  Future<void> init() async {
    channel = MethodChannel(channelName);
    channel.setMethodCallHandler(handler);
  }

  void dispose() {
    _isDisposed = true;
    channel.setMethodCallHandler(null);
    controller = null;
  }

  Future<dynamic> handler(MethodCall call) async {
    //print("********************************>>>${call.method}");
    switch (call.method) {
      case "finish": // 播放完毕
        // var index = call.arguments["type"];
        // var type = FinishType.values[index];
        _onPlayFinish(getInfo(call));
        break;
      case "playStateChange":
        onPlayStateChange(getInfo(call));
        break;
      case "prepare":
        onPrepare(getInfo(call));
        break;
      case "buffering_start": //加载中
        onBufferingStart(getInfo(call));
        break;
      case "buffering_end": //加载完毕
        onBufferingEnd();
        break;
      case "rotateChanged":
        onRotateChanged(call);
        break;
      case "error":
        //var info = await controller.getVideoInfo();
        //_onPlayFinish(info);
        int errorValue = call.arguments;
        _onPlayError(errorValue);
        break;
      default:
        return MissingPluginException(
          "$channelName ${call.method} not implement",
        );
    }
    return true;
  }

  VideoInfo getInfo(MethodCall call) {
    var map = call.arguments.cast<String, dynamic>();
    return VideoInfo.fromMap(map);
  }

  String getInfoString(MethodCall call) {
    return call.arguments.toString();
  }

  void _onPlayFinish(VideoInfo info) {
    controller?._onPlayFinish();
  }

  void onPlayStateChange(VideoInfo info) {
    controller.isPlaying = info.isPlaying;
  }

  void onPrepare(VideoInfo info) {
    controller.isPlaying = info.isPlaying;
    _prepareCompleter?.complete();
    _prepareCompleter = null;
  }

  //
  void onBufferingStart(VideoInfo info) {
    controller._ijkBufferingController.add(info);
    //controller._ijkStatus = IjkStatus.buffering;
  }

  void onBufferingEnd() {
    controller._ijkBufferingController.add(null);
    //controller._ijkStatus = IjkStatus.playing;
  }

  Future<void> waitPrepare() {
    _prepareCompleter = Completer();
    return _prepareCompleter.future;
  }

  Future<void> autoPlay(IjkMediaController ijkMediaController) async {
    try {
      await waitPrepare();
      await ijkMediaController.play();
    } catch (e) {
      LogUtils.info(e);
    }
  }

  Future<void> disableAutoPlay(IjkMediaController ijkMediaController) async {
    try {
      await waitPrepare();
      await ijkMediaController.pause();
    } catch (e) {
      LogUtils.info(e);
    }
  }

  void onRotateChanged(MethodCall call) {
    var info = getInfo(call);
    LogUtils.debug("onRotateChanged , info = $info");
  }

  void _onPlayError(int errorValue) {
    LogUtils.warning("play error , errorValue : $errorValue");
    controller._onError(errorValue);
  }
}

// enum FinishType {
//   playEnd,
//   userExit,
//   error,
// }
