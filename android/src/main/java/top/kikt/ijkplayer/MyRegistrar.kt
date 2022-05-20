package top.kikt.ijkplayer

import android.app.Activity
import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry


public class MyRegistrar {
        lateinit var textureRegistry : TextureRegistry;
        lateinit var  messenger : BinaryMessenger;
        lateinit var activity: Activity;
        lateinit var getResourceHandler1: (String)->String;
        lateinit var getResourceHandler2: (String,String)->String;
        companion object {
                @JvmStatic
                fun newFromRegistrar(registrar: PluginRegistry.Registrar):MyRegistrar {
                                var instance=MyRegistrar();
                                instance.textureRegistry=registrar.textures();
                                instance.messenger=registrar.messenger();
                                instance.activity= registrar.activity()!!;
                                instance.getResourceHandler1={input->registrar.lookupKeyForAsset(input)}
                                instance.getResourceHandler2={input1,input2->registrar.lookupKeyForAsset(input1,input2)}

                                return instance;
                }
                @JvmStatic
                fun newFromPluginBinding(bindding: FlutterPlugin.FlutterPluginBinding):MyRegistrar {
                        var instance=MyRegistrar();
                        instance.textureRegistry=bindding.textureRegistry;
                        instance.messenger=bindding.binaryMessenger;

                        instance.getResourceHandler1={input->bindding.flutterAssets.getAssetFilePathByName(input)}
                         instance.getResourceHandler2={input1,input2->bindding.flutterAssets.getAssetFilePathByName(input1,input2)}
                        return instance;
                }
        }
}