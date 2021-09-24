import 'package:flutter/material.dart';
import 'package:flutter_ijkplayer/flutter_ijkplayer.dart';
import 'network.dart';


class IndexPage extends StatefulWidget {
  @override
  _IndexPageState createState() => _IndexPageState();
}

class _IndexPageState extends State<IndexPage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text("test app"),
      ),
      body: ListView(
        children: <Widget>[

          buildButton("play", NetworkPage()),

          ElevatedButton(
            onPressed: () {
              IjkManager.initIJKPlayer();
            },
            child: Text("release all ijkplayer resource"),
          ),
        ],
      ),
    );
  }

  Widget buildButton(String text, Widget targetPage) {
    return ElevatedButton(
      onPressed: () {
        Navigator.push(context, MaterialPageRoute(builder: (_) => targetPage));
      },
      child: Text(text),
    );
  }
}
