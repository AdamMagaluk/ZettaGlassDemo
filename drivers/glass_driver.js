
var server = null;
var GlassDriver = module.exports = function(data,server) {
  this.type = 'glass';
  this.name = 'glass '+data.address;
  this.data = data;
  this.state = 'on';

  server = server;
  this._streams = {};
};

GlassDriver.prototype.init = function(config) {
  config
    .stream('heading', this.onHeading)
    .stream('accelX',this.onAccelX)
    .stream('accelY',this.onAccelY)
    .stream('accelZ',this.onAccelZ)
    .stream('pitch', this.onPitch)
    .stream('light-level',this.onLightLevel);

  server.on('message-'+this.data.address,this.onUpdate.bind(this));
};

/*
{ Lng: -83.6001525,
  Time: 1396035112067,
  Gravity: [ 1.2832920551300049, 1.2832920551300049, 1.2832920551300049 ],
  LightLevel: 46,
  Heading: 110.71527099609375,
  Lat: 42.583073,
  LinearAcceleration: [ 0.33862996101379395, 0.3227682113647461, 0.02334347367286682 ],
  GlassId: 'android_id',
  Pitch: -1.5754826068878174,
  address: '10.0.1.29' }
*/

GlassDriver.prototype.onUpdate = function(data) {
  this._streams['heading'].emit(data.Heading);
  this._streams['accelX'].emit(data.LinearAcceleration[0]);
  this._streams['accelY'].emit(data.LinearAcceleration[1]);
  this._streams['accelZ'].emit(data.LinearAcceleration[2]);
  this._streams['pitch'].emit(data.Pitch);
  this._streams['light-level'].emit(data.LightLevel);
};

GlassDriver.prototype.onHeading = function(emitter) {
  this._streams['heading'] = emitter;
};

GlassDriver.prototype.onAccelX = function(emitter) {
  this._streams['accelX'] = emitter;
};

GlassDriver.prototype.onAccelY = function(emitter) {
  this._streams['accelY'] = emitter;
};

GlassDriver.prototype.onAccelZ = function(emitter) {
  this._streams['accelZ'] = emitter;
};

GlassDriver.prototype.onPitch = function(emitter) {
  this._streams['pitch'] = emitter;
};

GlassDriver.prototype.onLightLevel = function(emitter) {
  this._streams['light-level'] = emitter;
};
