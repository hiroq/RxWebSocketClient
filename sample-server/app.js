/**
 * MIT License
 *
 * Copyright (c) 2016 Hiroki Oizumi
 *
 * This sample-server is ported from following sample
 *  1. Copyright (c) yamatatsu
 *     yamatatsu'node-chat
 *     https://github.com/yamatatsu/node-chat
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

var WebSocketServer = require('ws').Server;
var http = require('http');
var express = require('express');
var app = express();
var port = process.env.PORT || 8080;

app.use(express.static('public'));

var server = http.createServer(app);
server.listen(port);
console.log("Node app is running at localhost:" + port);

///////////////////
// setting ws
var wss = new WebSocketServer({server:server});
console.log("websocket server created");

wss.on('connection', function(ws) {
	console.log('websocket connection open');
	ws.on('message', function(data) {
		console.log('message is ' + data);
			if(data === "term"){
				// if the message data is "term" as a special word,
				// disconnect ws session by server
				ws.terminate();
			}else{
				// only echo received data
				ws.send(data);
			}
	});

	// close
	ws.on('close', function() {
		console.log('websocket connection close');
	});
});
