var HelloApp = module.exports = function() {
  this.name = 'hello';
};

HelloApp.prototype.init = function(elroy) {
  elroy.observe('type="glass"').subscribe(function(glass){
    elroy.expose(glass);
  });
};
