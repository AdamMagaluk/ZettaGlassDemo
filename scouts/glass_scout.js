var util = require('util');
var EventEmitter = require('events').EventEmitter;
var dgram = require("dgram");

var Glass = require('../drivers/glass_driver');

var GlassScout = module.exports = function() {
  EventEmitter.call(this);
  this.drivers = [];
  this._server = null;
};
util.inherits(GlassScout, EventEmitter);

GlassScout.prototype.init = function(next) {
  var self = this;

  this._server = dgram.createSocket("udp4");
  this._server.on("error", function (err) {
    console.error("server error:\n" + err.stack);
    self._server.close();
  });

  this._server.on('message',this.onData.bind(this));
  this._server.bind(2323);
};

GlassScout.prototype.onData = function(buffer,rinfo){
  try{
    var json = JSON.parse(buffer.toString());
    json.address = rinfo.address;
    this.emit('discover',Glass,json,this._server);
    this._server.emit('message-'+rinfo.address,json);
    }catch(err){
    console.error(err);
  }
};



